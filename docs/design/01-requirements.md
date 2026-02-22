# 요구사항 명세서

## 1. 문제 상황 정의

### 1.1 사용자 관점
- 브랜드별로 구성된 상품을 탐색하고 싶다
- 마음에 드는 상품을 "좋아요"로 표시하고 나중에 다시 찾고 싶다
- 원하는 상품을 장바구니에 담아 주문하고 싶다
- 상품을 최신순, 가격순, 인기순으로 정렬하여 탐색하고 싶다

### 1.2 비즈니스 관점
- **브랜드-상품 계층 구조**를 명확히 분리하여 관리 효율성 확보
- **일반 사용자와 관리자**를 분리하여 권한 기반 접근 제어
- 사용자의 선호도(좋아요)를 추적하여 향후 추천/마케팅 활용
- 주문 내역 추적 및 재고 관리
- 주문 시점의 상품 정보를 보존하여 법적 증빙 및 정산 기능 제공

### 1.3 시스템 관점
- **인증/인가 분리**: LDAP 기반 어드민, 자체 로그인 기반 일반 유저
- **읽기 중심 워크로드**: 상품/브랜드 조회가 주요 트래픽
- **쓰기 작업**: 주문 생성, 좋아요 등록/취소
- **정렬/페이징** 성능 최적화 필요
- 재고 차감의 트랜잭션 일관성 보장

---

## 2. 핵심 액터

| 액터 | 역할 | 인증 방식 |
|------|------|-----------|
| **일반 사용자** | 상품 조회, 좋아요, 주문 | 자체 로그인 (X-Loopers-LoginId, X-Loopers-LoginPw) |
| **관리자** | 브랜드/상품/주문 CRUD 관리 | LDAP (X-Loopers-Ldap: loopers.admin) |

---

## 3. 핵심 도메인

### 3.1 User (유저)
- 회원가입, 로그인, 내 정보 조회, 비밀번호 변경
- 이미 구현 완료

### 3.2 Brand (브랜드)
- 브랜드는 여러 상품을 포함
- 관리자만 CRUD 가능
- 브랜드 삭제 시 해당 브랜드의 상품도 함께 삭제 (CASCADE)

### 3.3 Product (상품)
- 특정 브랜드에 속함
- 재고 수량 관리 (stock)
- 좋아요 개수 추적 (likeCount)
- 정렬 기준: 최신순(latest), 가격순(price_asc), 좋아요순(likes_desc)

### 3.4 Like (좋아요)
- 사용자-상품 간 관계
- 중복 좋아요 방지 (멱등성)
- 좋아요 등록/취소 시 상품의 likeCount 업데이트

### 3.5 Order (주문)
- 주문 시점의 상품 정보 스냅샷 저장 (상품명, 가격)
- 재고 차감 보장
- 주문 상태 관리: PENDING, CONFIRMED, CANCELLED

---

## 4. 비즈니스 정책

### 4.1 주문 정책

#### 주문 생성
- 주문 시 상품 재고를 확인하고 차감한다
- 재고가 부족하면 주문 실패 (400 Bad Request)
- 주문 생성 시점의 상품명, 가격을 스냅샷으로 저장한다
- 주문 생성 후 상태는 `PENDING`

**스냅샷 저장 이유:**
- 주문 후 상품 정보(가격, 이름)가 변경되어도 주문 내역은 보존
- 법적 증빙 및 정산 처리

#### 주문 상태
| 상태 | 설명 |
|------|------|
| `PENDING` | 재고 차감 완료, 결제 대기 |
| `CONFIRMED` | 결제 완료, 주문 확정 |
| `CANCELLED` | 주문 취소 (재고 복구) |

**향후 확장 가능:**
- `SHIPPING`, `DELIVERED` 등 배송 상태 추가

#### 재고 처리
- 주문 생성 시: 재고 차감
- 주문 취소 시: 재고 복구
- 동시성 제어: 낙관적 락(Optimistic Lock) 또는 비관적 락 사용

---

### 4.2 좋아요 정책

