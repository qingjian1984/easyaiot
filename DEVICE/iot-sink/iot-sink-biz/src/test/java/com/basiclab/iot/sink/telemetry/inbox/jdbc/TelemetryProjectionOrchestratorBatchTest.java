package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.store.TelemetrySample;
import com.basiclab.iot.sink.telemetry.store.TelemetryStorePort;
import com.basiclab.iot.sink.telemetry.store.WriteBatchResult;
import com.basiclab.iot.sink.telemetry.store.WriteItemResult;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Projector batch routing tests using an in-memory JDBC proxy; no database/network. */
class TelemetryProjectionOrchestratorBatchTest {

    @Test
    void callsAppendBatchOnceAndRoutesResultsInInputOrder() throws Exception {
        JdbcProbe jdbc = new JdbcProbe(rows("m-1", "m-2"));
        RecordingStore store = new RecordingStore(new WriteBatchResult(List.of(
                WriteItemResult.stored("m-1"), WriteItemResult.duplicate("m-2"))));
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(jdbc, store);

        invokeProjectBatch(orchestrator);

        assertEquals(1, store.calls);
        assertEquals(List.of("m-1", "m-2"), store.samples.stream()
                .map(TelemetrySample::messageId).toList());
        assertEquals(2, countSql(jdbc.updates, "PROJECTION_STATE = 'COMPLETED'"));
        assertFalse(jdbc.updates.stream().anyMatch(sql -> sql.contains("PROJECTION_DEAD_LETTER")));
    }

    @Test
    void finalAndRetryableResultsUseIndependentStableTransitions() throws Exception {
        JdbcProbe jdbc = new JdbcProbe(rows("m-final", "m-retry"));
        RecordingStore store = new RecordingStore(new WriteBatchResult(List.of(
                WriteItemResult.finalFailed("m-final", "MESSAGE_ID_COLLISION"),
                WriteItemResult.retryable("m-retry", "STORE_UNAVAILABLE"))));
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(jdbc, store);

        invokeProjectBatch(orchestrator);

        assertEquals(1, store.calls);
        assertTrue(jdbc.updates.stream().anyMatch(sql -> sql.contains("PROJECTION_DEAD_LETTER")));
        assertTrue(countSql(jdbc.updates, "SET PROJECTION_STATE = 'RECEIVED'") > 0);
    }

    @Test
    void firstStateUpdateFailureDoesNotPreventFollowingRows() throws Exception {
        JdbcProbe jdbc = new JdbcProbe(rows("m-1", "m-2"));
        jdbc.failFirstUpdate = true;
        RecordingStore store = new RecordingStore(new WriteBatchResult(List.of(
                WriteItemResult.stored("m-1"), WriteItemResult.stored("m-2"))));
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(jdbc, store);

        invokeProjectBatch(orchestrator);

        assertEquals(1, store.calls);
        assertEquals(2, countSql(jdbc.updates, "PROJECTION_STATE = 'COMPLETED'"));
        assertEquals(1, countSql(jdbc.updates, "SET PROJECTION_STATE = 'RECEIVED'"));
    }

    @Test
    void resultIdentityMismatchRetriesAllRowsWithoutPositionalCompletion() throws Exception {
        JdbcProbe jdbc = new JdbcProbe(rows("m-1", "m-2"));
        RecordingStore store = new RecordingStore(WriteBatchResult.of(WriteItemResult.stored("wrong-id")));
        TelemetryProjectionOrchestrator orchestrator = new TelemetryProjectionOrchestrator(jdbc, store);

        invokeProjectBatch(orchestrator);

        assertEquals(1, store.calls);
        assertEquals(2, countSql(jdbc.updates, "SET PROJECTION_STATE = 'RECEIVED'"));
        assertEquals(0, countSql(jdbc.updates, "PROJECTION_STATE = 'COMPLETED'"));
    }

    private static void invokeProjectBatch(TelemetryProjectionOrchestrator orchestrator) throws Exception {
        Method method = TelemetryProjectionOrchestrator.class.getDeclaredMethod("projectBatch");
        method.setAccessible(true);
        method.invoke(orchestrator);
    }

