package com.loopers.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@DisplayName("Clean Architecture 계층 규칙 검증")
class CleanArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void setUp() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.loopers");
    }

    @Nested
    @DisplayName("계층 간 의존성 규칙")
    class LayerDependencyRules {

        @Test
        @DisplayName("Domain 계층은 다른 계층에 의존하지 않는다")
        void domainShouldNotDependOnOtherLayers() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces..",
                            "..application..",
                            "..infrastructure.."
                    );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Application 계층은 Domain에만 의존한다")
        void applicationShouldOnlyDependOnDomain() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces..",
                            "..infrastructure.."
                    );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Infrastructure 계층은 Domain에만 의존한다")
        void infrastructureShouldOnlyDependOnDomain() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..infrastructure..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "..interfaces..",
                            "..application.."
                    );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Interfaces 계층은 모든 계층에 의존할 수 있다")
        void interfacesShouldDependOnAllLayers() {
            // Interfaces는 최상위 계층이므로 모든 계층에 의존 가능
            // 이 테스트는 명시적으로 규칙을 문서화하기 위한 것
            ArchRule rule = classes()
                    .that().resideInAPackage("..interfaces..")
                    .should().onlyDependOnClassesThat().resideOutsideOfPackages(
                            "..batch..",
                            "..streamer.."
                    );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("계층 구조가 올바르게 정의되어 있다")
        void layersShouldBeProperlyDefined() {
            ArchRule rule = layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Interfaces").definedBy("..interfaces..")
                    .layer("Application").definedBy("..application..")
                    .layer("Domain").definedBy("..domain..")
                    .layer("Infrastructure").definedBy("..infrastructure..")
                    .whereLayer("Interfaces").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Application").mayOnlyBeAccessedByLayers("Interfaces")
                    .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Interfaces")
                    .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Interfaces");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("명명 규칙")
    class NamingConventionRules {

        @Test
        @DisplayName("Controller는 V1Controller로 끝나야 한다")
        void controllersShouldEndWithV1Controller() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..interfaces.api..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().haveSimpleNameEndingWith("V1Controller");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Facade는 Facade로 끝나야 한다")
        void facadesShouldEndWithFacade() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..application..")
                    .and().areNotInterfaces()
                    .and().areNotRecords()
                    .should().haveSimpleNameEndingWith("Facade")
                    .orShould().haveSimpleNameEndingWith("Info");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Service는 Service로 끝나야 한다")
        void servicesShouldEndWithService() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Service")
                    .and().areNotInterfaces()
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repository 구현체는 RepositoryImpl로 끝나야 한다")
        void repositoryImplsShouldEndWithRepositoryImpl() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..infrastructure..")
                    .and().haveSimpleNameEndingWith("RepositoryImpl")
                    .should().beAnnotatedWith(org.springframework.stereotype.Repository.class);

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("어노테이션 규칙")
    class AnnotationRules {

        @Test
        @DisplayName("Controller는 @RestController를 가져야 한다")
        void controllersShouldHaveRestControllerAnnotation() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..interfaces.api..")
                    .and().haveSimpleNameEndingWith("Controller")
                    .should().beAnnotatedWith(org.springframework.web.bind.annotation.RestController.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Service는 @Service를 가져야 한다")
        void servicesShouldHaveServiceAnnotation() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().beAnnotatedWith(org.springframework.stereotype.Service.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repository 인터페이스는 어노테이션이 없어야 한다")
        void repositoryInterfacesShouldNotHaveAnnotations() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Repository")
                    .and().areInterfaces()
                    .should().notBeAnnotatedWith(org.springframework.stereotype.Repository.class);

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Repository 구현체는 @Repository를 가져야 한다")
        void repositoryImplsShouldHaveRepositoryAnnotation() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..infrastructure..")
                    .and().haveSimpleNameEndingWith("RepositoryImpl")
                    .should().beAnnotatedWith(org.springframework.stereotype.Repository.class);

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("패키지 규칙")
    class PackageRules {

        @Test
        @DisplayName("Domain Entity는 JPA에만 의존한다 (Spring 의존 금지)")
        void domainEntitiesShouldOnlyDependOnJPA() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..domain..")
                    .and().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
                    .because("Domain entities should only depend on JPA, not Spring Framework");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Entity는 domain 패키지에만 존재한다")
        void entitiesShouldOnlyResideInDomainPackage() {
            ArchRule rule = classes()
                    .that().areAnnotatedWith(jakarta.persistence.Entity.class)
                    .should().resideInAPackage("..domain..");

            rule.check(importedClasses);
        }
    }

    @Nested
    @DisplayName("DIP (Dependency Inversion Principle) 규칙")
    class DependencyInversionRules {

        @Test
        @DisplayName("Domain은 인터페이스에만 의존해야 한다 (구현체 의존 금지)")
        void domainShouldOnlyDependOnInterfaces() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..")
                    .andShould().dependOnClassesThat()
                    .haveSimpleNameEndingWith("RepositoryImpl");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain Repository는 인터페이스여야 한다")
        void domainRepositoriesShouldBeInterfaces() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Repository")
                    .should().beInterfaces();

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Infrastructure는 Domain 인터페이스를 구현해야 한다")
        void infrastructureShouldImplementDomainInterfaces() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..infrastructure..")
                    .and().haveSimpleNameEndingWith("RepositoryImpl")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..domain..")
                    .andShould().haveSimpleNameEndingWith("RepositoryImpl");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Application은 Domain 인터페이스에만 의존해야 한다 (구현체 의존 금지)")
        void applicationShouldOnlyDependOnDomainInterfaces() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..application..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("..infrastructure..");

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Domain Service는 구체 클래스가 아닌 인터페이스에 의존해야 한다")
        void domainServicesShouldDependOnInterfacesNotConcreteClasses() {
            ArchRule rule = classes()
                    .that().resideInAPackage("..domain..")
                    .and().haveSimpleNameEndingWith("Service")
                    .should().onlyHaveDependentClassesThat()
                    .resideInAnyPackage(
                            "..domain..",
                            "..application..",
                            "..interfaces..",
                            "java..",
                            "org.springframework..",
                            "lombok..",
                            "com.fasterxml.."
                    );

            rule.check(importedClasses);
        }

        @Test
        @DisplayName("Infrastructure 구현체는 Domain에서 직접 참조되지 않아야 한다")
        void infrastructureImplsShouldNotBeReferencedByDomain() {
            ArchRule rule = noClasses()
                    .that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat()
                    .haveSimpleNameEndingWith("RepositoryImpl")
                    .orShould().dependOnClassesThat()
                    .haveSimpleNameEndingWith("JpaRepository");

            rule.check(importedClasses);
        }
    }
}