#### 좋아요 등록
- **POST** `/api/v1/products/{productId}/likes`
- 이미 좋아요한 상품에 다시 요청 시: **200 OK (멱등성)**
  ```json
  {
    "productId": 1,
    "liked": true,
    "message": "already_liked"
  }
  ```
- 좋아요 등록 시 `products.like_count` 증가

#### 좋아요 취소
- **DELETE** `/api/v1/products/{productId}/likes`
- 좋아요하지 않은 상품에 DELETE 시: **204 No Content (멱등성)**
- 좋아요 취소 시 `products.like_count` 감소

#### 내가 좋아요한 상품 조회
- **GET** `/api/v1/users/{userId}/likes`
- 본인 좋아요 목록만 조회 가능 (타 유저 ID 접근 불가)

#### 좋아요 토글 UX 정책
- 클라이언트 UI는 토글 방식으로 동작:
  - 현재 `liked=false` 상태에서 클릭 → `POST /likes`
  - 현재 `liked=true` 상태에서 클릭 → `DELETE /likes`
- 서버는 각 API를 멱등적으로 보장하여 중복 요청에 안전해야 함

#### 멱등성 보장 이유
- 네트워크 재시도에 안전
- 모바일/웹 환경에서 중복 요청 가능성 대응
- 좋아요 버튼 토글 UI에 적합

---

### 4.3 브랜드 정책

#### 브랜드 삭제 (Soft Delete)
- **DELETE** `/api-admin/v1/brands/{brandId}`
- 브랜드 삭제 시 **Soft Delete** 방식으로 처리:
  - `deleted_at` 컬럼에 삭제 시각 기록
  - 해당 브랜드의 모든 상품도 함께 Soft Delete (애플리케이션 레벨 CASCADE)
  - 실제 데이터는 DB에 보존되며, 조회 시 제외됨
- 주문 테이블에는 상품 스냅샷이 있으므로 과거 주문 내역은 영향 없음

**Soft Delete 선택 이유:**
- **복구 가능**: 실수로 삭제한 브랜드/상품 복구 가능
- **데이터 보존**: 법적/감사 요구사항 대응, 데이터 분석 가능
- **운영 안전성**: Hard Delete의 돌이킬 수 없는 리스크 제거
- **고아 데이터 방지**: 브랜드 삭제 시 상품도 함께 soft delete 처리

**구현 방식:**
- 모든 조회 쿼리에 `WHERE deleted_at IS NULL` 조건 자동 적용
- JPA `@SQLDelete`, `@Where` 어노테이션 활용
- 애플리케이션 레벨에서 연관 엔티티 soft delete 처리

#### 브랜드 복구
- **POST** `/api-admin/v1/brands/{brandId}/restore`
- 삭제된 브랜드 복구:
  - `deleted_at`을 NULL로 설정
  - 해당 브랜드의 모든 상품도 함께 복구
- 관리자 권한 필요

**주의사항:**
- 브랜드 삭제는 운영 영향이 큰 작업이므로 관리자 확인 절차 필요
- 삭제된 데이터는 30일 후 배치 작업으로 물리 삭제 (선택사항)

---

### 4.4 상품 정렬 정책

상품 목록 조회 시 `sort` 파라미터로 정렬 기준 지정:

| sort 값 | 정렬 기준 | 구현 방식 |
|---------|-----------|-----------|
| `latest` | 최신순 (등록일 내림차순) | `ORDER BY created_at DESC` |
| `price_asc` | 가격 오름차순 | `ORDER BY price ASC` |
| `likes_desc` | 좋아요 많은 순 | `ORDER BY like_count DESC` |

#### 좋아요 개수 집계 전략

**Phase 1: 비정규화 (초기 구현)**
- `products` 테이블에 `like_count` 컬럼 추가
- 좋아요 등록/취소 시 `like_count` 업데이트
- 장점: 구현 단순, 정렬 쿼리 빠름
- 주의: 동시성 제어 필요 (낙관적 락)

