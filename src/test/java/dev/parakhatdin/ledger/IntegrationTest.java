package dev.parakhatdin.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base for tests that need the full application and a real PostgreSQL (started once by Testcontainers and
 * shared by all test classes). Polling is switched off so tests drive the webhook worker explicitly.
 * Each test starts from an empty ledger; the chart of accounts from Flyway stays.
 */
@SpringBootTest(properties = {
        "ledger.webhooks.worker.enabled=false",
        // The concurrency test provokes unique violations on purpose; keep Hibernate from logging them as errors.
        "logging.level.org.hibernate.orm.jdbc.error=OFF"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE ledger_entries, ledger_transactions, webhook_events RESTART IDENTITY CASCADE");
    }
}
