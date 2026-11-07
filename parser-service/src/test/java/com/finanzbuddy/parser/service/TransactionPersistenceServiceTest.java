package com.finanzbuddy.parser.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finanzbuddy.parser.entity.Transaction;
import com.finanzbuddy.parser.repository.ProcessedDocumentRepository;
import com.finanzbuddy.parser.repository.StoredTransactionKey;
import com.finanzbuddy.parser.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "parser.formats.csv=false")
@EmbeddedKafka(
        partitions = 1,
        topics = {"documents.csv", "documents.csv.dlt"})
@ActiveProfiles("test")
class TransactionPersistenceServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 8, 1);

    @Autowired private TransactionPersistenceService service;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private ProcessedDocumentRepository processedDocumentRepository;

    @AfterEach
    void cleanUp() {
        transactionRepository.deleteAll();
        processedDocumentRepository.deleteAll();
    }

    @Test
    void sameFileUploadedAgainIsFullySkipped() {
        List<Transaction> file =
                List.of(tx(1, DAY, "-5.00", "Shop", "a"), tx(1, DAY, "9", "B", "b"));
        service.saveIfNotProcessed(1L, 1L, file);

        PersistenceOutcome second = service.saveIfNotProcessed(2L, 1L, copyOf(2, file));

        assertThat(second).isEqualTo(new PersistenceOutcome(0, 2));
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void overlappingStatementSavesOnlyNewLines() {
        service.saveIfNotProcessed(
                1L,
                1L,
                List.of(tx(1, DAY, "-1", "A", "x"), tx(1, DAY.plusDays(1), "-2", "B", "y")));

        PersistenceOutcome outcome =
                service.saveIfNotProcessed(
                        2L,
                        1L,
                        List.of(
                                tx(2, DAY.plusDays(1), "-2", "B", "y"),
                                tx(2, DAY.plusDays(2), "-3", "C", "z")));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(1, 1));
        assertThat(transactionRepository.findByDocumentId(2L))
                .extracting(Transaction::getCounterparty)
                .containsExactly("C");
    }

    @Test
    void differentOwnersNeverMatch() {
        service.saveIfNotProcessed(1L, 1L, List.of(tx(1, DAY, "-1", "A", "x")));

        PersistenceOutcome outcome =
                service.saveIfNotProcessed(2L, 2L, List.of(tx(2, DAY, "-1", "A", "x", 2L)));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(1, 0));
    }

    @Test
    void twoEqualPaymentsOnFirstImportAreBothSaved() {
        PersistenceOutcome outcome =
                service.saveIfNotProcessed(
                        1L,
                        1L,
                        List.of(tx(1, DAY, "-3", "Cafe", "c"), tx(1, DAY, "-3", "Cafe", "c")));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(2, 0));
    }

    @Test
    void reUploadOfTwoEqualPaymentsSkipsBoth() {
        List<Transaction> file =
                List.of(tx(1, DAY, "-3", "Cafe", "c"), tx(1, DAY, "-3", "Cafe", "c"));
        service.saveIfNotProcessed(1L, 1L, file);

        PersistenceOutcome outcome = service.saveIfNotProcessed(2L, 1L, copyOf(2, file));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(0, 2));
        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    @Test
    void threeIncomingAgainstOneStoredSavesSurplus() {
        service.saveIfNotProcessed(1L, 1L, List.of(tx(1, DAY, "-3", "Cafe", "c")));

        PersistenceOutcome outcome =
                service.saveIfNotProcessed(
                        2L,
                        1L,
                        List.of(
                                tx(2, DAY, "-3", "Cafe", "c"),
                                tx(2, DAY, "-3", "Cafe", "c"),
                                tx(2, DAY, "-3", "Cafe", "c")));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(2, 1));
    }

    @Test
    void editedCategoryStillMatches() {
        service.saveIfNotProcessed(1L, 1L, List.of(tx(1, DAY, "-3", "Cafe", "c")));
        Transaction stored = transactionRepository.findAll().get(0);
        stored.setCategory("Edited");
        transactionRepository.saveAndFlush(stored);

        PersistenceOutcome outcome =
                service.saveIfNotProcessed(2L, 1L, List.of(tx(2, DAY, "-3", "Cafe", "c")));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(0, 1));
        assertThat(transactionRepository.findAll().get(0).getCategory()).isEqualTo("Edited");
    }

    @Test
    void keyIgnoresWhitespaceNullVersusEmptyAndAmountScale() {
        service.saveIfNotProcessed(1L, 1L, List.of(tx(1, DAY, "-3.5", " Cafe ", null)));

        PersistenceOutcome outcome =
                service.saveIfNotProcessed(2L, 1L, List.of(tx(2, DAY, "-3.5000", "Cafe", "")));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(0, 1));
    }

    @Test
    void linesWithoutBookingDateAreComparedToo() {
        List<Transaction> file = List.of(tx(1, null, "-3", "Cafe", "c"));
        service.saveIfNotProcessed(1L, 1L, file);

        PersistenceOutcome outcome = service.saveIfNotProcessed(2L, 1L, copyOf(2, file));

        assertThat(outcome).isEqualTo(new PersistenceOutcome(0, 1));
    }

    @Test
    void keyQueryReturnsOnlyOwnRowsInRangeAndUndated() {
        service.saveIfNotProcessed(
                1L,
                1L,
                List.of(
                        tx(1, DAY.minusDays(5), "-1", "Old", "o"),
                        tx(1, DAY, "-2", "InRange", "i"),
                        tx(1, null, "-3", "Undated", "u")));
        service.saveIfNotProcessed(2L, 2L, List.of(tx(2, DAY, "-4", "Other", "x", 2L)));

        List<StoredTransactionKey> keys =
                transactionRepository.findKeysInBookingRange(1L, DAY, DAY.plusDays(1));

        assertThat(keys)
                .extracting(StoredTransactionKey::counterparty)
                .containsExactlyInAnyOrder("InRange", "Undated");
        assertThat(transactionRepository.findKeysWithoutBookingDate(1L))
                .extracting(StoredTransactionKey::counterparty)
                .containsExactly("Undated");
    }

    @Test
    void recordsOutcomeAndExposesItForRedelivery() {
        service.saveIfNotProcessed(1L, 1L, List.of(tx(1, DAY, "-3", "Cafe", "c")));
        service.saveIfNotProcessed(2L, 1L, List.of(tx(2, DAY, "-3", "Cafe", "c")));

        assertThat(service.findOutcome(1L)).contains(new PersistenceOutcome(1, 0));
        assertThat(service.findOutcome(2L)).contains(new PersistenceOutcome(0, 1));
        assertThat(service.findOutcome(99L)).isEmpty();
    }

    @Test
    void redeliveredDocumentIsRejectedAndCountsStayUnchanged() {
        List<Transaction> file = List.of(tx(1, DAY, "-3", "Cafe", "c"));
        service.saveIfNotProcessed(1L, 1L, file);

        assertThatThrownBy(() -> service.saveIfNotProcessed(1L, 1L, copyOf(1, file)))
                .isInstanceOf(DuplicateDocumentException.class)
                .hasMessageContaining("1");

        assertThat(transactionRepository.count()).isEqualTo(1);
        assertThat(service.findOutcome(1L)).contains(new PersistenceOutcome(1, 0));
    }

    private static List<Transaction> copyOf(long documentId, List<Transaction> source) {
        return source.stream()
                .map(
                        t ->
                                tx(
                                        documentId,
                                        t.getBookingDate(),
                                        t.getAmount().toPlainString(),
                                        t.getCounterparty(),
                                        t.getDescription(),
                                        t.getUserId()))
                .toList();
    }

    private static Transaction tx(
            long documentId, LocalDate booking, String amount, String counterparty, String desc) {
        return tx(documentId, booking, amount, counterparty, desc, 1L);
    }

    private static Transaction tx(
            long documentId,
            LocalDate booking,
            String amount,
            String counterparty,
            String desc,
            long userId) {
        Transaction t = new Transaction();
        t.setDocumentId(documentId);
        t.setUserId(userId);
        t.setBookingDate(booking);
        t.setValueDate(booking);
        t.setAmount(new BigDecimal(amount));
        t.setCurrency("EUR");
        t.setCounterparty(counterparty);
        t.setDescription(desc);
        return t;
    }
}
