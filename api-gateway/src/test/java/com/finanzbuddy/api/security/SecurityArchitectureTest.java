package com.finanzbuddy.api.security;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Enforces the hexagonal boundaries of the security slice (see spec: security-architecture). */
class SecurityArchitectureTest {

    private static final JavaClasses SECURITY_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.finanzbuddy.api.security");

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..security.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..", "jakarta.servlet..", "io.jsonwebtoken..");
        rule.check(SECURITY_CLASSES);
    }

    @Test
    void portsAreFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..security.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..", "jakarta.servlet..", "io.jsonwebtoken..");
        rule.check(SECURITY_CLASSES);
    }

    @Test
    void jwtLibraryIsConfinedToTheTokenAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..security.adapter.out.token..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("io.jsonwebtoken..");
        rule.check(SECURITY_CLASSES);
    }

    @Test
    void webFiltersDependOnInboundPortsNotDomainAuthenticators() {
        // Filters may touch domain data types (Principal, token exceptions) returned/thrown across
        // the inbound ports, but must resolve authentication through the ports — never the concrete
        // domain authenticators.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..security.adapter.in.web..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.security.domain.RequestAuthenticator")
                        .orShould()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName(
                                "com.finanzbuddy.api.security.domain.InternalAuthenticator");
        rule.check(SECURITY_CLASSES);
    }
}
