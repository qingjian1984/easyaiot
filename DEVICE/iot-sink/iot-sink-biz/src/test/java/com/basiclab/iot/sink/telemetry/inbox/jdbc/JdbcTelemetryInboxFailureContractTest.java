package com.basiclab.iot.sink.telemetry.inbox.jdbc;

import com.basiclab.iot.sink.telemetry.inbox.InboxEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.transaction.CannotCreateTransactionException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

/** LC01-12: a database failure must propagate instead of becoming a success result. */
class JdbcTelemetryInboxFailureContractTest {

    @Test
    void databaseFailurePropagatesWithoutSuccessResult() {
        JdbcTelemetryInbox inbox = new JdbcTelemetryInbox(new FailingDataSource());
        InboxEnvelope envelope = new InboxEnvelope(
                "failure-message", "failure-request", "999888777", "site-test",
                "device-test", "property-test", "{}".getBytes(StandardCharsets.UTF_8),
                "hash", 1L, 1L, "test", 1L);

        assertThrows(CannotCreateTransactionException.class,
                () -> inbox.receiveEnvelopes(List.of(envelope)));
    }

    private static final class FailingDataSource extends AbstractDataSource {
        @Override
        public Connection getConnection() throws SQLException {
            throw new SQLException("intentional contract-test failure");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            throw new SQLException("intentional contract-test failure");
        }
    }
}
