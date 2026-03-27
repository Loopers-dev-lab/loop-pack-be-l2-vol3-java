package com.loopers.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * 계층 간 의존 방향 ArchUnit 테스트.
 *
 * 출처: package-convention.md § 5. 의존 방향 규칙
 * 규칙: interfaces → application → domain ← infrastructure
 *
 * 복사 위치: apps/commerce-api/src/test/java/com/loopers/architecture/LayeredArchitectureTest.java
 */
@AnalyzeClasses(
    packages = "com.loopers",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class LayeredArchitectureTest {

    // ========================================================================
    // 계층 의존 방향: domain은 어떤 계층도 알지 못한다
    // 출처: package-convention.md § 5
    // ========================================================================

    @ArchTest
    static final ArchRule domain은_infrastructure를_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("domain 계층은 순수 Java로만 구성한다. " +
                     "infrastructure 의존은 DIP 위반이다. " +
                     "(package-convention.md § 5)");

    @ArchTest
    static final ArchRule domain은_application을_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..application..")
            .because("domain → application 역방향 의존 금지. " +
                     "(package-convention.md § 5)");

    @ArchTest
    static final ArchRule domain은_interfaces를_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAPackage("..interfaces..")
            .because("domain → interfaces 역방향 의존 금지. " +
                     "(package-convention.md § 5)");

    @ArchTest
    static final ArchRule interfaces는_infrastructure를_직접_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..interfaces..")
            .and().resideOutsideOfPackage("..interfaces.datagenerator..")
            .should().dependOnClassesThat()
            .resideInAPackage("..infrastructure..")
            .because("Controller는 Facade를 통해 접근한다. " +
                     "Repository 직접 접근 금지. " +
                     "(package-convention.md § 5) " +
                     "[예외: datagenerator는 테스트 데이터 유틸리티]");

    @ArchTest
    static final ArchRule application은_interfaces를_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .resideInAPackage("..interfaces..")
            .because("Facade가 Controller/Request/Response DTO를 알면 안 된다. " +
                     "(package-convention.md § 5)");

    // ========================================================================
    // 순환 의존 금지: 도메인 간 순환 참조 방지
    // 출처: package-convention.md § 5. 의존 방향 규칙
    // ========================================================================

    @ArchTest
    static final ArchRule 도메인_간_순환_의존이_없다 =
        slices()
            .matching("..domain.(*)..")
            .should().beFreeOfCycles()
            .because("도메인 간 순환 의존은 결합도를 높인다. " +
                     "타 도메인 접근은 Facade에서 조율한다. " +
                     "(package-convention.md § 5)");
}
