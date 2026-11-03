package com.finanzbuddy.api.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.api.document.dto.DocumentStatusUpdateRequest;
import com.finanzbuddy.api.document.dto.TransactionEditRequest;
import com.finanzbuddy.api.document.dto.TransactionTagRule.Condition;
import com.finanzbuddy.api.document.dto.TransactionTagRuleRequest;
import com.finanzbuddy.api.entity.DocumentStatus;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class TransactionTagRuleServiceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTagRuleService rules;
    @Autowired DocumentService documents;
    @Autowired TransactionTagService editor;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        "jdbc:h2:mem:ruletestdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        jdbc.execute(
                "CREATE TABLE IF NOT EXISTS transactions (id BIGINT PRIMARY KEY, document_id BIGINT,"
                        + " user_id BIGINT, booking_date DATE, value_date DATE, amount DECIMAL(19,4),"
                        + " currency VARCHAR(3), counterparty VARCHAR(500), description CLOB, category VARCHAR(255))");
        jdbc.update("DELETE FROM transaction_tag_rule_contributions");
        jdbc.update("DELETE FROM transaction_tag_assignments");
        jdbc.update("DELETE FROM transaction_tag_rule_conditions");
        jdbc.update("DELETE FROM transaction_tag_rules");
        jdbc.update("DELETE FROM personal_transaction_tags");
        jdbc.update("DELETE FROM personal_transaction_categories");
        jdbc.update("DELETE FROM transactions");
        jdbc.update("DELETE FROM documents");
        jdbc.update("DELETE FROM users");
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (1,'one@example.com','x')");
        jdbc.update("INSERT INTO users (id,email,password_hash) VALUES (2,'two@example.com','x')");
        jdbc.update(
                "INSERT INTO documents (id,user_id,original_filename,type,status,version)"
                        + " VALUES (10,1,'a.csv','CSV','PROCESSED',0)");
        jdbc.update(
                "INSERT INTO documents (id,user_id,original_filename,type,status,version)"
                        + " VALUES (20,2,'b.csv','CSV','PROCESSED',0)");
        jdbc.update(
                "INSERT INTO transactions VALUES (100,10,1,NULL,NULL,1,'EUR',' X ','Payment yupi',NULL)");
        jdbc.update(
                "INSERT INTO transactions VALUES (200,20,2,NULL,NULL,1,'EUR',' X ','Payment yupi',NULL)");
        jdbc.update(
                "INSERT INTO personal_transaction_tags (id,user_id,name,normalized_name)"
                        + " VALUES (1000,1,'A','a'),(1001,1,'B','b'),(2000,2,'Other','other')");
    }

    @Test
    void createChoicesAndOwnerIsolation() {
        var future = rules.create("one@example.com", request(1000, false, "X"));
        assertThat(assigned(1, 100)).isZero();
        assertThat(rules.list("one@example.com")).containsExactly(future);
        assertThat(rules.list("two@example.com")).isEmpty();
        rules.create("one@example.com", request(1001, true, "X"));
        assertThat(assigned(1, 100)).isEqualTo(1);
        assertThat(assigned(2, 200)).isZero();
        assertThatThrownBy(() -> rules.create("one@example.com", request(2000, true, "X")))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(rules.list("one@example.com")).hasSize(2);
    }

    @Test
    void distinctMatchingTargetsProduceDistinctEffectiveTags() {
        rules.create("one@example.com", request(1000, true, "X"));
        rules.create("one@example.com", request(1001, true, "X"));
        assertThat(assigned(1, 100)).isEqualTo(2);
    }

    @Test
    void createsOrReusesTagWithRuleAndRollsBackOnFailure() {
        var conditions = List.of(new Condition("COUNTERPARTY", "EXACT", "X"));
        var created =
                rules.create(
                        "one@example.com",
                        new TransactionTagRuleRequest(null, " New tag ", "AND", conditions, true));
        assertThat(assigned(1, 100, created.tagId())).isEqualTo(1);
        var reused =
                rules.create(
                        "one@example.com",
                        new TransactionTagRuleRequest(null, " new TAG ", "AND", conditions, false));
        assertThat(reused.tagId()).isEqualTo(created.tagId());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM personal_transaction_tags WHERE user_id = 1 AND normalized_name = 'new tag'",
                                Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                rules.create(
                                        "one@example.com",
                                        new TransactionTagRuleRequest(
                                                null, "   ", "AND", conditions, false)))
                .isInstanceOf(ResponseStatusException.class);
        jdbc.execute(
                "ALTER TABLE transaction_tag_rule_conditions ADD CONSTRAINT reject_new_tag CHECK (match_value <> 'FAIL')");
        try {
            assertThatThrownBy(
                            () ->
                                    rules.create(
                                            "one@example.com",
                                            new TransactionTagRuleRequest(
                                                    null,
                                                    "Rollback",
                                                    "AND",
                                                    List.of(
                                                            new Condition(
                                                                    "COUNTERPARTY",
                                                                    "EXACT",
                                                                    "FAIL")),
                                                    true)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM personal_transaction_tags WHERE user_id = 1 AND normalized_name = 'rollback'",
                                    Integer.class))
                    .isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE transaction_tag_rule_conditions DROP CONSTRAINT reject_new_tag");
        }
    }

    @Test
    void categoryRuleUsesLatestSavedCategoryWithoutWritingVersionsOrCrossingOwners() {
        jdbc.update("UPDATE transactions SET category = 'Other' WHERE id = 100");
        jdbc.update("UPDATE transactions SET category = 'Travel' WHERE id = 200");
        editor.save("one@example.com", 100, new TransactionEditRequest("Travel", null, List.of()));
        int versions =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM transaction_versions WHERE transaction_id = 100",
                        Integer.class);
        rules.create(
                "one@example.com",
                new TransactionTagRuleRequest(
                        1000L,
                        "AND",
                        List.of(new Condition("CATEGORY", "EXACT", " travel ")),
                        true));
        assertThat(assigned(1, 100, 1000)).isEqualTo(1);
        assertThat(assigned(2, 200)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transaction_versions WHERE transaction_id = 100",
                                Integer.class))
                .isEqualTo(versions);
    }

    @Test
    void createsCategoryWithRuleAndRollsBackBothOnFailure() {
        var request =
                new TransactionTagRuleRequest(
                        1000L,
                        null,
                        "AND",
                        List.of(new Condition("CATEGORY", "EXACT", " Utilities ")),
                        false,
                        List.of(" Utilities "));
        var created = rules.create("one@example.com", request);
        assertThat(created.conditions().getFirst().value()).isEqualTo("Utilities");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT name FROM personal_transaction_categories WHERE user_id = 1",
                                String.class))
                .isEqualTo("Utilities");
        rules.update(
                "one@example.com",
                created.id(),
                new TransactionTagRuleRequest(
                        1000L,
                        null,
                        "AND",
                        List.of(new Condition("CATEGORY", "EXACT", "utilities")),
                        false,
                        List.of("utilities")));
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM personal_transaction_categories WHERE user_id = 1",
                                Integer.class))
                .isEqualTo(1);
        assertThatThrownBy(
                        () ->
                                rules.create(
                                        "one@example.com",
                                        new TransactionTagRuleRequest(
                                                1000L,
                                                null,
                                                "AND",
                                                List.of(
                                                        new Condition(
                                                                "COUNTERPARTY", "EXACT", "X")),
                                                false,
                                                List.of("Unrelated"))))
                .isInstanceOf(ResponseStatusException.class);
        jdbc.execute(
                "ALTER TABLE transaction_tag_rule_conditions ADD CONSTRAINT reject_category CHECK (match_value <> 'Rollback')");
        try {
            assertThatThrownBy(
                            () ->
                                    rules.create(
                                            "one@example.com",
                                            new TransactionTagRuleRequest(
                                                    1000L,
                                                    null,
                                                    "AND",
                                                    List.of(
                                                            new Condition(
                                                                    "CATEGORY",
                                                                    "EXACT",
                                                                    "Rollback")),
                                                    false,
                                                    List.of("Rollback"))))
                    .isInstanceOf(RuntimeException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM personal_transaction_categories WHERE user_id = 1 AND normalized_name = 'rollback'",
                                    Integer.class))
                    .isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE transaction_tag_rule_conditions DROP CONSTRAINT reject_category");
        }
    }

    @Test
    void categoryRuleMatchesImportedCategoryAtCompletion() {
        jdbc.update("UPDATE transactions SET category = 'Groceries' WHERE id = 100");
        rules.create(
                "one@example.com",
                new TransactionTagRuleRequest(
                        1000L,
                        "AND",
                        List.of(new Condition("CATEGORY", "CONTAINS", "grocer")),
                        false));
        jdbc.update("UPDATE documents SET status = 'PROCESSING' WHERE id = 10");
        documents.updateDocumentStatus(
                10L, new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null));
        assertThat(assigned(1, 100, 1000)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM transaction_versions WHERE transaction_id = 100",
                                Integer.class))
                .isZero();
    }

    @Test
    void failedHistoricalCreateAndEditRollBackRuleAndSources() {
        jdbc.execute(
                "ALTER TABLE transaction_tag_rule_contributions"
                        + " ADD CONSTRAINT reject_create CHECK (transaction_id <> 100)");
        try {
            assertThatThrownBy(() -> rules.create("one@example.com", request(1000, true, "X")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(rules.list("one@example.com")).isEmpty();
            assertThat(assigned(1, 100)).isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE transaction_tag_rule_contributions DROP CONSTRAINT reject_create");
        }
        var saved = rules.create("one@example.com", request(1000, true, "X"));
        jdbc.execute(
                "ALTER TABLE transaction_tag_rule_contributions"
                        + " ADD CONSTRAINT reject_edit CHECK (tag_id <> 1001)");
        try {
            assertThatThrownBy(
                            () ->
                                    rules.update(
                                            "one@example.com",
                                            saved.id(),
                                            request(1001, true, "X")))
                    .isInstanceOf(RuntimeException.class);
            assertThat(rules.list("one@example.com").getFirst().tagId()).isEqualTo(1000);
            assertThat(assigned(1, 100, 1000)).isEqualTo(1);
            assertThat(contributions(1, 100)).isEqualTo(1);
        } finally {
            jdbc.execute(
                    "ALTER TABLE transaction_tag_rule_contributions DROP CONSTRAINT reject_edit");
        }
    }

    @Test
    void failedCleanupRollsBackDelete() {
        var saved = rules.create("one@example.com", request(1000, true, "X"));
        jdbc.execute(
                "CREATE TABLE assignment_guard (user_id BIGINT, transaction_id BIGINT,"
                        + " tag_id BIGINT, FOREIGN KEY (user_id,transaction_id,tag_id)"
                        + " REFERENCES transaction_tag_assignments(user_id,transaction_id,tag_id))");
        jdbc.update("INSERT INTO assignment_guard VALUES (1,100,1000)");
        try {
            assertThatThrownBy(() -> rules.delete("one@example.com", saved.id(), true))
                    .isInstanceOf(RuntimeException.class);
            assertThat(rules.list("one@example.com")).hasSize(1);
            assertThat(assigned(1, 100)).isEqualTo(1);
            assertThat(contributions(1, 100)).isEqualTo(1);
        } finally {
            jdbc.execute("DROP TABLE assignment_guard");
        }
    }

    @Test
    void editChoicesPreserveOrReconcileOldContributions() {
        var rule = rules.create("one@example.com", request(1000, true, "X"));
        rules.update("one@example.com", rule.id(), request(1001, false, "X"));
        assertThat(assigned(1, 100, 1000)).isEqualTo(1);
        assertThat(assigned(1, 100, 1001)).isZero();
        rules.update("one@example.com", rule.id(), request(1001, true, "X"));
        assertThat(assigned(1, 100, 1000)).isZero();
        assertThat(assigned(1, 100, 1001)).isEqualTo(1);
        assertThatThrownBy(
                        () -> rules.update("two@example.com", rule.id(), request(2000, true, "X")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cleanupKeepsManualAndOtherRuleSources() {
        var first = rules.create("one@example.com", request(1000, true, "X"));
        var second = rules.create("one@example.com", request(1000, true, "X"));
        rules.delete("one@example.com", first.id(), true);
        assertThat(assigned(1, 100)).isEqualTo(1);
        rules.delete("one@example.com", second.id(), true);
        assertThat(assigned(1, 100)).isZero();
        var retained = rules.create("one@example.com", request(1000, true, "X"));
        rules.delete("one@example.com", retained.id(), false);
        assertThat(assigned(1, 100)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT manual_assignment FROM transaction_tag_assignments"
                                        + " WHERE user_id = 1 AND transaction_id = 100",
                                Boolean.class))
                .isTrue();
    }

    @Test
    void cleanupPreservesPreexistingManualAssignment() {
        jdbc.update(
                "INSERT INTO transaction_tag_assignments"
                        + " (user_id,document_id,transaction_id,tag_id) VALUES (1,10,100,1000)");
        var rule = rules.create("one@example.com", request(1000, true, "X"));
        rules.delete("one@example.com", rule.id(), true);
        assertThat(assigned(1, 100)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT manual_assignment FROM transaction_tag_assignments"
                                        + " WHERE user_id = 1 AND transaction_id = 100",
                                Boolean.class))
                .isTrue();
    }

    @Test
    void importCompletionIsAtomicAndDuplicateDoesNotReevaluate() {
        var rule = rules.create("one@example.com", request(1000, false, "X"));
        rules.create("one@example.com", request(1001, false, "X"));
        jdbc.update("UPDATE documents SET status = 'PROCESSING' WHERE id = 10");
        jdbc.execute(
                "ALTER TABLE transaction_tag_rule_contributions"
                        + " ADD CONSTRAINT reject_rule_application CHECK (transaction_id <> 100)");
        try {
            assertThatThrownBy(
                            () ->
                                    documents.updateDocumentStatus(
                                            10L,
                                            new DocumentStatusUpdateRequest(
                                                    DocumentStatus.PROCESSED, null)))
                    .isInstanceOf(RuntimeException.class);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT status FROM documents WHERE id = 10", String.class))
                    .isEqualTo("PROCESSING");
            assertThat(assigned(1, 100)).isZero();
        } finally {
            jdbc.execute(
                    "ALTER TABLE transaction_tag_rule_contributions DROP CONSTRAINT reject_rule_application");
        }
        documents.updateDocumentStatus(
                10L, new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null));
        assertThat(assigned(1, 100)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM documents WHERE id = 10", String.class))
                .isEqualTo("PROCESSED");
        rules.update("one@example.com", rule.id(), request(1001, false, "X"));
        documents.updateDocumentStatus(
                10L, new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null));
        assertThat(assigned(1, 100, 1000)).isEqualTo(1);
        assertThat(assigned(1, 100, 1001)).isEqualTo(1);
        assertThat(contributions(1, 100)).isEqualTo(2);
    }

    @Test
    void editorKeepsVisibleRuleSourcesOnUnchangedSaveAndRemovesThemOnOmission() {
        var first = rules.create("one@example.com", request(1000, true, "X"));
        var second = rules.create("one@example.com", request(1000, true, "X"));
        editor.save(
                "one@example.com",
                100,
                new TransactionEditRequest(
                        null, null, List.of(new TransactionEditRequest.TagSelection(1000L, null))));
        assertThat(assigned(1, 100)).isEqualTo(1);
        assertThat(contributions(1, 100)).isEqualTo(2);
        editor.save("one@example.com", 100, new TransactionEditRequest(null, null, List.of()));
        assertThat(assigned(1, 100)).isZero();
        assertThat(contributions(1, 100)).isZero();
        rules.update("one@example.com", first.id(), request(1000, true, "X"));
        assertThat(assigned(1, 100)).isEqualTo(1);
        rules.delete("one@example.com", second.id(), true);
        assertThat(assigned(1, 100)).isEqualTo(1);
    }

    @Test
    void concurrentImportAndRuleEditConvergeOnNewTarget() throws Exception {
        var rule = rules.create("one@example.com", request(1000, false, "X"));
        jdbc.update("UPDATE documents SET status = 'PROCESSING' WHERE id = 10");
        race(
                () ->
                        documents.updateDocumentStatus(
                                10L,
                                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null)),
                () -> rules.update("one@example.com", rule.id(), request(1001, true, "X")));
        assertThat(assigned(1, 100, 1000)).isZero();
        assertThat(assigned(1, 100, 1001)).isEqualTo(1);
        assertThat(contributions(1, 100)).isEqualTo(1);
    }

    @Test
    void concurrentImportAndRuleDeleteLeaveNoRuleTag() throws Exception {
        var rule = rules.create("one@example.com", request(1000, false, "X"));
        jdbc.update("UPDATE documents SET status = 'PROCESSING' WHERE id = 10");
        race(
                () ->
                        documents.updateDocumentStatus(
                                10L,
                                new DocumentStatusUpdateRequest(DocumentStatus.PROCESSED, null)),
                () -> rules.delete("one@example.com", rule.id(), true));
        assertThat(assigned(1, 100)).isZero();
        assertThat(contributions(1, 100)).isZero();
    }

    @Test
    void concurrentManualSaveAndReconciliationKeepSourcesConsistent() throws Exception {
        var rule = rules.create("one@example.com", request(1000, true, "X"));
        race(
                () ->
                        editor.save(
                                "one@example.com",
                                100,
                                new TransactionEditRequest(null, null, List.of())),
                () -> rules.update("one@example.com", rule.id(), request(1000, true, "X")));
        assertThat(assigned(1, 100)).isBetween(0, 1);
        assertThat(assigned(1, 100)).isEqualTo(contributions(1, 100));
    }

    private static void race(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a =
                    executor.submit(
                            () -> {
                                start.await();
                                first.run();
                                return null;
                            });
            var b =
                    executor.submit(
                            () -> {
                                start.await();
                                second.run();
                                return null;
                            });
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
    }

    private TransactionTagRuleRequest request(long tag, boolean apply, String value) {
        return new TransactionTagRuleRequest(
                tag, "AND", List.of(new Condition("COUNTERPARTY", "EXACT", value)), apply);
    }

    private int assigned(long owner, long transaction) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaction_tag_assignments"
                        + " WHERE user_id = ? AND transaction_id = ?",
                Integer.class,
                owner,
                transaction);
    }

    private int assigned(long owner, long transaction, long tag) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaction_tag_assignments"
                        + " WHERE user_id = ? AND transaction_id = ? AND tag_id = ?",
                Integer.class,
                owner,
                transaction,
                tag);
    }

    private int contributions(long owner, long transaction) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM transaction_tag_rule_contributions"
                        + " WHERE user_id = ? AND transaction_id = ?",
                Integer.class,
                owner,
                transaction);
    }
}
