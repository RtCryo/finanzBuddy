package com.finanzbuddy.parser;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@EmbeddedKafka(
        partitions = 1,
        topics = {"documents.csv", "documents.csv.dlt"})
@DirtiesContext
@ActiveProfiles("test")
class ParserServiceApplicationTests {

    @Test
    void contextLoads() {}
}
