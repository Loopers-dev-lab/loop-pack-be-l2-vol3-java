# Loop Pack BE - Java Spring Template

## 프로젝트 개요
- **프로젝트명**: loopers-java-spring-template
- **그룹**: com.loopers
- **빌드 도구**: Gradle (Kotlin DSL)
- **버전 관리**: Git Hash 기반

## 기술 스택

### 코어 프레임워크
- **Java**: 21 (Toolchain)
- **Kotlin**: 2.0.20
- **Spring Boot**: 3.4.4
- **Spring Cloud**: 2024.0.1
- **Spring Dependency Management**: 1.1.7

### 주요 라이브러리
- **ORM/Database**
  - Spring Data JPA
  - QueryDSL (Jakarta)
  - MySQL Connector

- **캐싱**
  - Spring Data Redis

- **메시징**
  - Spring Kafka

- **웹/API**
  - Spring Web
  - Spring Actuator
  - SpringDoc OpenAPI: 2.7.0

- **직렬화**
  - Jackson (JSR310 지원)

- **유틸리티**
  - Lombok

### 테스트 프레임워크
- **테스트 도구**
  - JUnit 5 (Jupiter)
  - Spring Boot Test
  - SpringMockK: 4.0.2
  - Mockito: 5.14.0
  - Instancio JUnit: 5.0.2

- **컨테이너 테스트**
  - Testcontainers (MySQL, Redis, Kafka)

- **코드 커버리지**
  - JaCoCo (XML 리포트)

### 코드 품질
- **ktLint**: 1.0.1 (Plugin: 12.1.2)

### 모니터링
- **Slack Appender**: 1.6.1

## 모듈 구조

### Applications (`apps/`)
실행 가능한 애플리케이션 모듈들 (BootJar 활성화)

1. **commerce-api**
   - 커머스 REST API 서버
   - 의존 모듈: jpa, redis, jackson, logging, monitoring
   - Spring Web, Actuator, OpenAPI 포함

2. **commerce-streamer**
   - 실시간 데이터 스트리밍 처리

3. **commerce-batch**
   - 배치 작업 처리

### Modules (`modules/`)
핵심 인프라 모듈들 (Library)

1. **jpa**
   - JPA 및 QueryDSL 설정
   - MySQL 연동
   - Test Fixtures 제공

2. **redis**
   - Redis 캐싱 설정
   - Test Fixtures 제공

3. **kafka**
   - Kafka 메시징 설정
   - Test Fixtures 제공

### Supports (`supports/`)
공통 지원 모듈들 (Library)

1. **jackson**
   - JSON 직렬화 설정

2. **logging**
   - 로깅 설정 및 Slack 통합

3. **monitoring**
   - 모니터링 및 메트릭 수집

## 빌드 설정

### Gradle 태스크
- **Test**: 단일 병렬 실행, JUnit Platform 사용
  - Timezone: Asia/Seoul
  - Profile: test
  - JVM Args: -Xshare:off

- **JaCoCo**: XML 리포트 생성 (HTML/CSV 비활성화)

### 패키징
- Applications: BootJar (실행 가능한 JAR)
- Modules/Supports: Jar (라이브러리)

## 프로젝트 특징
- 멀티 모듈 아키텍처
- Test Fixtures를 통한 테스트 지원
- Testcontainers 기반 통합 테스트
- QueryDSL을 통한 타입 안전 쿼리
- Spring Cloud 기반 확장 가능한 구조

## SKILLS

작업 시작 전 관련 스킬 파일을 반드시 읽고 따를 것:

### 요구사항 분석
- 파일: `.claude/skills/requirements-analysis/SKILL.md`
- 요구사항 분석, 설계 문서 작성 요청 시 해당 파일을 Read 도구로 읽고 지침을 따를 것
- 시퀀스 다이어그램, 클래스 다이어그램, ERD 작성 시 Mermaid 문법 사용

### 조회 API 설계 리뷰
- 파일: `.claude/skills/read-optimization-review/SKILL.md`
- 조회 API 설계 검토, 캐시 전략 리뷰, 읽기 최적화 리뷰 요청 시 해당 파일을 Read 도구로 읽고 지침을 따를 것
- 구조적 리스크와 trade-off 관점에서만 분석하며, 구현 코드나 설정 값은 제안하지 않음


## 도메인 & 객체 설계 전략
- 도메인 객체는 비즈니스 규칙을 캡슐화해야 합니다.
- 애플리케이션 서비스는 서로 다른 도메인을 조립해, 도메인 로직을 조정하여 기능을 제공해야 한다.
- 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행해야 한다.

## 아키텍처, 패키지 구성 전략
- 본 프로젝트는 레이어드 아키텍처를 따르며, DIP (의존성 역전 원칙) 을 준수한다.
- API request, response DTO와 응용 레이어의 DTO는 분리해 작성하도록 한다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징하는 형태로 작성한다.
    - 예시
      > /interfaces/api (presentation 레이어 - API)
      /application/.. (application 레이어 - 도메인 레이어를 조합해 사용 가능한 기능을 제공)
      /domain/.. (domain 레이어 - 도메인 객체 및 엔티티, Repository 인터페이스가 위치)
      /infrastructure/.. (infrastructure 레이어 - JPA, Redis 등을 활용해 Repository 구현체를 제공)