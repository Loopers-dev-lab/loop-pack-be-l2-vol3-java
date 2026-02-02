package com.loopers;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.DisplayName;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;

/**
 * 아키텍처 테스트
 */
@AnalyzeClasses(packages = "com.loopers", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String INTERFACES = "..interfaces..";

    /**
     * domain 패키지는 application, infrastructure, interfaces 패키지에 의존하지 않는다.
     */
    @ArchTest
    void domain(JavaClasses classes) {
        noClasses().that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE, INTERFACES)
                .check(classes);
    }

    /**
     * infrastructure 패키지는 application, interfaces 패키지에 의존하지 않는다.
     */
    @ArchTest
    void infrastructure(JavaClasses classes) {
        noClasses().that().resideInAPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INTERFACES)
                .check(classes);
    }

    /**
     * application 패키지는 application, interface 패키지에만 존재해야 한다.
     */
    @ArchTest
    void application(JavaClasses classes) {
        classes().that().resideInAPackage(APPLICATION)
                .should().onlyHaveDependentClassesThat().resideInAnyPackage(APPLICATION, INTERFACES)
                .check(classes);
    }

    /**
     * application 패키지는 interfaces 패키지에 의존하지 않는다.
     */
    @ArchTest
    void interfaces(JavaClasses classes) {
        noClasses().that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAPackage(INTERFACES)
                .check(classes);
    }
}
