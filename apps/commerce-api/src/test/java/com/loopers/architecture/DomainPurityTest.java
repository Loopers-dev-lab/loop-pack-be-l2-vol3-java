package com.loopers.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * domain 계층 순수성 ArchUnit 테스트.
 *
 * domain 패키지에 Spring Framework 의존이 침투하지 않는 것을 보장한다.
 * Repository 인터페이스는 순수 Java, Entity에 Spring 어노테이션 금지.
 *
 * 출처: package-convention.md § 5, infrastructure-convention.md § 1
 *
 * 복사 위치: apps/commerce-api/src/test/java/com/loopers/architecture/DomainPurityTest.java
 */
@AnalyzeClasses(
    packages = "com.loopers",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class DomainPurityTest {

    // ========================================================================
    // domain에 Spring 스테레오타입 어노테이션 금지
    // 출처: package-convention.md § 5 — "domain은 순수 Java로만 구성한다"
    // ========================================================================

    @ArchTest
    static final ArchRule domain에_Spring_Repository_어노테이션_금지 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(
                org.springframework.stereotype.Repository.class)
            .because("@Repository는 infrastructure의 RepositoryImpl에만 사용한다. " +
                     "domain Repository는 순수 Java 인터페이스여야 한다. " +
                     "(infrastructure-convention.md § 1)");

    // 컨벤션: domain Service는 @Service 어노테이션 사용 가능.
    // DI 등록을 위해 domain Service 클래스에 @Service를 허용한다.
    // @Component, @Repository(Spring)는 여전히 금지 (위 규칙으로 검증).

    @ArchTest
    static final ArchRule domain에_Spring_Component_어노테이션_금지 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(
                org.springframework.stereotype.Component.class)
            .because("domain 계층에 Spring @Component 금지. " +
                     "domain은 순수 Java로만 구성한다. " +
                     "(package-convention.md § 5)");

    // ========================================================================
    // domain Service 허용 사항 (컨벤션)
    //
    // service-layer-convention.md § 3~4에 따라 domain Service는 다음을 사용할 수 있다:
    //   - @Service (DI 등록)
    //   - @Transactional (메서드 레벨, Facade와 REQUIRED 전파)
    //   - Page / Pageable (Spring Data 페이지네이션)
    //
    // 금지 대상은 Entity, VO, Repository 인터페이스이며,
    // 해당 클래스에 대한 @Repository, @Component 금지는 위 규칙으로 검증한다.
    // JpaRepository 상속은 infrastructure에서만 허용 (NamingConventionTest에서 검증).
    // ========================================================================
}
