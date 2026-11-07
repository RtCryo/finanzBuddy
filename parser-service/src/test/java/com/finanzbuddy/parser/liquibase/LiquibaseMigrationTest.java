package com.finanzbuddy.parser.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class LiquibaseMigrationTest {

    @Test
    void backfillsHistoricOwnersAndRetriesTheirStatusReports() throws Exception {
        var dataSource =
                new DriverManagerDataSource(
                        "jdbc:h2:mem:status_backfill;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute(
                "CREATE TABLE processed_documents (document_id BIGINT PRIMARY KEY, user_id BIGINT, status_confirmed BOOLEAN NOT NULL)");
        jdbc.execute(
                "CREATE TABLE transactions (document_id BIGINT NOT NULL, user_id BIGINT NOT NULL)");
        jdbc.update(
                "INSERT INTO processed_documents VALUES (1, NULL, TRUE), (2, NULL, TRUE), (3, 9, FALSE), (4, NULL, TRUE)");
        jdbc.update("INSERT INTO transactions VALUES (1, 7), (1, 7), (2, 8)");

        var migration = new SpringLiquibase();
        migration.setDataSource(dataSource);
        migration.setChangeLog(
                "classpath:/db/changelog/changes/005-backfill-status-confirmation.xml");
        migration.afterPropertiesSet();

        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM processed_documents WHERE document_id = 1",
                                Long.class))
                .isEqualTo(7L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM processed_documents WHERE document_id = 2",
                                Long.class))
                .isEqualTo(8L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_confirmed FROM processed_documents WHERE document_id = 1",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_confirmed FROM processed_documents WHERE document_id = 2",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM processed_documents WHERE document_id = 3",
                                Long.class))
                .isEqualTo(9L);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status_confirmed FROM processed_documents WHERE document_id = 3",
                                Boolean.class))
                .isFalse();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT user_id FROM processed_documents WHERE document_id = 4",
                                Long.class))
                .isNull();
    }
}
