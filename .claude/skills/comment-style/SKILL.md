---
name: comment-style
description: >
  Java 소스 코드 주석 작성 및 리뷰 시 사용하는 스킬.
  프로젝트 전용 주석 컨벤션을 적용한다: 비즈니스 설명은 한국어, 구조적 마커는 영어, 번호 매칭 메서드, 레이어별 패턴.
---

# Comment Style Guide

실제 코드베이스 분석에서 도출한 프로젝트 주석 컨벤션. 헥사고날 아키텍처 전 레이어에 일관된 주석 스타일을 보장한다.

## When to Activate

- 새 Java 클래스 작성 (도메인 모델, 서비스, 컨트롤러 등)
- 기존 코드의 주석 추가·수정
- 코드 리뷰 시 주석 스타일 일관성 확인
- 테스트 클래스, 픽스처, 이벤트 핸들러 작성
- 의존성 주입 필드 정리

---

## Core Rules

### 1. Language Rules

- **비즈니스 로직·설명**: 항상 한국어 / **구조적 마커**: 항상 영어 소문자

```java
// ❌ WRONG                          // ✅ CORRECT
/** Trade management service */      /** 거래 관리 서비스 */
// 서비스                             // service
```

### 2. Comment Style Selection

| Style            | Use For                                |
|------------------|----------------------------------------|
| `/** */` Javadoc | 클래스 문서, 복잡한 메서드/필드 설명, 테스트 픽스처 팩토리     |
| `//` Single-line | 인라인 설명, 구조적 마커, 번호 매칭, given/when/then |
| `/* */` Block    | **사용 금지**                              |

### 3. No Over-Commenting

자명한 코드에 주석 금지: 단순 getter/setter, 이름으로 설명되는 메서드, 보일러플레이트, 필드명이 용도를 설명하는 경우.

### 4. Class Javadoc Placement

클래스 Javadoc은 **클래스 본문 내부**, 의존성 필드 뒤, 첫 메서드 앞에 배치.

```java

@Service
@RequiredArgsConstructor
public class TradeManagementService {

	// repository
	private final TradeCommandRepository tradeCommandRepository;


	/**
	 * 거래 관리 서비스
	 * 1. 거래 생성
	 */

	// 1. 거래 생성
	public void createTrade(...) { ...}

}
```

**Note:** 의존성 필드가 없는 도메인 모델은 클래스 선언 위에 Javadoc 배치.

### 5. Numbered Method Matching

클래스 Javadoc 번호 목록 → 각 메서드에 `// N.` 주석으로 매칭:

```java
/**
 * 유저 관리 서비스
 * 1. 유저 생성
 * 2. 유저 수정
 */

// 1. 유저 생성
public void createUser(...) { ...}


// 2. 유저 수정
public void updateUser(...) { ...}
```

### 6. Domain Model Field Listing

Javadoc에 `- fieldName: 한국어 설명` 형식으로 필드 나열. 자명한 경우 `- 거래번호` 형식도 허용.

### 7. Inline Comments for Multi-Line Methods

- **1줄 메서드**: `// N.` 주석만으로 충분
- **2줄 이상 메서드**: 각 논리적 단계마다 `// 한국어 설명` 인라인 주석 필수

> 인라인 주석 상세 예제 (Sequential/Conditional): [references/detail.md](references/detail.md)

---

## Layer Summary

| 레이어             | 클래스 주석             | 메서드 주석                  | 의존성 주입                          |
|-----------------|--------------------|-------------------------|---------------------------------|
| Domain Model    | Javadoc 필드 목록      | Javadoc 로직 목록 + `// N.` | -                               |
| Service/Facade  | Javadoc 메서드 목록     | `// N.` 매칭              | `// service`, `// repository` 등 |
| Controller      | Javadoc API 목록     | `// N.` 매칭              | `// service`, `// util` 등       |
| Repository Impl | 없음/간략              | `//` 간단 설명              | `// jpa`, `// util` 등           |
| Event Handler   | Javadoc 이벤트 설명     | `// N.` 매칭              | -                               |
| Test            | `@DisplayName` 한국어 | `// given/when/then`    | -                               |
| Fixture         | `//` 상수 그룹핑        | Javadoc 팩토리 설명          | -                               |

> 레이어별 전체 코드 예제: [references/detail.md](references/detail.md)

---

## Structural Markers

| Marker          | 용도       | Marker                                   | 용도        |
|-----------------|----------|------------------------------------------|-----------|
| `// service`    | 서비스 클래스  | `// event`                               | 이벤트 퍼블리셔  |
| `// repository` | 리포지토리    | `// jpa`                                 | JPA 리포지토리 |
| `// port`       | 포트 인터페이스 | `// facade`                              | 퍼사드       |
| `// util`       | 유틸리티     | `// field` / `// converter` / `// value` | 기타        |

---

## Output Contract & Expectations

**MUST — 새 파일 작성 시:**

1. **클래스 Javadoc** — 필드 목록(도메인) 또는 메서드 목록(서비스/컨트롤러)
2. **구조적 마커** — 의존성 주입 필드 그룹핑 (해당 시)
3. **`// N.` 번호 주석** — 클래스 Javadoc 목록과 매칭
4. **한국어** — 모든 비즈니스 설명 / **영어** — 모든 구조적 마커
5. 테스트: `// given` / `// when` / `// then` + `@DisplayName` 한국어
6. 2줄 이상 메서드에 논리적 단계별 인라인 주석

**코드 작성 전:** 아키텍처 레이어 확인 + 인접 파일 주석 스타일 확인
**코드 수정 시:** 메서드 추가·삭제 → Javadoc + 번호 주석 동기화, 기존 스타일 보존

**NEVER:**

- 비즈니스 주석을 영어로 작성
- `/* */` 블록 주석 사용
- 자명한 코드에 주석 추가
- 번호 주석과 Javadoc 목록 불일치 방치

---

## Summary Checklist

- [ ] **Language**: 비즈니스=한국어, 마커=영어
- [ ] **Class-Level**: Javadoc 존재 + 클래스 본문 내부 배치 + 도메인 필드 목록 or 서비스 메서드 목록
- [ ] **Method-Level**: `// N.` 번호 매칭 + 2줄 이상이면 인라인 주석
- [ ] **Dependencies**: 구조적 마커 그룹핑 (영어 소문자)
- [ ] **Tests**: `@DisplayName` 한국어 + `// given/when/then` + 픽스처 Javadoc

> 안티패턴 및 상세 예시: [references/detail.md](references/detail.md)
