# 프로젝트 컨텍스트

## 프로젝트 개요
**Loopers Template (Spring + Java)** - 엔터프라이즈급 Spring Boot 멀티 모듈 프로젝트 템플릿

### 핵심 특징
- **멀티 모듈 아키텍처**: apps, modules, supports 계층 구조
- **도메인 주도 설계**: Value Object 패턴, 레이어드 아키텍처
- **테스트 주도 개발**: 단위/통합/E2E 테스트 전략
- **프로덕션 레디**: 모니터링, 로깅, 보안 설정 완비

## 기술 스택

### Core
- **Java**: 21
- **Spring Boot**: 3.4.4
- **Spring Cloud**: 2024.0.1
- **Gradle**: Kotlin DSL

### Framework & Libraries
- **Spring Data JPA**: QueryDSL 지원
- **Spring Security**: BCrypt 암호화
- **Spring Batch**: 배치 작업 처리
- **Spring Kafka**: 이벤트 스트리밍
- **Redis**: 캐싱 및 세션 관리
- **MySQL**: 주 데이터베이스

### API & Documentation
- **SpringDoc OpenAPI**: 2.7.0 (Swagger UI)
- **Jakarta Validation**: 요청 검증

### Testing
- **JUnit 5**: 테스트 프레임워크
- **AssertJ**: 유창한 assertion
- **Mockito**: 5.14.0 (모킹)
- **SpringMockK**: 4.0.2 (Kotlin 모킹)
- **Instancio**: 5.0.2 (테스트 데이터 생성)
- **TestContainers**: 통합 테스트 환경

### Monitoring & Logging
- **Spring Actuator**: 헬스체크 및 메트릭
- **Prometheus**: 메트릭 수집
- **Grafana**: 시각화 대시보드
- **Logback**: 로깅 프레임워크
- **Slack Appender**: 알림 연동

### Build & Quality
- **Jacoco**: 코드 커버리지
- **Lombok**: 보일러플레이트 제거

## 모듈 구조

```
Root
├── apps (실행 가능한 Spring Boot 애플리케이션)
│   ├── commerce-api       # REST API 서버
│   ├── commerce-batch     # 배치 작업 처리
│   └── commerce-streamer  # Kafka 이벤트 처리
├── modules (재사용 가능한 인프라 설정)
│   ├── jpa                # JPA, QueryDSL 설정
│   ├── redis              # Redis 설정
│   └── kafka              # Kafka 설정
└── supports (부가 기능 모듈)
    ├── jackson            # JSON 직렬화 설정
    ├── logging            # 로깅 설정
    └── monitoring         # 모니터링 설정
```

### 모듈 의존성 규칙
1. **apps** → **modules**, **supports** 의존 가능
2. **modules** → 다른 **modules** 의존 불가 (독립성 유지)
3. **supports** → 다른 모듈 의존 불가 (순수 부가 기능)
4. 순환 참조 금지

## 환경 설정

### 프로파일
- `local`: 로컬 개발 환경
- `test`: 테스트 환경 (TestContainers)
- `dev`: 개발 서버
- `qa`: QA 서버
- `prd`: 프로덕션 서버

### 인프라 실행
```bash
# MySQL, Redis 실행
docker-compose -f ./docker/infra-compose.yml up

# Prometheus, Grafana 실행
docker-compose -f ./docker/monitoring-compose.yml up
```

### 접속 정보
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Grafana**: http://localhost:3000 (admin/admin)
- **Prometheus**: http://localhost:9090

## 빌드 및 실행

### 빌드
```bash
# 전체 빌드
./gradlew clean build

# 특정 모듈 빌드
./gradlew :apps:commerce-api:build

# 테스트 제외 빌드
./gradlew clean build -x test
```

### 실행
```bash
# commerce-api 실행
./gradlew :apps:commerce-api:bootRun

# commerce-batch 실행
./gradlew :apps:commerce-batch:bootRun

# commerce-streamer 실행
./gradlew :apps:commerce-streamer:bootRun
```

### 테스트
```bash
# 전체 테스트
./gradlew test

# 특정 모듈 테스트
./gradlew :apps:commerce-api:test

# 커버리지 리포트 생성
./gradlew test jacocoTestReport
```

## 주요 도메인

### Member (회원)
- **Value Objects**: MemberId, Email, BirthDate, Name, Gender
- **비즈니스 규칙**:
  - MemberId: 영문+숫자, 1~10자
  - Email: RFC 5322 표준 형식
  - BirthDate: yyyy-MM-dd, 과거 날짜, 130년 이내
  - Password: 8~16자, 영문 대소문자+숫자+특수문자, 생년월일 불포함
  - Gender: 필수 입력
- **API 엔드포인트**:
  - `POST /api/v1/members/register`: 회원 가입
  - `GET /api/v1/members/me`: 내 정보 조회 (X-Loopers-LoginId, X-Loopers-LoginPw 헤더 필요)
  - `PATCH /api/v1/members/me/password`: 비밀번호 수정 (X-Loopers-LoginId, X-Loopers-LoginPw 헤더 필요)

## 참고 문서
- **CLAUDE.md**: 전체 개발 가이드
- **.claude/skills/**: 세부 스킬별 가이드라인
- **README.md**: 프로젝트 소개 및 시작 가이드
