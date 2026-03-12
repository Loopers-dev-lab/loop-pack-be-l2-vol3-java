# Code Scanner Agent

프로젝트 코드를 스캔하여 설계 맵(design-map)을 생성한다.
면접 모드에서 프로젝트 맥락 기반 질문을 할 수 있도록 사전 분석을 수행한다.

## 필수 참조 문서

코드 스캔 전에 아래 문서들을 **반드시 먼저 Read**하여 프로젝트의 설계 의도와 컨벤션을 이해한다.

### 설계 문서 (`docs/design/`)
- `docs/design/_shared/OVERVIEW.md` — 전체 ERD + 클래스 다이어그램
- `docs/design/_shared/CONVENTIONS.md` — 참조 방식, Soft Delete, 용어집
- `docs/design/brand/DESIGN.md` — 브랜드 도메인 요구사항 + 유즈케이스 + ERD
- `docs/design/product/DESIGN.md` — 상품 도메인
- `docs/design/like/DESIGN.md` — 좋아요 도메인
- `docs/design/cart/DESIGN.md` — 장바구니 도메인
- `docs/design/order/DESIGN.md` — 주문 도메인
- `docs/design/도메인_관계_설계_의사결정_기록_v3.md` — 도메인 간 관계 설계 의사결정 기록

### 프로젝트 컨벤션 (`.claude/skills/project-convention/`)
- `references/common/package-convention.md` — 패키지 구조 규칙
- `references/common/dto-convention.md` — DTO 설계 규칙
- `references/common/exception-convention.md` — 예외 처리 전략
- `references/domain/entity-vo-convention.md` — Entity/VO 설계 규칙
- `references/application/service-layer-convention.md` — 서비스 레이어 규칙
- `references/infrastructure/infrastructure-convention.md` — 인프라 레이어 규칙
- `references/interfaces/api-convention.md` — API 설계 규칙

이 문서들의 내용은 design-map의 **"설계 포인트"** 추출에 활용한다.
코드가 컨벤션과 설계 문서에 기술된 의도대로 구현되었는지도 사실 기반으로 기록한다.

## 입력

- 프로젝트 루트 경로 (기본: 현재 작업 디렉토리)

## 출력

- `.interview-state/design-map.md`

## 프로세스

### Step 1: 프로젝트 구조 파악

1. 프로젝트 루트에서 디렉토리 트리를 생성한다 (3레벨 깊이)
2. `apps/`, `modules/`, `supports/` 구조를 파악한다
3. `build.gradle` 또는 `pom.xml`에서 의존성과 모듈 관계를 확인한다

### Step 2: 레이어별 클래스 분류

각 도메인 모듈에서 아래 레이어별 클래스를 식별한다:

```
Interface 레이어:
  - *Controller.java → API 엔드포인트 목록
  - *Request.java, *Response.java → DTO 목록

Application 레이어:
  - *Facade.java → 파사드 목록 + 의존하는 서비스 목록
  - *UseCase.java → 유스케이스 목록 (있다면)

Domain 레이어:
  - *Service.java → 도메인 서비스 목록 + 의존 관계
  - 엔티티 클래스 → 필드, 연관관계 (@OneToMany 등)
  - *VO.java, @Embeddable → 값 객체 목록
  - *Repository.java (인터페이스) → 리포지토리 목록

Infrastructure 레이어:
  - *RepositoryImpl.java → 구현체 (QueryDSL 사용 여부)
  - *Producer.java, *Consumer.java → Kafka 사용처
  - Redis 관련 클래스 → 캐시/락 사용처
```

### Step 3: 도메인 간 참조 관계 분석

1. 각 도메인 패키지의 import문을 분석한다
2. 도메인 A → 도메인 B 참조 방향을 파악한다
3. ID 참조 vs 객체 참조를 구분한다
4. 순환 참조가 있는지 확인한다

### Step 4: 설계 포인트 추출

코드에서 면접 질문 소재가 될 수 있는 포인트를 추출한다:

- 파사드가 여러 서비스를 조합하는 지점
- @Transactional 범위와 전파 속성
- 도메인 간 경계를 넘는 호출
- VO로 감싼 원시값 목록
- 복잡한 쿼리 (QueryDSL 사용처)
- 이벤트 발행/구독 지점
- 예외 처리 전략

### Step 5: design-map.md 생성

아래 포맷으로 `.interview-state/design-map.md`에 저장한다:

```markdown
# Design Map

생성일: [날짜]
프로젝트 루트: [경로]

## 모듈 구조
[apps/, modules/, supports/ 트리]

## 도메인 목록
| 도메인 | 패키지 경로 | 주요 엔티티 | 파사드 | 서비스 |
|--------|-----------|------------|--------|--------|
| ...    | ...       | ...        | ...    | ...    |

## 레이어별 클래스 맵

### [도메인명]
- Interface: [컨트롤러, DTO]
- Application: [파사드] → 의존: [서비스 목록]
- Domain: [서비스, 엔티티, VO]
- Infrastructure: [구현체, 외부 연동]

## 도메인 간 참조 관계
[도메인A] → [도메인B]: [참조 방식 (ID/객체)] / [어떤 클래스에서]
...

## 설계 포인트 (면접 소재)
1. [포인트]: [위치] - [간단 설명]
2. ...

## 기술 스택 사용 맵
- Kafka: [Producer/Consumer 위치]
- Redis: [사용처와 용도]
- QueryDSL: [사용하는 Repository]
```

## 주의사항

- 코드의 좋고 나쁨을 판단하지 않는다 (그건 면접 모드에서 할 일)
- 가능한 한 객관적 사실만 기록한다
- 너무 세부적인 코드까지 기록하지 않는다 (클래스/메서드 수준까지만)
- 분석 중 발견한 패턴이나 특이사항은 "설계 포인트"에 기록한다
