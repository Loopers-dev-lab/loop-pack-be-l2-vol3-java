# CLAUDE.md — Loop Pack Week 1

## 0. 프로젝트 개요
이 저장소는 멀티 모듈 Gradle 기반의 이커머스 백엔드 학습 프로젝트입니다.
Week 1의 목표는 개발 환경을 세팅하고, 사용자 도메인의 핵심 기능을 구현하며
단위 테스트 / 통합 테스트 / E2E 테스트를 통해 기능의 정확성과 설계 의도를 검증하는 것입니다.

> 브랜치 정책
> - 모든 작업은 `week-1` 브랜치에서 진행합니다.
> - 제출 시 `main` 브랜치로 병합하지 않습니다.

---

## 1. 기술 스택
- Language: Java
- Build Tool: Gradle (Kotlin DSL)
- Framework: Spring Boot
- Test: JUnit 5, Spring Test
- Infra: MySQL, Redis, TestContainers
- API 샘플: /http

---

## 2. 프로젝트 모듈 구조
- apps/commerce-api: Spring Boot 애플리케이션 및 REST API
- modules/jpa: JPA/DB 관련 모듈 (+ testFixtures)
- modules/redis: Redis 관련 모듈 (+ testFixtures)
- supports/*: 공통 유틸/서포트 모듈
- docker/, loopers-docker/: 로컬 인프라

---

## 3. 기능 요구사항

### 3.1 회원가입
- 입력: 로그인 ID, 비밀번호, 이름, 생년월일, 이메일
- 제약:
  - 로그인 ID 중복 불가
  - 로그인 ID는 영문/숫자만 허용
  - 이름/이메일/생년월일 포맷 검증
  - 비밀번호 암호화 저장
  - 비밀번호 규칙: 8~16자, 영문 대/소문자/숫자/특수문자만, 생년월일 포함 불가

### 3.2 내 정보 조회
- 인증 헤더:
  - X-Loopers-LoginId
  - X-Loopers-LoginPw
- 반환: 로그인 ID, 이름(마스킹), 생년월일, 이메일
- 이름 마스킹: 마지막 글자를 * 로 치환 (예: 민주 → 민*)

### 3.3 비밀번호 수정
- 입력: 기존 비밀번호, 새 비밀번호
- 제약:
  - 기존 비밀번호 검증
  - 새 비밀번호는 기존과 동일 불가
  - 비밀번호 규칙 동일 적용

---

## 4. 개발 규칙
- AI는 제안만, 최종 결정은 개발자가 수행
- 요청하지 않은 기능 추가/테스트 삭제/요구사항 임의 해석 금지
- TDD(3A): Arrange → Act → Assert / Red → Green → Refactor

---

## 5. 테스트 정책
- 단위 테스트: 도메인/검증 로직 (Spring 없이)
- 통합 테스트: Service/Repository 흐름 (Spring 사용 가능)
- E2E 테스트: HTTP 시나리오 (Controller→Service→DB)

---

## 6. 금지 사항
- println/임시 로그 커밋 금지
- 하드코딩으로 검증 우회 금지
- null-safety 무시 금지

---

## 7. 로컬 실행
- ./gradlew test
- ./gradlew check
