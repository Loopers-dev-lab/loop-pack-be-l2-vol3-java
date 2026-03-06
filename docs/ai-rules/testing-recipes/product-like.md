# Product / Like Recipe

## Scope
- 상품 목록 조회, 좋아요 등록/취소, likeCount 반영

## Mandatory Cases
- 상품 목록 조회 성공 (필터/정렬/페이지네이션)
- 존재하지 않는 필터 조건에서 빈 결과 반환
- 좋아요 등록 성공
- 중복 좋아요 충돌
- 좋아요 취소 성공/미존재 좋아요 취소 실패
- likeCount 증감 정합성

## Assertions
- 조회 API: 응답 구조 + 핵심 필드 + 페이징 정보
- 좋아요 API: 상태코드 + 카운트 변화

## Risk Checks
- likeCount와 실제 Like 데이터 불일치 가능성을 회귀 케이스로 추가
