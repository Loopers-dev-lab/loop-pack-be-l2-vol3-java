# Step 1 — 대기열 설계

## 개요

Redis Sorted Set 기반 대기열입니다.
사용자가 진입하면 번호표(순번)를 발급하고, 스케줄러가 처리 가능한 수만큼만 다음 단계로 넘깁니다.

## 핵심 설계 결정

| 항목 | 결정 | 이유 |
|------|------|------|
| score 기준 | `System.currentTimeMillis()` | 밀리초 충돌은 극히 드물고 비즈니스상 무의미 |
| 중복 방지 | `ZADD NX` | 단일 명령어로 원자적 처리, TOCTOU 방지 |
| 순번 표현 | 1-indexed | 사용자에게 보여줄 때 0번째는 어색함 |

## API

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/v1/queue/enter?userId=` | 대기열 진입. 중복 진입 시 기존 순번 반환 |
| GET | `/api/v1/queue/position?userId=` | 현재 순번 + 전체 대기 인원 조회 |

## 시퀀스 다이어그램

### POST /queue/enter — 대기열 진입

```mermaid
sequenceDiagram
    actor User
    participant Controller as QueueV1Controller
    participant Facade as QueueFacade
    participant Service as QueueService
    participant Repo as QueueRepositoryImpl
    participant Redis

    User->>Controller: POST /api/v1/queue/enter?userId=user-1
    Controller->>Facade: enter(userId)
    Facade->>Service: enter(userId)
    Service->>Repo: enter(userId, currentTimeMillis())
    Repo->>Redis: ZADD queue:waiting NX score userId
    Redis-->>Repo: 1 (추가됨) or 0 (이미 존재)
    Repo->>Redis: ZRANK queue:waiting userId
    Redis-->>Repo: rank (0-indexed)
    Repo-->>Service: position (rank + 1)
    Service-->>Facade: position
    Facade->>Service: getTotalCount()
    Service->>Repo: getTotalCount()
    Repo->>Redis: ZCARD queue:waiting
    Redis-->>Repo: totalCount
    Repo-->>Facade: totalCount
    Facade-->>Controller: QueueInfo(position, totalCount)
    Controller-->>User: ApiResponse { position: 1, totalCount: 1 }
```

### GET /queue/position — 순번 조회

```mermaid
sequenceDiagram
    actor User
    participant Controller as QueueV1Controller
    participant Facade as QueueFacade
    participant Service as QueueService
    participant Repo as QueueRepositoryImpl
    participant Redis

    User->>Controller: GET /api/v1/queue/position?userId=user-1
    Controller->>Facade: getPosition(userId)
    Facade->>Service: getPosition(userId)
    Service->>Repo: findPosition(userId)
    Repo->>Redis: ZRANK queue:waiting userId
    Redis-->>Repo: rank or null
    alt 대기열에 없는 userId
        Repo-->>Service: Optional.empty()
        Service-->>Controller: CoreException(NOT_FOUND)
        Controller-->>User: 404 Not Found
    else 대기열에 있는 userId
        Repo-->>Service: Optional(rank + 1)
        Service-->>Facade: position
        Facade->>Service: getTotalCount()
        Service-->>Facade: totalCount
        Facade-->>Controller: QueueInfo(position, totalCount)
        Controller-->>User: ApiResponse { position: 2, totalCount: 5 }
    end
```

## 클래스 구조

```
domain/queue/
  QueueRepository       인터페이스 (enter, findPosition, getTotalCount)
  QueueService          enter(userId), getPosition(userId), getTotalCount()

infrastructure/queue/
  QueueRepositoryImpl   Redis Sorted Set 구현

application/queue/
  QueueFacade           enter, getPosition → QueueInfo 반환
  QueueInfo             record(position, totalCount)

interfaces/api/queue/
  QueueV1Controller     POST /enter, GET /position
  QueueV1ApiSpec        OpenAPI 스펙
  QueueV1Dto            EnterResponse, PositionResponse
```

## Redis 키

| 키 | 타입 | 설명 |
|----|------|------|
| `queue:waiting` | Sorted Set | 대기열. member=userId, score=진입시각(ms) |