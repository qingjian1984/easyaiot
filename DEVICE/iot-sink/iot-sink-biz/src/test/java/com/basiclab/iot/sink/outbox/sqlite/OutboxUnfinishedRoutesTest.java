package com.basiclab.iot.sink.outbox.sqlite;

import com.basiclab.iot.sink.telemetry.envelope.DataPriority;
import com.basiclab.iot.sink.telemetry.envelope.EnvelopeCanonicalCodec;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryEnvelope;
import com.basiclab.iot.sink.telemetry.envelope.TelemetryQuality;
import com.basiclab.iot.sink.telemetry.outbox.OutboxUnavailableException;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryOutboxBatch;
import com.basiclab.iot.sink.telemetry.outbox.TelemetryRoute;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxUnfinishedRoutesTest {

    @TempDir
    Path directory;
    private Path database;
    private SqliteTelemetryOutbox outbox;

    @BeforeEach
    void setUp() throws Exception {
        database = directory.resolve("outbox.db");
        SqliteOutboxMigration.migrate(database);
        outbox = new SqliteTelemetryOutbox(database, new EnvelopeCanonicalCodec(), 100);
    }

    @AfterEach
    void tearDown() {
        if (outbox != null) {
            outbox.shutdown();
        }
    }

    @Test
    void listsOnlyUnfinishedRoutesDedupedAndSorted() throws Exception {
        append("a-1", "prod-b", "device-1");
        append("a-2", "prod-a", "device-2");
        append("a-3", "prod-a", "device-1");
        append("a-4", "prod-a", "device-1");
        append("a-5", "prod-z", "device-terminal");
        append("a-6", "prod-null", "device-null");

        updateStatus("a-1", "IN_FLIGHT");
        updateStatus("a-5", "ACKED");
        updateStatus("a-6", "DEAD_LETTER");
        updateProduct("a-4", null);

        assertEquals(List.of(
                new TelemetryRoute("prod-a", "device-1"),
                new TelemetryRoute("prod-a", "device-2"),
                new TelemetryRoute("prod-b", "device-1")), outbox.listUnfinishedRoutes());
        assertThrows(UnsupportedOperationException.class,
                () -> outbox.listUnfinishedRoutes().add(new TelemetryRoute("x", "y")));
    }

    @Test
    void queryDoesNotChangeOutboxState() throws Exception {
        append("state-1", "prod", "device");
        List<Object> before = state("state-1");

        assertEquals(List.of(new TelemetryRoute("prod", "device")), outbox.listUnfinishedRoutes());

        assertEquals(before, state("state-1"));
    }

    @Test
    void invalidRouteFailsWholeQueryAndWriterRemainsUsable() throws Exception {
        append("invalid-1", "prod", "device");
        updateProduct("invalid-1", "/");

        OutboxUnavailableException failure = assertThrows(OutboxUnavailableException.class,
                () -> outbox.listUnfinishedRoutes());
        assertTrue(failure.getMessage().startsWith("ROUTE_IDENTITY_INVALID"));

        updateProduct("invalid-1", "fixed-product");
        assertEquals(List.of(new TelemetryRoute("fixed-product", "device")),
                outbox.listUnfinishedRoutes());
    }

    private void append(String messageId, String product, String device) {
        outbox.appendBatch(new TelemetryOutboxBatch(product, List.of(env(messageId, device))),
                Duration.ofSeconds(5));
    }

    private TelemetryEnvelope env(String messageId, String device) {
        return new TelemetryEnvelope(
                TelemetryEnvelope.SCHEMA_VERSION, TelemetryEnvelope.CANONICALIZATION_VERSION,
                messageId, "request-" + messageId, "tenant", "site", device, "voltage",
                "220.5", TelemetryEnvelope.VALUE_ENCODING_DECIMAL_STRING, TelemetryQuality.GOOD,
                DataPriority.NORMAL_TELEMETRY, "2026-08-20T00:00:00Z", "2026-08-20T00:00:00Z",
                1, "modbus-rtu", 1);
    }

    private void updateStatus(String messageId, String status) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE telemetry_outbox SET status = ? WHERE message_id = ?")) {
            statement.setString(1, status);
            statement.setString(2, messageId);
            statement.executeUpdate();
        }
    }

    private void updateProduct(String messageId, String product) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE telemetry_outbox SET product_identification = ? WHERE message_id = ?")) {
            statement.setString(1, product);
            statement.setString(2, messageId);
            statement.executeUpdate();
        }
    }

    private List<Object> state(String messageId) throws Exception {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT status, attempts, next_retry_at_ms, in_flight_at_ms, "
                             + "ack_deadline_at_ms, updated_at_ms FROM telemetry_outbox "
                             + "WHERE message_id = ?")) {
            statement.setString(1, messageId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return List.of();
                }
                return Arrays.asList(result.getString(1), result.getInt(2), result.getObject(3),
                        result.getObject(4), result.getObject(5), result.getLong(6));
            }
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
    }
}