**Phase 2: Redis 캐싱 (향후 확장)**
- 트래픽 증가 시 Redis로 전환
- `product:{productId}:likes` 키로 카운트 관리
- INCR/DECR 연산 사용
- 배치 작업으로 주기적으로 DB 동기화

---

## 5. API 제약사항

### 5.1 인증/인가
- **일반 사용자 API** (`/api/v1/*`):
  - Header: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
  - 인증 실패 시: 401 Unauthorized

- **관리자 API** (`/api-admin/v1/*`):
  - Header: `X-Loopers-Ldap: loopers.admin`
  - 인증 실패 시: 403 Forbidden

### 5.2 페이징
- `page`: 페이지 번호 (기본값: 0)
- `size`: 페이지당 항목 수 (기본값: 20)
- 최대 size: 100 (초과 시 400 Bad Request)

### 5.3 응답 형식
- 성공: 200 OK (데이터 포함)
- 생성: 201 Created (Location 헤더 포함)
- 삭제: 204 No Content
- 에러: 4xx/5xx (에러 메시지 포함)

---

## 6. 기술적 제약사항

### 6.1 트랜잭션 경계
- **주문 생성**: 주문 저장 + 재고 차감 → 하나의 트랜잭션
- **좋아요 등록**: 좋아요 저장 + likeCount 증가 → 하나의 트랜잭션

### 6.2 동시성 제어
- 재고 차감: 낙관적 락 사용 (`@Version`)
- 좋아요 카운트: 낙관적 락 또는 원자적 업데이트

### 6.3 성능 요구사항
- 상품 목록 조회: 100ms 이내
- 주문 생성: 500ms 이내
- 동시 접속자: 1000명 이상 처리 가능

### 6.4 데이터 수명주기
- 브랜드 삭제 시 해당 브랜드 상품은 함께 Soft Delete (애플리케이션 CASCADE)
- 상품 단건 삭제 시 Soft Delete 처리
- 좋아요는 상품이 soft-deleted 상태여도 유지 (통계용)
- 주문 이력은 스냅샷 기반으로 보존
- 삭제된 데이터는 30일 후 배치 작업으로 물리 삭제 가능 (선택사항)

---

## 7. API 목록 (요구사항 매칭)

### 7.1 유저 (Users)
| METHOD | URI | user_required | 설명 |
|--------|-----|---------------|------|
| POST | `/api/v1/users` | X | 회원가입 |
| GET | `/api/v1/users/me` | O | 내 정보 조회 |
| PUT | `/api/v1/users/password` | O | 비밀번호 변경 |

### 7.2 브랜드/상품 (고객 API)
| METHOD | URI | user_required | 설명 |
|--------|-----|---------------|------|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |
| GET | `/api/v1/products` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 상세 조회 |

### 7.3 좋아요 (Likes)
| METHOD | URI | user_required | 설명 |
|--------|-----|---------------|------|
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요한 상품 목록 조회 |

### 7.4 주문 (고객 API)
| METHOD | URI | user_required | 설명 |
|--------|-----|---------------|------|
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt={startAt}&endAt={endAt}` | O | 유저 주문 목록 조회 |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

### 7.5 브랜드/상품 ADMIN
| METHOD | URI | ldap_required | 설명 |
|--------|-----|---------------|------|
| GET | `/api-admin/v1/brands?page={page}&size={size}` | O | 등록 브랜드 목록 조회 (삭제된 항목 제외) |
| GET | `/api-admin/v1/brands/{brandId}` | O | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | O | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | O | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | O | 브랜드 Soft Delete (상품도 함께 soft delete) |
| POST | `/api-admin/v1/brands/{brandId}/restore` | O | 삭제된 브랜드 복구 (상품도 함께 복구) |
| GET | `/api-admin/v1/products?page={page}&size={size}&brandId={brandId}` | O | 등록 상품 목록 조회 (삭제된 항목 제외) |
| GET | `/api-admin/v1/products/{productId}` | O | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | O | 상품 등록 (브랜드는 반드시 존재해야 함) |
| PUT | `/api-admin/v1/products/{productId}` | O | 상품 정보 수정 (브랜드는 수정 불가) |
| DELETE | `/api-admin/v1/products/{productId}` | O | 상품 Soft Delete |
| POST | `/api-admin/v1/products/{productId}/restore` | O | 삭제된 상품 복구 |

### 7.6 주문 ADMIN
| METHOD | URI | ldap_required | 설명 |
|--------|-----|---------------|------|
| GET | `/api-admin/v1/orders?page={page}&size={size}` | O | 주문 목록 조회 |
| GET | `/api-admin/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

