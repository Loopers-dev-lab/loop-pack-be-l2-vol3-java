# CLAUDE.md

## 프로젝트 개요

**loopers-java-spring-template**은 DDD + Hexagonal Architecture 기반 멀티모듈 Spring Boot 프로젝트 템플릿이다.

## 프로젝트 구조
```
├── apps/
│   ├── commerce-api         # REST API (port 8080)
│   ├── commerce-batch       # 배치 처리
│   └── commerce-streamer    # Kafka 스트림 처리
├── modules/
│   ├── jpa                  # JPA/Hibernate 설정
│   ├── redis                # Redis 설정
│   └── kafka                # Kafka 설정
├── supports/
│   ├── jackson              # JSON 직렬화
│   ├── logging              # 로깅 설정
│   └── monitoring           # Actuator/Prometheus 메트릭
└── docs/
    ├── requirements/        # 요구사항 정의서 (Epic 단위)
    ├── specs/               # 기능 명세서 (Feature 단위)
    └── design/              # 설계 문서 (다이어그램)

```

## Rules 구조

- rules 파일의 "참고 스킬" 섹션에 명시된 스킬은 해당 규칙 적용 시 SKILL.md를 읽고 따를 것

```
.claude/rules/
├── core/           # Claude 행동 규칙 (프로젝트 무관, 전역 승격 후보)
├── project/        # 이 프로젝트의 팩트 (스택, 구조, 명령어, 환경)
└── conventions/    # 이 프로젝트의 코딩 정책 (설계 원칙, 테스트 전략)
```
