package com.loopers;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.loopers", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // ── 1. 계층형 아키텍처 의존성 검증 ──────────────────────────────────────────
    // Interfaces → Application → Domain ← Infrastructure
    // Interfaces → Domain 허용: Controller가 User, PageResult, ProductSortType 등 도메인 타입을 직접 참조
    // Config, Support 패키지는 레이어 외부이므로 검사 대상에서 제외
    @ArchTest
    static final ArchRule layered_architecture_is_respected = layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.loopers..")
            .layer("Interfaces").definedBy("..interfaces..")
            .layer("Application").definedBy("..application..")
            .layer("Domain").definedBy("..domain..")
            .layer("Infrastructure").definedBy("..infrastructure..")
            .layer("Config").definedBy("..config..")

            .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces", "Config")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Config").mayNotBeAccessedByAnyLayer();

    // ── 2. Domain 계층 독립성 (DIP 핵심) ────────────────────────────────────────
    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_application = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..application..");

    @ArchTest
    static final ArchRule domain_should_not_depend_on_interfaces = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..interfaces..");

    // ── 3. 클래스 배치 규칙 (DIP) ───────────────────────────────────────────────
    // Repository 인터페이스는 Domain 패키지에 위치한다 (JpaRepository 제외)
    @ArchTest
    static final ArchRule repository_interfaces_should_be_in_domain = classes()
            .that().haveSimpleNameEndingWith("Repository")
            .and().haveSimpleNameNotContaining("Jpa")
            .and().areInterfaces()
            .should().resideInAPackage("..domain..");

    // Repository 구현체는 Infrastructure 패키지에 위치한다
    @ArchTest
    static final ArchRule repository_implementations_should_be_in_infrastructure = classes()
            .that().haveSimpleNameEndingWith("RepositoryImpl")
            .should().resideInAPackage("..infrastructure..");

    // DomainService는 Domain 패키지에 위치한다
    @ArchTest
    static final ArchRule domain_services_should_be_in_domain = classes()
            .that().haveSimpleNameEndingWith("DomainService")
            .should().resideInAPackage("..domain..");

    // ApplicationService는 Application 패키지에 위치한다
    @ArchTest
    static final ArchRule application_services_should_be_in_application = classes()
            .that().haveSimpleNameEndingWith("ApplicationService")
            .should().resideInAPackage("..application..");

    // Controller는 Interfaces 패키지에 위치한다
    @ArchTest
    static final ArchRule controllers_should_be_in_interfaces = classes()
            .that().haveSimpleNameEndingWith("Controller")
            .should().resideInAPackage("..interfaces..");

    // ── 4. Domain 순수성 ────────────────────────────────────────────────────────
    // Domain은 Spring Web 기술에 의존하지 않는다
    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_web = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..springframework.web..");

    // Domain에 @Service, @Component, @Repository를 사용하지 않는다 (DomainServiceConfig에서 @Bean 등록)
    @ArchTest
    static final ArchRule domain_should_not_use_spring_stereotype_annotations = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(Service.class)
            .orShould().beAnnotatedWith(Component.class)
            .orShould().beAnnotatedWith(Repository.class);

    // Domain에서 @Transactional을 사용하지 않는다 (트랜잭션은 ApplicationService 책임)
    @ArchTest
    static final ArchRule domain_should_not_use_transactional = noClasses()
            .that().resideInAPackage("..domain..")
            .should().beAnnotatedWith(Transactional.class);
}
