package com.finanzbuddy.api.document;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Enforces the hexagonal boundaries of the document slice (see spec: document-architecture). */
class DocumentArchitectureTest {

    // The cleanup slice is its own top-level slice (com.finanzbuddy.api.cleanup) with its own
    // architecture test, so it is not imported here.
    private static final JavaClasses DOCUMENT_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages(
                            "com.finanzbuddy.api.document.domain",
                            "com.finanzbuddy.api.document.port",
                            "com.finanzbuddy.api.document.adapter");

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..document.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "io.minio..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(DOCUMENT_CLASSES);
    }

    @Test
    void portsAreFreeOfFrameworkTransportTypes() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..document.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "io.minio..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(DOCUMENT_CLASSES);
    }

    @Test
    void webAdapterDependsOnInboundPortsNotConcreteService() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..document.adapter.in.web..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.document.domain.DocumentService");
        rule.check(DOCUMENT_CLASSES);
    }

    @Test
    void objectStorageClientIsConfinedToStorageAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..document.adapter.out.storage..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("io.minio..");
        rule.check(DOCUMENT_CLASSES);
    }

    @Test
    void jacksonIsConfinedToMessagingAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..document.adapter.out.messaging..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("com.fasterxml..");
        rule.check(DOCUMENT_CLASSES);
    }

    @Test
    void springDataRepositoriesAreConfinedToOutboundAdapters() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..document.adapter.out..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.document.adapter.out.persistence"
                                        + ".DocumentRepository")
                        .orShould()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.document.adapter.out.persistence"
                                        + ".OutboxEventRepository");
        rule.check(DOCUMENT_CLASSES);
    }
}