---

## 8. 예외 처리 시나리오

### 8.1 주문 생성 실패
| 케이스 | HTTP 상태 | 메시지 |
|--------|-----------|--------|
| 재고 부족 | 400 Bad Request | "재고가 부족합니다" |
| 존재하지 않는 상품 | 404 Not Found | "상품을 찾을 수 없습니다" |
| 중복 주문 (동시성) | 409 Conflict | "이미 처리 중인 주문입니다" |

### 8.2 브랜드 삭제 실패
| 케이스 | HTTP 상태 | 메시지 |
|--------|-----------|--------|
| 존재하지 않는 브랜드 | 404 Not Found | "브랜드를 찾을 수 없습니다" |
| 이미 삭제된 브랜드 | 400 Bad Request | "이미 삭제된 브랜드입니다" |

### 8.4 브랜드/상품 복구 실패
| 케이스 | HTTP 상태 | 메시지 |
|--------|-----------|--------|
| 존재하지 않는 브랜드/상품 | 404 Not Found | "브랜드/상품을 찾을 수 없습니다" |
| 삭제되지 않은 브랜드/상품 | 400 Bad Request | "삭제되지 않은 항목입니다" |

### 8.3 상품 등록 실패
| 케이스 | HTTP 상태 | 메시지 |
|--------|-----------|--------|
| 존재하지 않는 브랜드 | 400 Bad Request | "유효하지 않은 브랜드입니다" |
| 중복된 상품명 | 409 Conflict | "이미 존재하는 상품명입니다" |
| 상품 수정 시 브랜드 변경 시도 | 400 Bad Request | "상품의 브랜드는 변경할 수 없습니다" |

---

## 9. 향후 확장 가능성

### 9.1 주문 확장
- 결제 시스템 연동 (PG사)
- 배송 상태 추가 (SHIPPING, DELIVERED)
- 주문 취소/환불 정책

### 9.2 상품 확장
- 상품 카테고리 추가
- 상품 옵션 (색상, 사이즈)
- 상품 리뷰 시스템
- 삭제된 항목 배치 물리 삭제 (30일 경과 데이터)

### 9.3 성능 최적화
- Redis 캐싱 (상품 목록, 좋아요 개수)
- CDN (상품 이미지)
- 읽기 전용 Replica DB

### 9.4 검색 기능
- Elasticsearch 연동
- 상품명, 브랜드명 전문 검색
- 필터링 (가격대, 브랜드)

---

## 10. 비기능 요구사항

### 10.1 가용성
- 시스템 가동률: 99.9% 이상

### 10.2 확장성
- 수평적 확장 가능한 구조 (Stateless)

### 10.3 보안
- 비밀번호 암호화 (BCrypt)
- SQL Injection 방지 (JPA/QueryDSL)
- XSS 방지

### 10.4 모니터링
- API 응답 시간 추적
- 에러율 모니터링
- 재고 부족 알림

---

## 11. 설계 시 주의사항

### 11.1 책임 분리
- Controller: 요청/응답 변환
- Service: 비즈니스 로직
- Repository: 데이터 액세스

### 11.2 트랜잭션 범위
- Service 레이어에서 트랜잭션 관리
- 외부 API 호출은 트랜잭션 밖에서

### 11.3 도메인 모델
- 엔티티는 비즈니스 로직 포함
- DTO는 계층 간 데이터 전달만

### 11.4 테스트 전략
- 단위 테스트: Service, Domain 로직
- 통합 테스트: API, Repository (Testcontainers)
- 동시성 테스트: 재고 차감, 좋아요 카운트
