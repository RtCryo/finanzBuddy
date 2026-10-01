package com.finanzbuddy.parser.parsing;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

/**
 * Enforces the hexagonal boundaries of the parsing slice (see spec: parser-parsing-architecture).
 */
class ParsingArchitectureTest {

    private static final JavaClasses PARSING_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.finanzbuddy.parser.parsing");

    @Test
    void domainIsFreeOfFrameworkAndFormatDependencies() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..parsing.domain..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "com.opencsv..",
                                "org.apache.pdfbox..",
                                "com.fasterxml..",
                                "org.apache.commons.io..",
                                "jakarta.persistence..");
        rule.check(PARSING_CLASSES);
    }

    @Test
    void portIsFreeOfFrameworkAndPersistenceTypes() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideInAPackage("..parsing.port..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "org.springframework..",
                                "com.opencsv..",
                                "org.apache.pdfbox..",
                                "com.fasterxml..",
                                "org.apache.commons.io..",
                                "jakarta.persistence..");
        rule.check(PARSING_CLASSES);
    }

    @Test
    void formatLibrariesAreConfinedToAdapters() {
        ArchRule rule =
                noClasses()
                        .that()
                        .resideOutsideOfPackage("..parsing.adapter..")
                        .should()
                        .dependOnClassesThat()
                        .resideInAnyPackage(
                                "com.opencsv..",
                                "org.apache.pdfbox..",
                                "com.fasterxml..",
                                "org.apache.commons.io..");
        rule.check(PARSING_CLASSES);
    }
}
