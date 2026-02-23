# 브랜드 삭제 시퀀스다이어그램

## 개요
관리자가 브랜드를 삭제하면 해당 브랜드에 속한 모든 상품도 연쇄 삭제되는 흐름을 정의한다.

## 시퀀스

```mermaid
sequenceDiagram
    actor 관리자
    participant BC as BrandController
    participant BF as BrandFacade
    participant BS as BrandService
    participant PS as ProductService

    관리자->>BC: DELETE /api-admin/v1/brands/{brandId}
    activate BC
    BC->>BF: 브랜드 삭제
    activate BF

    critical @Transactional
        BF->>BS: 브랜드 조회
        activate BS
        BS-->>BF: Brand
        deactivate BS

        BF->>BS: 브랜드 삭제 처리
        activate BS
        BS-->>BF: 완료
        deactivate BS

        BF->>PS: 하위 상품 연쇄 삭제
        activate PS
        PS-->>BF: 완료
        deactivate PS
    end

    BF-->>BC: 완료
    deactivate BF
    BC-->>관리자: 200 OK
    deactivate BC
```

## 핵심 포인트

- 브랜드 삭제와 하위 상품 삭제는 같은 트랜잭션에서 원자적으로 처리한다
- Facade가 BrandService와 ProductService를 오케스트레이션한다
- Brand Entity는 Product를 모르며, 연쇄 삭제는 Facade의 책임이다
