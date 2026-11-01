package com.finanzbuddy.api.liquibase;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                .contains(
                        "PUBLIC.USERS",
                        "PUBLIC.DOCUMENTS",
                        "PUBLIC.OUTBOX_EVENTS",
                        "PUBLIC.PERSONAL_TRANSACTION_CATEGORIES",
                        "PUBLIC.TRANSACTION_VERSIONS",
                        "PUBLIC.PERSONAL_TRANSACTION_TAGS",
                        "PUBLIC.TRANSACTION_TAG_ASSIGNMENTS")
                .doesNotContain("PUBLIC.TRANSACTIONS");
    }

    @Test
    void outboxEventsColumnsMatchEntity() {
        Map<String, String> columns = columnNullability("OUTBOX_EVENTS");
        assertThat(columns.keySet())
                .contains(
                        "ID",
                        "AGGREGATE_ID",
                        "TOPIC",
                        "PAYLOAD",
                        "CREATED_AT",
                        "PUBLISHED_AT",
                        "DEAD_LETTERED_AT",
                        "NEXT_ATTEMPT_AT",
                        "ATTEMPTS");
        assertThat(columns.get("AGGREGATE_ID")).isEqualTo("NO");
        assertThat(columns.get("TOPIC")).isEqualTo("NO");
        assertThat(columns.get("PAYLOAD")).isEqualTo("NO");
        assertThat(columns.get("ATTEMPTS")).isEqualTo("NO");
        assertThat(columns.get("PUBLISHED_AT")).isEqualTo("YES");
        assertThat(columns.get("DEAD_LETTERED_AT")).isEqualTo("YES");
        assertThat(columns.get("NEXT_ATTEMPT_AT")).isEqualTo("YES");
    }

    @Test
    void documentsColumnsMatchEntity() {
        Map<String, String> columns = columnNullability("DOCUMENTS");
        assertThat(columns.keySet())
                .contains(
                        "ID",
                        "USER_ID",
                        "ORIGINAL_FILENAME",
                        "STORAGE_PATH",
                        "TYPE",
                        "STATUS",
                        "ERROR_MESSAGE",
                        "CREATED_AT",
                        "UPDATED_AT",
                        "VERSION");
        assertThat(columns.get("USER_ID")).isEqualTo("NO");
        assertThat(columns.get("ORIGINAL_FILENAME")).isEqualTo("NO");
        assertThat(columns.get("STATUS")).isEqualTo("NO");
        assertThat(columns.get("VERSION")).isEqualTo("NO");
        assertThat(columns.get("STORAGE_PATH")).isEqualTo("YES");
        assertThat(columns.get("ERROR_MESSAGE")).isEqualTo("YES");
    }

    @Test
    void usersEmailHasUniqueConstraint() {
        List<String> constraints =
                jdbcTemplate.queryForList(
                        "SELECT UPPER(constraint_name) FROM information_schema.table_constraints"
                                + " WHERE UPPER(table_name) = 'USERS' AND constraint_type = 'UNIQUE'",
                        String.class);
        assertThat(constraints).contains("UK_USERS_EMAIL");
    }

    @Test
    void outboxEventsHasRequiredIndexes() {
        List<String> indexes =
                jdbcTemplate.queryForList(
                        "SELECT UPPER(index_name) FROM information_schema.indexes"
                                + " WHERE UPPER(table_name) = 'OUTBOX_EVENTS'",
                        String.class);
        assertThat(indexes)
                .contains("IDX_OUTBOX_EVENTS_UNPUBLISHED", "IDX_OUTBOX_EVENTS_AGGREGATE_ID");
    }

    @Test
    void transactionCategoryTablesHaveOwnerAndVersionConstraints() {
        List<String> versionConstraints =
                jdbcTemplate.queryForList(
                        "SELECT UPPER(constraint_name) FROM information_schema.table_constraints"
                                + " WHERE UPPER(table_name) = 'TRANSACTION_VERSIONS'",
                        String.class);
        List<String> categoryConstraints =
                jdbcTemplate.queryForList(
                        "SELECT UPPER(constraint_name) FROM information_schema.table_constraints"
                                + " WHERE UPPER(table_name) = 'PERSONAL_TRANSACTION_CATEGORIES'",
                        String.class);

        assertThat(versionConstraints)
                .contains(
                        "UK_TRANSACTION_VERSIONS_TRANSACTION_USER_VERSION",
                        "FK_TRANSACTION_VERSIONS_USER");
        assertThat(categoryConstraints)
                .contains(
                        "UK_PERSONAL_TRANSACTION_CATEGORIES_USER_NAME",
                        "FK_PERSONAL_TRANSACTION_CATEGORIES_USER");
    }

    @Test
    void tagTablesHaveOwnerConsistentConstraintsAndIndexes() {
        assertThat(columnNullability("PERSONAL_TRANSACTION_TAGS"))
                .containsEntry("USER_ID", "NO")
                .containsEntry("NORMALIZED_NAME", "NO");
        assertThat(columnNullability("TRANSACTION_TAG_ASSIGNMENTS").values()).containsOnly("NO");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT UPPER(constraint_name) FROM information_schema.table_constraints"
                                        + " WHERE UPPER(table_name) IN ('PERSONAL_TRANSACTION_TAGS', 'TRANSACTION_TAG_ASSIGNMENTS')",
                                String.class))
                .contains(
                        "UK_TAGS_OWNER_NAME",
                        "FK_TAGS_USER",
                        "FK_ASSIGNMENTS_OWNER_TAG",
                        "FK_ASSIGNMENTS_OWNER_DOCUMENT",
                        "PK_TRANSACTION_TAG_ASSIGNMENTS");
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT UPPER(index_name) FROM information_schema.indexes"
                                        + " WHERE UPPER(table_name) = 'TRANSACTION_TAG_ASSIGNMENTS'",
                                String.class))
                .contains("IDX_ASSIGNMENTS_OWNER_TAG_TRANSACTION");
    }

    private Map<String, String> columnNullability(String table) {
        return jdbcTemplate.query(
                "SELECT UPPER(column_name) AS c, is_nullable AS n FROM information_schema.columns"
                        + " WHERE UPPER(table_name) = ?",
                rs -> {
                    Map<String, String> map = new HashMap<>();
                    while (rs.next()) {
                        map.put(rs.getString("c"), rs.getString("n"));
                    }
                    return map;
                },
                table);
    }
}
