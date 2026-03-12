package com.loopers.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 서비스 계층 호출 규칙 ArchUnit 테스트.
 *
 * Facade ↔ Service 간 호출 규칙을 바이트코드 수준에서 검증한다.
 *
 * 출처: service-layer-convention.md § 5. 계층 간 호출 규칙
 */
@AnalyzeClasses(
    packages = "com.loopers",
    importOptions = ImportOption.DoNotIncludeTests.class
)
class ServiceLayerTest {

    // ========================================================================
    // Facade → Facade 호출 금지
    // 출처: service-layer-convention.md § 5
    // "Facade → 타 Facade ❌ 순환 의존, 트랜잭션 경계 혼란"
    // ========================================================================

    @ArchTest
    static final ArchRule Facade는_다른_Facade를_의존하지_않는다 =
        noClasses()
            .that().haveSimpleNameEndingWith("Facade")
            .and().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Facade")
            .because("Facade → Facade 호출 금지. " +
                     "순환 의존과 트랜잭션 경계 혼란을 방지한다. " +
                     "타 도메인 접근은 타 도메인의 Domain Service를 직접 호출한다. " +
                     "(service-layer-convention.md § 5)");

    // ========================================================================
    // Controller → Domain Service 직접 호출 금지
    // 출처: service-layer-convention.md § 5
    // "Controller → Domain Service 직접 ❌ Facade 우회"
    // ========================================================================

    @ArchTest
    static final ArchRule Controller는_domain_Service를_직접_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..interfaces..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat(
                DescribedPredicate.describe(
                    "domain Service classes",
                    (JavaClass clazz) -> clazz.getPackageName().contains(".domain.")
                        && clazz.getSimpleName().endsWith("Service")
                ))
            .because("Controller는 Facade만 호출한다. " +
                     "Domain Service 직접 접근은 Facade 우회이다. " +
                     "Filter 등 인프라 클래스는 예외. " +
                     "(service-layer-convention.md § 5)");

    // ========================================================================
    // Domain Service → Facade 역방향 호출 금지
    // 출처: service-layer-convention.md § 5
    // "Domain Service → Facade ❌ 하위 → 상위 역방향"
    // ========================================================================

    @ArchTest
    static final ArchRule Domain_Service는_Facade를_의존하지_않는다 =
        noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Facade")
            .because("Domain Service → Facade 역방향 호출 금지. " +
                     "하위 계층은 상위 계층을 알 수 없다. " +
                     "(service-layer-convention.md § 5)");

    // ========================================================================
    // Facade는 Repository를 직접 접근하지 않는다
    // 출처: service-layer-convention.md § 2
    // "Facade에 넣지 않는 것: Repository 직접 호출 → Domain Service"
    // ========================================================================

    @ArchTest
    static final ArchRule Facade는_Repository를_직접_의존하지_않는다 =
        noClasses()
            .that().haveSimpleNameEndingWith("Facade")
            .and().resideInAPackage("..application..")
            .should().dependOnClassesThat()
            .haveSimpleNameEndingWith("Repository")
            .because("Facade는 Repository를 직접 호출하지 않는다. " +
                     "데이터 접근은 Domain Service를 통해 한다. " +
                     "(service-layer-convention.md § 2)");
}
