package com.basiclab.iot.sink.telemetry.store.jdbc;

import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import com.basiclab.iot.sink.telemetry.store.WriteStatus;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PostgreSQL adapter batch guards; no database/network is used in this class. */
class JdbcTelemetryStoreBatchTest {

    private final JdbcTelemetryStore store = new JdbcTelemetryStore(new FakeJdbcDataSource());

    @Test
    void nullAndEmptyBatchHaveExplicitContracts() {
        assertThrows(IllegalArgumentException.class, () -> store.appendBatch(null));
        assertTrue(store.appendBatch(List.of()).items().isEmpty());
    }

    @Test
    void hardCapPreservesOrderAndOnlyIdentifiableItemsGetTooLarge() {
        List<TelemetrySample> input = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            input.add(sample("pg-cap-" + i, "{\"value\":\"1.0\"}"));
        }
        input.add(null);

        var result = store.appendBatch(input);
        assertEquals(501, result.items().size());
        assertEquals("pg-cap-0", result.items().get(0).messageId());
        assertEquals(WriteStatus.FINAL_FAILED, result.items().get(0).status());
        assertEquals("STORE_BATCH_TOO_LARGE", result.items().get(0).errorCode());
        assertEquals(WriteStatus.FINAL_FAILED, result.items().get(500).status());
        assertEquals("STORE_SAMPLE_INVALID", result.items().get(500).errorCode());
        assertNull(result.items().get(500).messageId());
    }

    @Test
    void deterministicInvalidValueIsFinalWithoutDatabaseAccess() {
        WriteItemResult result = store.appendBatch(List.of(
                sample("pg-invalid-value", "{\"value\":\"not-a-decimal\"}"))).items().get(0);
        assertEquals(WriteStatus.FINAL_FAILED, result.status());
        assertEquals("STORE_VALUE_INVALID", result.errorCode());
    }

    @Test
    void sameHashIsDuplicateAndDifferentHashIsCollision() {
        FakeJdbcDataSource dataSource = new FakeJdbcDataSource();
        JdbcTelemetryStore adapter = new JdbcTelemetryStore(dataSource);
        TelemetrySample first = sample("pg-idempotent", "{\"value\":\"1.0\"}");
        TelemetrySample different = sampleWithHash("pg-idempotent", "{\"value\":\"2.0\"}", "b".repeat(64));

        assertEquals(WriteStatus.STORED, adapter.appendBatch(List.of(first)).items().get(0).status());
        assertEquals(WriteStatus.DUPLICATE, adapter.appendBatch(List.of(first)).items().get(0).status());
        WriteItemResult collision = adapter.appendBatch(List.of(different)).items().get(0);
        assertEquals(WriteStatus.FINAL_FAILED, collision.status());
        assertEquals("MESSAGE_ID_COLLISION", collision.errorCode());
        assertEquals(1, dataSource.insertedMessageIds.size());
    }

    @Test
    void multipleExistingHashesAreStateCorrupt() {
        FakeJdbcDataSource dataSource = new FakeJdbcDataSource();
        dataSource.existingHashes.put("pg-corrupt", new ArrayList<>(List.of("a".repeat(64), "b".repeat(64))));
        JdbcTelemetryStore adapter = new JdbcTelemetryStore(dataSource);

        WriteItemResult result = adapter.appendBatch(List.of(
                sample("pg-corrupt", "{\"value\":\"1.0\"}"))).items().get(0);
        assertEquals(WriteStatus.FINAL_FAILED, result.status());
        assertEquals("STORE_STATE_CORRUPT", result.errorCode());
        assertTrue(dataSource.insertedMessageIds.isEmpty());
    }

    @Test
    void dependencyFailureIsRetryableAndDoesNotMaskFollowingItem() {
        FakeJdbcDataSource dataSource = new FakeJdbcDataSource();
        dataSource.unavailableMessages.add("pg-unavailable");
        JdbcTelemetryStore adapter = new JdbcTelemetryStore(dataSource);

        var result = adapter.appendBatch(List.of(
                sample("pg-unavailable", "{\"value\":\"1.0\"}"),
                sample("pg-following", "{\"value\":\"2.0\"}")));
        assertEquals(WriteStatus.RETRYABLE_FAILED, result.items().get(0).status());
        assertEquals("STORE_UNAVAILABLE", result.items().get(0).errorCode());
        assertEquals(WriteStatus.STORED, result.items().get(1).status());
        assertEquals(List.of("pg-following"), dataSource.insertedMessageIds);
    }

    private static TelemetrySample sample(String messageId, String json) {
        return sampleWithHash(messageId, json, "a".repeat(64));
    }

    private static TelemetrySample sampleWithHash(String messageId, String json, String hash) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new TelemetrySample(messageId, "request-" + messageId, "100", "site",
                "device", "property", bytes, hash, 1L, 1L, "test", 1L);
    }

    private static final class FakeJdbcDataSource implements DataSource {
        private final Map<String, List<String>> existingHashes = new HashMap<>();
        private final Set<String> unavailableMessages = new HashSet<>();
        private final List<String> insertedMessageIds = new ArrayList<>();

        @Override
        public Connection getConnection() throws SQLException {
            return connectionProxy();
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return connectionProxy();
        }

        private Connection connectionProxy() {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("prepareStatement")) {
                            return statementProxy((String) args[0]);
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return false;
                        }
                        if (method.getName().equals("getAutoCommit")) {
                            return true;
                        }
                        if (method.getName().equals("unwrap")) {
                            throw new SQLException("not a wrapper");
                        }
                        if (method.getName().equals("isWrapperFor")) {
                            return false;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private PreparedStatement statementProxy(String sql) {
            Map<Integer, Object> params = new HashMap<>();
            return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                    new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
                        if (method.getName().startsWith("set") && args != null && args.length >= 2
                                && args[0] instanceof Integer index) {
                            params.put(index, args[1]);
                            return null;
                        }
                        if (method.getName().equals("executeQuery")) {
                            String messageId = String.valueOf(params.get(2));
                            if (unavailableMessages.contains(messageId)) {
                                throw new SQLException("simulated unavailable");
                            }
                            return hashResultSet(existingHashes.getOrDefault(messageId, List.of()));
                        }
                        if (method.getName().equals("executeUpdate")) {
                            String messageId = String.valueOf(params.get(2));
                            String hash = String.valueOf(params.get(3));
                            insertedMessageIds.add(messageId);
                            existingHashes.computeIfAbsent(messageId, ignored -> new ArrayList<>()).add(hash);
                            return 1;
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("isClosed")) {
                            return false;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private ResultSet hashResultSet(List<String> hashes) {
            int[] index = {-1};
            return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
                        if (method.getName().equals("next")) {
                            index[0]++;
                            return index[0] < hashes.size();
                        }
                        if (method.getName().equals("getString")) {
                            return hashes.get(index[0]);
                        }
                        if (method.getName().equals("close")) {
                            return null;
                        }
                        if (method.getName().equals("wasNull")) {
                            return false;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        @Override
        public PrintWriter getLogWriter() {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) {
        }

        @Override
        public void setLoginTimeout(int seconds) {
        }

        @Override
        public int getLoginTimeout() {
            return 0;
        }

        @Override
        public Logger getParentLogger() {
            return Logger.getLogger(FakeJdbcDataSource.class.getName());
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            throw new SQLException("not a wrapper");
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) {
            return false;
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) {
                return null;
            }
            if (type == boolean.class) {
                return false;
            }
            if (type == byte.class) {
                return (byte) 0;
            }
            if (type == short.class) {
                return (short) 0;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            if (type == float.class) {
                return 0F;
            }
            if (type == double.class) {
                return 0D;
            }
            if (type == char.class) {
                return (char) 0;
            }
            return null;
        }
    }
}
