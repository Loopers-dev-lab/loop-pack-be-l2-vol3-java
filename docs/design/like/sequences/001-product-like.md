# 상품 좋아요 등록 시퀀스다이어그램

## 개요
사용자가 상품에 좋아요를 등록하면 좋아요 데이터를 저장하고 상품의 좋아요 수를 동기 증가시키는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant LC as LikeController
    participant LF as LikeFacade
    participant PS as ProductService
    participant LS as LikeService

    사용자->>LC: POST /api/v1/products/{productId}/likes
    activate LC
    LC->>LF: 좋아요 등록
    activate LF

    critical @Transactional
        LF->>PS: 활성 상품 확인
        activate PS
        PS-->>LF: 완료
        deactivate PS

        LF->>LS: 좋아요 등록
        activate LS
        LS-->>LF: 생성 여부 (boolean)
        deactivate LS

        opt 좋아요가 새로 생성됨
            LF->>PS: 좋아요 수 증가
            activate PS
            PS-->>LF: 완료
            deactivate PS
        end
    end

    LF-->>LC: 200 OK
    deactivate LF
    LC-->>사용자: 200 OK
    deactivate LC
```

## 핵심 포인트

- 트랜잭션 범위: Facade 메서드 전체를 `@Transactional`로 감싼다
- 활성 상품 확인: ProductService가 미존재/삭제 시 예외를 던진다 (Facade는 분기하지 않음)
- 좋아요 등록 캡슐화: LikeService가 존재 여부 확인 + 저장을 캡슐화한다. Facade는 도메인 내부 상태(Optional 등)를 직접 다루지 않는다
- 멱등성: 이미 좋아요 상태이면 저장/증가 없이 200 응답한다
- 좋아요 저장과 likeCount 증가는 같은 트랜잭션에서 처리한다
