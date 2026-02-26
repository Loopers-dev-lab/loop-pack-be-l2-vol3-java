# 코드 배치 순서

## 원칙

클래스 내 멤버는 **공개 범위 넓은 것 → 좁은 것**, 역할별로 그룹핑한다.
모든 클래스에서 private 메서드는 최하단에 배치한다.

## 계층별 적용 (ApplicationService, Facade, Controller, Repository 등)

- 구분 주석: `// Command` → `// Query` 순서

| 계층 | 순서 | 비고 |
|------|------|------|
| Controller | `// Command` → `// Query` | POST/PATCH/DELETE → GET |
| ApiSpec | `// Command` → `// Query` | Controller와 동일 순서 |
| Facade | `// Command` → `// Query` | |
| ApplicationService | `// Command` → `// Query` | |
| Repository (interface) | `// Command` → `// Query` | save → find, exists |
| RepositoryImpl | `// Command` → `// Query` | Repository 인터페이스와 동일 순서 |
| JpaRepository | `// Query` 만 | 상속 메서드 생략, 직접 정의한 메서드만 기재 |
| Request | `// Command` → `// Query` | Place, Cancel → ListByUser |
| Command | 구분 주석 없음 | 기능별 나열 |
| V1Dto | `// Response` 만 | Response 전용 |

## Entity 멤버 순서

Entity는 Command/Query 구분 대신 역할별 순서를 따른다.

| 순서 | 역할 | 예시 |
|------|------|------|
| 1 | 상수 | `NAME_MAX_LENGTH` |
| 2 | 필드 | `name`, `description` |
| 3 | 생성자 | protected 기본 생성자 → private 생성자 순 |
| 4 | 정적 팩토리 메서드 | `create()` |
| 5 | 명령 메서드 | `update()`, `softDelete()` |
| 6 | 조회 메서드 | `isDeleted()`, `isOrderable()` |
| 7 | 검증 메서드 (public) | `validateNotDeleted()` |
| 8 | private 메서드 | `validateName()`, `validateDescription()` |

## 주석 형식

- 계층별 구분 주석: `// Command`, `// Query`, `// Response`
- Entity는 구분 주석 불필요 — 순서 자체가 규칙
- 메서드 그룹 사이에 빈 줄 하나