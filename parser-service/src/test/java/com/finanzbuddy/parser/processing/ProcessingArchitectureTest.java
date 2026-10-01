package com.finanzbuddy.parser.processing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces the hexagonal boundaries of the processing slice (see spec:
 * parser-processing-architecture).
 */
class ProcessingArchitectureTest {

    private static final JavaClasses PROCESSING_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.finanzbuddy.parser.processing");

    private static final String[] FORBIDDEN_IN_CORE = {
        "io.micrometer..",
        "org.springframework..",
        "io.minio..",
        "org.apache.kafka..",
        "com.fasterxml.."
    };

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..processing.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(FORBIDDEN_IN_CORE);
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void portsAreFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..processing.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(FORBIDDEN_IN_CORE);
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void kafkaAdapterHoldsNoOrchestration() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..processing.adapter.in..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("..processing.port.out..")
                        .orShould()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.parser.processing.domain.DocumentProcessor");
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void micrometerIsConfinedToMetricsAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..processing.adapter.out.metrics..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("io.micrometer..");
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void objectStorageClientIsConfinedToStorageAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..processing.adapter.out.storage..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("io.minio..");
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void jacksonAndKafkaAreConfinedToInboundAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..processing.adapter.in.kafka..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("com.fasterxml..", "org.apache.kafka..");
        rule.check(PROCESSING_CLASSES);
    }

    @Test
    void springDataIsConfinedToPersistenceAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..processing.adapter.out.persistence..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("org.springframework.data..");
        rule.check(PROCESSING_CLASSES);
    }
}
