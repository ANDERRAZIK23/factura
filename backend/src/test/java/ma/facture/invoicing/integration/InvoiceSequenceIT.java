package ma.facture.invoicing.integration;

import ma.facture.invoicing.invoice.numbering.InvoiceSequenceRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class InvoiceSequenceIT {
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    private static JdbcTemplate jdbc;
    private static InvoiceSequenceRepository repository;
    private static TransactionTemplate transactions;

    @BeforeAll static void migrateDatabase() {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()).load().migrate();
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        repository = new InvoiceSequenceRepository(jdbc);
        transactions = new TransactionTemplate(new JdbcTransactionManager(dataSource));
    }

    @Test void allocatesUniqueGapFreeCommittedValuesUnderConcurrencyAndRestartsByYear() throws Exception {
        int allocationCount = 40;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();

        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < allocationCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return transactions.execute(status -> repository.nextValue(2026, NOW));
                }));
            }
            start.countDown();

            List<Long> allocated = new ArrayList<>();
            for (Future<Long> future : futures) allocated.add(future.get());
            allocated.sort(Long::compareTo);

            assertThat(allocated).containsExactlyElementsOf(
                    java.util.stream.LongStream.rangeClosed(1, allocationCount).boxed().toList());
        }

        assertThat(jdbc.queryForObject("select last_value from invoice_sequences where sequence_year=2026", Long.class))
                .isEqualTo(40L);
        Long firstNextYearValue = transactions.execute(status -> repository.nextValue(2027, NOW));
        assertThat(firstNextYearValue).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select last_value from invoice_sequences where sequence_year=2027", Long.class))
                .isEqualTo(1L);
    }

    @Test void rollsBackAllocationWithTheSurroundingValidationTransaction() {
        Long rolledBackValue = transactions.execute(status -> {
            long value = repository.nextValue(2028, NOW);
            status.setRollbackOnly();
            return value;
        });

        assertThat(rolledBackValue).isEqualTo(1L);
        assertThat(jdbc.queryForObject("select count(*) from invoice_sequences where sequence_year=2028", Long.class))
                .isZero();
        Long firstCommittedValue = transactions.execute(status -> repository.nextValue(2028, NOW));
        assertThat(firstCommittedValue).isEqualTo(1L);
    }
}
