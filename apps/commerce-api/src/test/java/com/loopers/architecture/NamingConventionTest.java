package com.loopers.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * 네이밍 및 배치 규칙 ArchUnit 테스트.
 *
 * 클래스 이름과 패키지 배치가 프로젝트 컨벤션을 따르는지 검증한다.
 *
 * 출처: package-convention.md § 4, infrastructure-convention.md § 1
 *
 * 복사 위치: apps/commerce-api/src/test/java/com/loopers/architecture/NamingConventionTest.java
 */
@AnalyzeClasses(
    packages = "com.loopers",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class NamingConventionTest {

    // ========================================================================
    // Controller 배치: interfaces 패키지에만 존재
    // 출처: package-convention.md § 4
    // ========================================================================

    @ArchTest
    static final ArchRule Controller는_interfaces_패키지에_있다 =
        classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..interfaces..")
            .because("Controller는 interfaces/{domain}/ 패키지에 배치한다. " +
                     "(package-convention.md § 4)");

    // ========================================================================
    // Facade 배치: application 패키지에만 존재
    // 출처: package-convention.md § 4
    // ========================================================================

    @ArchTest
    static final ArchRule Facade는_application_패키지에_있다 =
        classes()
            .that().haveSimpleNameEndingWith("Facade")
            .should().resideInAPackage("..application..")
            .because("Facade는 application/{domain}/ 패키지에 배치한다. " +
                     "(package-convention.md § 4)");

    // ========================================================================
    // RepositoryImpl 배치: infrastructure 패키지에만 존재
    // 출처: infrastructure-convention.md § 1 — Repository 3-클래스 패턴
    // ========================================================================

    @ArchTest
    static final ArchRule RepositoryImpl은_infrastructure_패키지에_있다 =
        classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAPackage("..infrastructure..")
            .because("RepositoryImpl은 infrastructure/{domain}/ 패키지에 배치한다. " +
                     "(infrastructure-convention.md § 1. Repository 3-클래스 패턴)");

    // ========================================================================
    // JpaRepository 배치: infrastructure 패키지에만 존재
    // 출처: infrastructure-convention.md § 1 — Repository 3-클래스 패턴
    // ========================================================================

    @ArchTest
    static final ArchRule JpaRepository는_infrastructure_패키지에_있다 =
        classes()
            .that().haveSimpleNameEndingWith("JpaRepository")
            .should().resideInAPackage("..infrastructure..")
            .because("JpaRepository 인터페이스는 infrastructure/{domain}/ 패키지에 배치한다. " +
                     "(infrastructure-convention.md § 1. Repository 3-클래스 패턴)");

    // ========================================================================
    // @Repository 어노테이션은 RepositoryImpl에만
    // 출처: infrastructure-convention.md § 1
    // ========================================================================

    @ArchTest
    static final ArchRule Repository_어노테이션은_Impl_클래스에만 =
        classes()
            .that().areAnnotatedWith(
                org.springframework.stereotype.Repository.class)
            .should().haveSimpleNameEndingWith("RepositoryImpl")
            .orShould().haveSimpleNameEndingWith("QueryRepository")
            .because("@Repository는 RepositoryImpl 또는 QueryRepository에만 붙인다. " +
                     "domain Repository 인터페이스는 순수 Java로 유지한다. " +
                     "(infrastructure-convention.md § 1)");

    // ========================================================================
    // ErrorCode는 domain 패키지에 배치
    // 출처: exception-convention.md § 6
    // ========================================================================

    @ArchTest
    static final ArchRule ErrorCode는_domain_또는_support에_있다 =
        classes()
            .that().haveSimpleNameEndingWith("ErrorCode")
            .should().resideInAPackage("..domain..")
            .orShould().resideInAPackage("..support..")
            .because("도메인 ErrorCode는 domain/{domain}/, " +
                     "공통 ErrorType은 support/error/ 에 배치한다. " +
                     "(exception-convention.md § 6)");
}
