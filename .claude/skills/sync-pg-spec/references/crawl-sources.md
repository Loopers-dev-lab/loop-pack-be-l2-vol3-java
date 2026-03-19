# 크롤링 소스 URL 목록

## 토스페이먼츠

토스는 SPA 기반이므로 반드시 Playwright MCP `browser_navigate` + `browser_snapshot`으로 접근해야 합니다.

### API 스펙 페이지

| API | URL | 추출 대상 |
|-----|-----|-----------|
| 결제 승인 | `https://docs.tosspayments.com/reference#결제-승인` | endpoint, request params, response fields |
| 결제 취소 | `https://docs.tosspayments.com/reference#결제-취소` | endpoint, request params, response fields |
| 결제 조회 | `https://docs.tosspayments.com/reference#결제-조회` | endpoint, request params, response fields |
| 에러 코드 | `https://docs.tosspayments.com/reference/error-codes` | code, message, HTTP status |

### 가이드 페이지 (보조)

| 주제 | URL |
|------|-----|
| 결제 흐름 | `https://docs.tosspayments.com/guides/v2/get-started/payment-flow` |
| 인증/헤더 | `https://docs.tosspayments.com/reference/using-api/authorization` |
| API 키 | `https://docs.tosspayments.com/reference/using-api/api-keys` |

## 나이스페이먼츠

나이스는 GitHub에 마크다운 매뉴얼이 있으므로 raw URL로 직접 접근 가능합니다.
Playwright 없이 `WebFetch`로도 가져올 수 있습니다.

### GitHub Raw URL

| 문서 | URL |
|------|-----|
| 결제창 Server 승인 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/payment-window-server.md` |
| API/인증 공통 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/common/api.md` |
| 에러코드 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/common/code.md` |
| 취소/환불/망취소 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/cancel.md` |
| 거래 조회 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/status-transaction.md` |
| 빌링 | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/payment-subscribe.md` |

### GitHub 저장소

- Repository: `https://github.com/nicepayments/nicepay-manual`
- 마지막 확인일: 2026-03-19
