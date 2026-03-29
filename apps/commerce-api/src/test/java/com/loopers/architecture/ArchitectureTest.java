package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

@AnalyzeClasses(packages = "com.loopers", importOptions = DoNotIncludeTests.class)
class ArchitectureTest {

    // ========== 계층 의존성 룰 (4개) ==========

    @ArchTest
    static final ArchRule domain_should_not_depend_on_upper_layers =
            noClasses().that().resideInAPackage("com.loopers.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.loopers.application..",
                            "com.loopers.infrastructure..",
                            "com.loopers.interfaces.."
                    )
                    .because("Domain 계층은 application, infrastructure, interfaces에 의존할 수 없습니다 (Domain Purity)");

    @ArchTest
    static final ArchRule application_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("com.loopers.application..")
                    .should().dependOnClassesThat().resideInAPackage("com.loopers.infrastructure..")
                    .because("Application 계층은 infrastructure에 의존할 수 없습니다 (DIP)");

    @ArchTest
    static final ArchRule interfaces_should_not_depend_on_infrastructure =
            noClasses().that().resideInAPackage("com.loopers.interfaces..")
                    .should().dependOnClassesThat().resideInAPackage("com.loopers.infrastructure..")
                    .because("Interfaces 계층은 infrastructure에 의존할 수 없습니다 (DIP)");

