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

    LF->>PS: 상품 조회
    activate PS
    PS-->>LF: Product
    deactivate PS

    LF->>LS: 좋아요 존재 여부 확인
    activate LS
    LS-->>LF: 조회 결과
    deactivate LS

    alt 이미 좋아요 상태
        LF-->>LC: 현재 상태 유지
        LC-->>사용자: 200 OK
    else 좋아요 없음
        critical @Transactional
            LF->>LS: 좋아요 저장
            activate LS
            LS-->>LF: 완료
            deactivate LS

            LF->>PS: 좋아요 수 증가
            activate PS
            PS-->>LF: 완료
            deactivate PS
        end
        LF-->>LC: 등록 완료
        LC-->>사용자: 200 OK
    end
    deactivate LF
    deactivate LC
```

## 핵심 포인트

- 멱등성: 이미 좋아요 상태이면 저장/증가 없이 200 응답한다
- 좋아요 저장과 likeCount 증가는 같은 트랜잭션에서 처리한다
- 상품 존재 여부를 먼저 검증하여 삭제된 상품에 좋아요를 방지한다
