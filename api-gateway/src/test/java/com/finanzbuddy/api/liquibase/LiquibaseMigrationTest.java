package com.finanzbuddy.api.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class LiquibaseMigrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void configureLiquibase(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:liquibasetestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add(
                "spring.liquibase.change-log",
                () -> "classpath:/db/changelog/db.changelog-master.xml");
    }

    @Test
    void usersAndDocumentsTablesAreCreated() {
        List<String> tables =
                jdbcTemplate.queryForList(
                        "SELECT UPPER(table_schema || '.' || table_name) FROM information_schema.tables",
                        String.class);

        assertThat(tables)
                .contains("PUBLIC.USERS", "PUBLIC.DOCUMENTS", "PUBLIC.OUTBOX_EVENTS");
    }
}