    @ArchTest
    static final ArchRule layered_architecture =
            Architectures.layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Interfaces").definedBy("com.loopers.interfaces..")
                    .layer("Application").definedBy("com.loopers.application..")
                    .layer("Domain").definedBy("com.loopers.domain..")
                    .layer("Infrastructure").definedBy("com.loopers.infrastructure..")
                    .layer("Support").definedBy("com.loopers.support..")
                    .whereLayer("Interfaces").mayOnlyBeAccessedByLayers("Infrastructure", "Support")
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces", "Infrastructure", "Support")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Interfaces", "Application", "Infrastructure", "Support")
                    .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Support").mayOnlyBeAccessedByLayers("Interfaces", "Application", "Domain", "Infrastructure")
                    .because("계층 간 의존 방향은 Interfaces→Application→Domain←Infrastructure 이어야 합니다");

    // ========== 코드 품질 룰 (5개) ==========

    @ArchTest
    static final ArchRule no_field_injection =
            noFields().should().beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .because("Field Injection(@Autowired) 금지. 생성자 주입(@RequiredArgsConstructor)만 사용해야 합니다");

    @ArchTest
    static final ArchRule no_cyclic_dependencies =
            SlicesRuleDefinition.slices().matching("com.loopers.(*)..").should().beFreeOfCycles()
                    .because("패키지 간 순환 참조는 허용되지 않습니다");

    @ArchTest
    static final ArchRule no_standard_output =
            noClasses().that().resideInAPackage("com.loopers..")
                    .should().accessClassesThat().haveFullyQualifiedName("java.io.PrintStream")
                    .because("System.out/err, printStackTrace 사용 금지. @Slf4j를 사용해야 합니다");

    @ArchTest
    static final ArchRule app_service_methods_should_be_transactional =
            methods().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("AppService")
                    .and().areDeclaredInClassesThat().resideInAPackage("com.loopers.application..")
                    .and().arePublic()
                    .should(beTransactional())
                    .because("AppService의 모든 public 메서드는 @Transactional이 적용되어야 합니다");

    @ArchTest
    static final ArchRule controller_should_not_return_domain_entities =
            methods().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().areDeclaredInClassesThat().resideInAPackage("com.loopers.interfaces..")
                    .and().arePublic()
                    .should(notReturnDomainEntity())
                    .because("Controller는 도메인 엔티티를 직접 반환할 수 없습니다. ApiResponse<DTO>만 허용됩니다");

    // ========== 네이밍 & 어노테이션 룰 (3개) ==========

    @ArchTest
    static final ArchRule controllers_naming_convention =
            classes().that().haveSimpleNameEndingWith("Controller")
                    .and().areNotAnnotatedWith("org.springframework.web.bind.annotation.RestControllerAdvice")
                    .should().resideInAPackage("com.loopers.interfaces..")
                    .andShould().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .because("*Controller 클래스는 interfaces 패키지에 위치하고 @RestController를 사용해야 합니다");

    @ArchTest
    static final ArchRule app_services_naming_convention =
            classes().that().haveSimpleNameEndingWith("AppService")
                    .should().resideInAPackage("com.loopers.application..")
                    .andShould().beAnnotatedWith("org.springframework.stereotype.Service")
                    .because("*AppService 클래스는 application 패키지에 위치하고 @Service를 사용해야 합니다");

    @ArchTest
    static final ArchRule facades_naming_convention =
            classes().that().haveSimpleNameEndingWith("Facade")
                    .should().resideInAPackage("com.loopers.application..")
                    .andShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .because("*Facade 클래스는 application 패키지에 위치하고 @Component를 사용해야 합니다");

    @ArchTest
    static final ArchRule event_listeners_naming_convention =
            classes().that().haveSimpleNameEndingWith("EventListener")
                    .should().resideInAnyPackage("com.loopers.application..", "com.loopers.infrastructure..")
                    .andShould().beAnnotatedWith("org.springframework.stereotype.Component")
                    .because("*EventListener 클래스는 application 또는 infrastructure 패키지에 위치하고 @Component를 사용해야 합니다");

    @ArchTest
    static final ArchRule event_records_should_reside_in_domain =
            classes().that().haveSimpleNameEndingWith("Event")
                    .and().resideInAPackage("com.loopers..")
                    .should().resideInAPackage("com.loopers.domain.event..")
                    .because("*Event 클래스는 domain.event 패키지에 위치해야 합니다");

    @ArchTest
    static final ArchRule schedulers_should_reside_in_infrastructure =
            classes().that().haveSimpleNameEndingWith("Scheduler")
                    .should().resideInAPackage("com.loopers.infrastructure..")
                    .because("*Scheduler 클래스는 infrastructure 패키지에 위치해야 합니다");

    // ========== 접근 제어 룰 (3개) ==========

    @ArchTest
    static final ArchRule controllers_should_not_access_repositories =
            noClasses().that().resideInAPackage("com.loopers.interfaces..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("Controller에서 Repository 직접 사용 금지. Facade 또는 AppService를 통해 접근해야 합니다");

    @ArchTest
    static final ArchRule facades_should_not_access_repositories =
            noClasses().that().haveSimpleNameEndingWith("Facade")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("Facade에서 Repository 직접 사용 금지. AppService만 사용해야 합니다");

    @ArchTest
    static final ArchRule application_event_listeners_should_not_access_repositories =
            noClasses().that().haveSimpleNameEndingWith("EventListener")
                    .and().resideInAPackage("com.loopers.application..")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("Application EventListener에서 Repository 직접 사용 금지. AppService를 통해 접근해야 합니다");

    @ArchTest
    static final ArchRule outbox_should_be_isolated_to_infrastructure =
            noClasses().that().resideInAnyPackage("com.loopers.application..", "com.loopers.domain..", "com.loopers.interfaces..")
                    .should().dependOnClassesThat().haveSimpleName("Outbox")
                    .because("Outbox 엔티티는 infrastructure 내부에서만 사용 가능합니다");

    @ArchTest
    static final ArchRule kafka_should_be_isolated_to_infrastructure =
            noClasses().that().resideInAnyPackage("com.loopers.application..", "com.loopers.domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework.kafka..")
                    .orShould().dependOnClassesThat().resideInAPackage("org.apache.kafka..")
                    .because("Kafka 의존은 infrastructure 계층에만 허용됩니다 (기술 격리)");

    @ArchTest
    static final ArchRule controller_methods_should_return_api_response =
            methods().that().areDeclaredInClassesThat().haveSimpleNameEndingWith("Controller")
                    .and().areDeclaredInClassesThat().resideInAPackage("com.loopers.interfaces..")
                    .and().arePublic()
                    .and().areDeclaredInClassesThat().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                    .should(returnApiResponse())
                    .because("Controller의 모든 public 메서드는 ApiResponse를 반환해야 합니다");

    // ========== 커스텀 ArchCondition ==========

    private static ArchCondition<JavaMethod> beTransactional() {
        return new ArchCondition<>("be annotated with @Transactional (method or class level)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                boolean methodLevel = method.isAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                        || method.isAnnotatedWith("jakarta.transaction.Transactional");
                boolean classLevel = method.getOwner().isAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                        || method.getOwner().isAnnotatedWith("jakarta.transaction.Transactional");

                if (!methodLevel && !classLevel) {
                    events.add(SimpleConditionEvent.violated(method,
                            String.format("%s.%s() 메서드에 @Transactional이 없습니다",
                                    method.getOwner().getSimpleName(), method.getName())));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notReturnDomainEntity() {
        return new ArchCondition<>("not return domain entity directly") {
            private static final Set<String> ENTITY_ANNOTATIONS = Set.of(
                    "jakarta.persistence.Entity",
                    "javax.persistence.Entity"
            );

            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass returnType = method.getRawReturnType();
                boolean isEntity = ENTITY_ANNOTATIONS.stream()
                        .anyMatch(returnType::isAnnotatedWith);

                if (isEntity) {
                    events.add(SimpleConditionEvent.violated(method,
                            String.format("%s.%s()가 도메인 엔티티 %s를 직접 반환합니다",
                                    method.getOwner().getSimpleName(), method.getName(),
                                    returnType.getSimpleName())));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> returnApiResponse() {
        return new ArchCondition<>("return ApiResponse") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                JavaClass returnType = method.getRawReturnType();
                if (!returnType.getName().equals("com.loopers.interfaces.api.ApiResponse")) {
                    events.add(SimpleConditionEvent.violated(method,
                            String.format("%s.%s()의 반환 타입이 ApiResponse가 아닙니다 (실제: %s)",
                                    method.getOwner().getSimpleName(), method.getName(),
                                    returnType.getSimpleName())));
                }
            }
        };
    }
}