    private static int countSql(List<String> sqls, String fragment) {
        return (int) sqls.stream().filter(sql -> sql.toUpperCase().contains(fragment)).count();
    }

    private static List<Map<String, Object>> rows(String... messageIds) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String messageId : messageIds) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", (long) rows.size() + 1L);
            row.put("message_id", messageId);
            row.put("request_id", "request-" + messageId);
            row.put("tenant_id", 100L);
            row.put("site_code", "site");
            row.put("device_identification", "device");
            row.put("property_code", "property");
            row.put("payload", "{\"value\":\"1.0\"}".getBytes(StandardCharsets.UTF_8));
            row.put("content_sha256", "a".repeat(64));
            row.put("collected_at_ms", 1L);
            row.put("sequence_no", 1L);
            row.put("source", "test");
            row.put("config_version", 1L);
            row.put("projection_attempts", 1);
            rows.add(row);
        }
        return rows;
    }

    private static final class RecordingStore implements TelemetryStorePort {
        private final WriteBatchResult result;
        private final List<TelemetrySample> samples = new ArrayList<>();
        private int calls;

        private RecordingStore(WriteBatchResult result) {
            this.result = result;
        }

        @Override
        public WriteBatchResult appendBatch(List<TelemetrySample> samples) {
            calls++;
            this.samples.addAll(samples);
            return result;
        }
    }

    private static final class JdbcProbe implements DataSource {
        private final List<Map<String, Object>> rows;
        private final List<String> updates = new ArrayList<>();
        private boolean failFirstUpdate;

        private JdbcProbe(List<Map<String, Object>> rows) {
            this.rows = rows;
        }

        @Override
        public Connection getConnection() {
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "prepareStatement" -> preparedStatement((String) args[0]);
                case "close" -> null;
                case "isClosed" -> false;
                case "getAutoCommit" -> true;
                case "isReadOnly" -> false;
                case "unwrap" -> throw new SQLException("not a wrapper");
                case "isWrapperFor" -> false;
                default -> defaultValue(method.getReturnType());
            };
            return (Connection) Proxy.newProxyInstance(
                    Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, handler);
        }

        private PreparedStatement preparedStatement(String sql) {
            InvocationHandler handler = new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    return switch (method.getName()) {
                        case "executeQuery" -> resultSet();
                        case "executeUpdate" -> {
                            updates.add(sql);
                            if (failFirstUpdate) {
                                failFirstUpdate = false;
                                throw new SQLException("simulated");
                            }
                            yield 1;
                        }
                        case "close" -> null;
                        case "isClosed" -> false;
                        case "getConnection" -> getConnection();
                        default -> defaultValue(method.getReturnType());
                    };
                }
            };
            return (PreparedStatement) Proxy.newProxyInstance(
                    PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, handler);
        }

        private ResultSet resultSet() {
            int[] index = {-1};
            InvocationHandler handler = (proxy, method, args) -> {
                switch (method.getName()) {
                    case "next" -> {
                        index[0]++;
                        return index[0] < rows.size();
                    }
                    case "getString", "getLong", "getInt", "getBytes" -> {
                        Object value = rows.get(index[0]).get(String.valueOf(args[0]));
                        if (method.getName().equals("getString")) {
                            return value == null ? null : String.valueOf(value);
                        }
                        if (method.getName().equals("getBytes")) {
                            return value;
                        }
                        if (method.getName().equals("getInt")) {
                            return ((Number) value).intValue();
                        }
                        return ((Number) value).longValue();
                    }
                    case "close" -> {
                        return null;
                    }
                    case "wasNull" -> {
                        return false;
                    }
                    default -> {
                        return defaultValue(method.getReturnType());
                    }
                }
            };
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, handler);
        }

        @Override
        public Connection getConnection(String username, String password) {
            return getConnection();
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
            return Logger.getLogger(JdbcProbe.class.getName());
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
