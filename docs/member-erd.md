# Member ERD

## 회원 테이블 설계

```mermaid
erDiagram
    MEMBER {
        BIGINT id PK "AUTO_INCREMENT"
        VARCHAR(30) login_id UK "NOT NULL, 로그인 ID"
        VARCHAR(255) password "NOT NULL, 암호화 저장"
        VARCHAR(50) name "NOT NULL, 이름"
        DATE birthday "NOT NULL, 생년월일"
        VARCHAR(100) email UK "NOT NULL, 이메일"
        DATETIME created_at "NOT NULL, 생성일시"
        DATETIME updated_at "NOT NULL, 수정일시"
        DATETIME deleted_at "NULLABLE, 삭제일시"
    }
```

## 제약조건

| 제약조건 | 대상 컬럼 | 설명 |
|----------|-----------|------|
| PRIMARY KEY | `id` | AUTO_INCREMENT |
| UNIQUE | `login_id` | 로그인 ID 중복 방지 |
| UNIQUE | `email` | 이메일 중복 방지 |
| NOT NULL | `login_id`, `password`, `name`, `birthday`, `email` | 필수 입력 |

## 비고

- `password`는 BCrypt 등으로 암호화하여 저장 (VARCHAR 255)
- `birthday`는 `LocalDate` 매핑 (시분초 불필요)
- `created_at`, `updated_at`, `deleted_at`은 `BaseEntity`에서 자동 관리
- `deleted_at`을 통한 소프트 삭제 지원
