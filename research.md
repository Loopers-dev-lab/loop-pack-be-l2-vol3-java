# Redis Cache Research

## 1. 현재 프로젝트 Redis 인프라 분석

### 1-1. 토폴로지
- **Master-Replica 구조** (읽기 분산)
- Master: `localhost:6379` (쓰기 + 읽기)
- Replica: `localhost:6380` (읽기 전용, `replicaof master`)
- Lettuce 클라이언트, `ReadFrom.REPLICA_PREFERRED` 기본 설정

### 1-2. 현재 구성 (modules/redis)
| 구성요소 | 설명 |
|---------|------|
| `RedisConfig` | Master/Replica 커넥션 팩토리 2개, RedisTemplate 2개(default=replica우선, master전용) |
| `RedisProperties` | `datasource.redis.*` 바인딩 (database, master, replicas) |
| `RedisNodeInfo` | host, port record |
| Serializer | Key/Value 모두 `StringRedisSerializer` |
| TestContainers | 단일 Redis 컨테이너로 테스트 (master=replica 동일 포트) |

### 1-3. 현재 사용 현황
- Redis 모듈은 3개 앱 모두에 의존 (`commerce-api`, `commerce-batch`, `commerce-streamer`)
- **현재 캐시 적용된 코드 없음** — `@Cacheable`, `@CacheEvict`, `@CachePut` 미사용
- `CacheManager` Bean 미등록 상태

---

## 2. 캐시 적용 대상 분석

### 2-1. 상품 상세 API
- **특성**: 단건 조회, 상품 ID 기반, 높은 조회 빈도
- **캐시 키**: `product:{productId}`
- **TTL**: 5~10분 (상품 정보 변경 빈도 낮음)
- **무효화**: 상품 수정/삭제 시 해당 키 evict

### 2-2. 상품 목록 API
- **특성**: 다건 조회, 브랜드 필터 + 좋아요 순 정렬, 페이징
- **캐시 키**: `products:brandId:{brandId}:sort:{sortType}:page:{page}:size:{size}`
- **TTL**: 1~3분 (좋아요 수 변동 반영 필요)
- **무효화**: 좋아요 토글, 상품 등록/수정/삭제 시 관련 키 패턴 삭제

---

## 3. 캐시 전략 비교

### 3-1. Spring Cache Abstraction (`@Cacheable`)
```
장점: 선언적, 코드 침투 최소, AOP 기반
단점: 세밀한 제어 어려움, 복잡한 키 전략 한계
적합: 상품 상세 (단순 키)
```

### 3-2. RedisTemplate 직접 사용
```
장점: 완전한 제어, 복잡한 키 패턴 삭제 가능, 부분 갱신
단점: 보일러플레이트 증가, 캐시 로직이 비즈니스에 침투
적합: 상품 목록 (복합 키, 패턴 기반 무효화)
```

### 3-3. 하이브리드 (권장)
- 상품 상세 → `@Cacheable` + `@CacheEvict`
- 상품 목록 → `RedisTemplate` 직접 사용 (패턴 기반 무효화)

---

## 4. 캐시 무효화 전략

### 4-1. TTL 기반 (Time-To-Live)
- 가장 단순, 일정 시간 후 자동 만료
- 정합성 보장 수준: TTL 범위 내 eventual consistency
- **리스크**: TTL 동안 stale data 노출

### 4-2. 이벤트 기반 명시적 무효화
- 데이터 변경 시점에 캐시 삭제
- 정합성 보장 수준: 높음 (변경 즉시 반영)
- **리스크**: 무효화 누락 시 영구 stale data

### 4-3. TTL + 이벤트 하이브리드 (권장)
- 변경 시 즉시 evict + TTL을 안전망으로 설정
- 무효화 누락되어도 TTL 후 자동 갱신

---

## 5. 캐시 키 설계

### 5-1. 네이밍 컨벤션
```
{domain}:{identifier}
{domain}:{filter1}:{value1}:{filter2}:{value2}
```

### 5-2. 구체적 키 설계안
| API | 캐시 키 패턴 | TTL |
|-----|-------------|-----|
| 상품 상세 | `product:detail:{productId}` | 10분 |
| 상품 목록 | `product:list:brand:{brandId}:sort:{sortType}:page:{page}:size:{size}` | 3분 |

### 5-3. 무효화 매핑
| 이벤트 | 삭제 대상 |
|--------|----------|
| 상품 수정 | `product:detail:{productId}` + `product:list:*` |
| 상품 삭제 | `product:detail:{productId}` + `product:list:*` |
| 좋아요 토글 | `product:detail:{productId}` + `product:list:*` (좋아요순 정렬 캐시) |

---

## 6. 구현 시 고려사항

### 6-1. CacheManager 설정 필요
- 현재 `RedisConfig`에 `RedisCacheManager` Bean 미등록
- `@Cacheable` 사용을 위해 `RedisCacheManager` + `RedisCacheConfiguration` 추가 필요
- JSON 직렬화: `GenericJackson2JsonRedisSerializer` 또는 도메인별 커스텀 직렬화

### 6-2. 캐시 미스 시 정상 동작 보장
- Redis 장애 시에도 DB fallback으로 서비스 지속
- `@Cacheable`의 기본 동작: 캐시 미스 → DB 조회 → 캐시 저장
- Redis 연결 실패 시 예외 처리: `CacheErrorHandler` 커스텀 구현 고려

### 6-3. 직렬화 전략
| 방식 | 장점 | 단점 |
|------|------|------|
| `StringRedisSerializer` (현재) | 단순, 디버깅 용이 | 객체 저장 불가 |
| `GenericJackson2JsonRedisSerializer` | 범용, 타입 정보 포함 | 저장 공간 큼 |
| `Jackson2JsonRedisSerializer<T>` | 타입별 최적화 | 캐시마다 설정 필요 |

### 6-4. Master/Replica 읽기 분산과 캐시의 관계
- 캐시 읽기: `defaultRedisTemplate` (REPLICA_PREFERRED) → replica에서 읽기
- 캐시 쓰기/삭제: master에서 수행 (replica는 자동 동기화)
- **주의**: replica 동기화 지연(replication lag) 동안 stale 캐시 읽힐 수 있음

---

## 7. 구현 순서 (안)

```
Step 1 → RedisCacheManager Bean 등록 + CacheConfiguration 설정
         검증: CacheManager Bean 로딩 확인

Step 2 → 상품 상세 API 캐시 적용 (@Cacheable / @CacheEvict)
         검증: 캐시 히트/미스 테스트, 수정 시 무효화 테스트

Step 3 → 상품 목록 API 캐시 적용 (RedisTemplate 직접 사용)
         검증: 필터/정렬 조합별 캐시 동작, 좋아요 토글 시 무효화 테스트

Step 4 → CacheErrorHandler 구현 (Redis 장애 시 graceful fallback)
         검증: Redis 중단 상태에서 API 정상 응답 확인

Step 5 → 성능 비교 (캐시 적용 전/후 응답시간 측정)
         검증: 캐시 히트 시 응답시간 단축 확인
```

---

## 8. 참고: Round 5 요구사항 체크리스트

- [ ] Redis 캐시를 적용하고 TTL 또는 무효화 전략을 적용했다
- [ ] 캐시 미스 상황에서도 서비스가 정상 동작하도록 처리했다
