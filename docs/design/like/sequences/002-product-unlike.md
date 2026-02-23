# 상품 좋아요 취소 시퀀스다이어그램

## 개요
사용자가 상품의 좋아요를 취소하면 좋아요 데이터를 물리적으로 삭제하고 상품의 좋아요 수를 동기 감소시키는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 사용자
    participant LC as LikeController
    participant LF as LikeFacade
    participant LS as LikeService
    participant PS as ProductService

    사용자->>LC: DELETE /api/v1/products/{productId}/likes
    activate LC
    LC->>LF: 좋아요 취소
    activate LF

    LF->>LS: 좋아요 존재 여부 확인
    activate LS
    LS-->>LF: 조회 결과
    deactivate LS

    alt 좋아요 없음
        LF-->>LC: 현재 상태 유지
        LC-->>사용자: 200 OK
    else 좋아요 존재
        critical @Transactional
            LF->>LS: 좋아요 물리적 삭제
            activate LS
            LS-->>LF: 완료
            deactivate LS

            LF->>PS: 좋아요 수 감소
            activate PS
            PS-->>LF: 완료
            deactivate PS
        end
        LF-->>LC: 취소 완료
        LC-->>사용자: 200 OK
    end
    deactivate LF
    deactivate LC
```

## 핵심 포인트

- 멱등성: 좋아요가 없으면 삭제/감소 없이 200 응답한다
- 좋아요 데이터는 물리적으로 삭제한다 (Soft Delete 아님)
- 좋아요 삭제와 likeCount 감소는 같은 트랜잭션에서 처리한다
- 상품 존재 여부를 검증하지 않는다 (멱등성 우선)
