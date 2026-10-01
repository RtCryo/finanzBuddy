package com.finanzbuddy.api.cleanup;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Enforces the hexagonal boundaries of the cleanup slice (see spec: cleanup-architecture). */
class CleanupArchitectureTest {

    private static final JavaClasses CLEANUP_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.finanzbuddy.api.cleanup");

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..cleanup.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "io.minio..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(CLEANUP_CLASSES);
    }

    @Test
    void portsAreFreeOfFrameworkTransportTypes() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..cleanup.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "io.minio..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(CLEANUP_CLASSES);
    }

    @Test
    void schedulerDependsOnInboundPortAndHoldsNoReconciliationLogic() {
        ArchRule dependsOnPort =
                noClasses()
                        .that()
                        .resideInAPackage("..cleanup.adapter.in.scheduler..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.cleanup.domain.StalePendingReconciliation");
        dependsOnPort.check(CLEANUP_CLASSES);

        ArchRule noInfrastructure =
                noClasses()
                        .that()
                        .resideInAPackage("..cleanup.adapter.in.scheduler..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage("io.minio..", "org.springframework.data..");
        noInfrastructure.check(CLEANUP_CLASSES);
    }

    @Test
    void objectStorageClientIsAbsentFromTheSlice() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..cleanup..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("io.minio..");
        rule.check(CLEANUP_CLASSES);
    }

    @Test
    void domainReadsTimeThroughClockPort() {
        ArchRule rule =
                classes()
                        .that()
                        .haveSimpleName("StalePendingReconciliation")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("com.finanzbuddy.api.cleanup.port.out.ClockPort");
        rule.check(CLEANUP_CLASSES);
    }
}
