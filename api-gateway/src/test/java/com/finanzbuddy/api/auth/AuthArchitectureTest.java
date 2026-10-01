package com.finanzbuddy.api.auth;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/** Enforces the hexagonal boundaries of the auth slice (see spec: auth-architecture). */
class AuthArchitectureTest {

    private static final JavaClasses AUTH_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.finanzbuddy.api.auth");

    @Test
    void domainIsFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..auth.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(AUTH_CLASSES);
    }

    @Test
    void portsAreFreeOfFrameworkDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..auth.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "jakarta.persistence..",
                                "jakarta.servlet..",
                                "com.fasterxml..");
        rule.check(AUTH_CLASSES);
    }

    @Test
    void webAdapterDependsOnInboundPortsNotConcreteService() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..auth.adapter.in.web..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("com.finanzbuddy.api.auth.domain.AuthService");
        rule.check(AUTH_CLASSES);
    }

    @Test
    void springSecurityIsConfinedToOutboundAdapters() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..auth.adapter.out..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAPackage("org.springframework.security..");
        rule.check(AUTH_CLASSES);
    }

    @Test
    void springDataRepositoryIsConfinedToPersistenceAdapter() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..auth.adapter.out.persistence..")
                        .should()
                        .dependOnClassesThat()
                        .haveFullyQualifiedName("com.finanzbuddy.api.repository.UserRepository");
        rule.check(AUTH_CLASSES);
    }

    @Test
    void domainSignalsFailuresWithDomainExceptions() {
        // ResponseStatusException (web), DataIntegrityViolationException (dao), and
        // BadCredentialsException (security.authentication) must not leak into the domain.
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..auth.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework.web..",
                                "org.springframework.dao..",
                                "org.springframework.security..");
        rule.check(AUTH_CLASSES);
    }
}
