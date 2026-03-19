---
name: sync-pg-spec
description: 토스페이먼츠, 나이스페이먼츠 공식 API 스펙을 Playwright MCP로 크롤링하여 docs/{pg}/spec/을 최신화하고, 현재 mock 서버 코드와의 차이점을 분석합니다. "PG 스펙 동기화", "토스 API 스펙 업데이트", "나이스 공식문서 싱크", "mock 스펙 차이 확인"을 요청할 때 사용합니다. mock 소스 코드 수정에는 사용하지 마세요.
---

# PG 공식 스펙 동기화

PG사 공식 API 문서를 Playwright MCP로 크롤링하여 `docs/{pg}/spec/`을 최신화하고, mock 서버 코드와 스펙 차이를 분석하는 스킬입니다.

## 크롤링 소스

### 토스페이먼츠

| 스펙 문서 | 공식 URL |
|-----------|----------|
| `confirm.md` | `https://docs.tosspayments.com/reference#결제-승인` |
| `cancel.md` | `https://docs.tosspayments.com/reference#결제-취소` |
| `get-payment.md` | `https://docs.tosspayments.com/reference#결제-조회` |
| `error-codes.md` | `https://docs.tosspayments.com/reference/error-codes` |

### 나이스페이먼츠

GitHub 기반 문서 (Playwright 대신 raw URL 접근 가능):

| 스펙 문서 | 소스 URL |
|-----------|----------|
| `payment-window-server.md` | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/payment-window-server.md` |
| (인증) | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/common/api.md` |
| (에러코드) | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/common/code.md` |
| (취소) | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/cancel.md` |
| (조회) | `https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/status-transaction.md` |

## 대상 경로

pg-mock-server 저장소 기준:

```
docs/
├── toss/spec/       ← 토스 공식 스펙
│   ├── confirm.md
│   ├── cancel.md
│   ├── get-payment.md
│   └── error-codes.md
└── nice/spec/       ← 나이스 공식 스펙
    └── payment-window-server.md
```

## 명령어

### 단계 1: 대상 PG 선택

사용자에게 어떤 PG의 스펙을 업데이트할지 확인합니다.

- `toss` — 토스페이먼츠만
- `nice` — 나이스페이먼츠만
- `all` — 전체

### 단계 2: 공식 문서 크롤링

#### 토스페이먼츠

Playwright MCP로 공식 문서 페이지를 크롤링합니다.

```
mcp__playwright__browser_navigate → https://docs.tosspayments.com/reference#결제-승인
mcp__playwright__browser_snapshot → 결제 승인 API 스펙 추출
```

각 API별로 다음을 추출합니다:
- Endpoint (method + path)
- Request 파라미터 (이름, 타입, 필수 여부, 설명)
- Response 필드 (이름, 타입, 설명)
- 에러 코드 목록

#### 나이스페이먼츠

GitHub raw URL로 직접 접근합니다.

```
mcp__playwright__browser_navigate → https://raw.githubusercontent.com/nicepayments/nicepay-manual/main/api/payment-window-server.md
```

마크다운을 그대로 가져와서 필요한 섹션을 추출합니다.

### 단계 3: 기존 스펙과 diff 비교

크롤링한 최신 스펙과 현재 `docs/{pg}/spec/` 파일을 비교합니다.

확인 항목:
- **추가된 파라미터**: 공식 스펙에 새로 추가된 요청/응답 필드
- **삭제된 파라미터**: 공식 스펙에서 제거된 필드
- **변경된 타입/필수 여부**: 기존 필드의 스펙 변경
- **새로운 에러 코드**: 추가된 에러 코드
- **Endpoint 변경**: URL path 또는 HTTP method 변경

### 단계 4: 스펙 문서 업데이트

변경 사항이 있으면 `docs/{pg}/spec/` 파일을 업데이트합니다.
사용자에게 diff를 보여주고 확인을 받은 후 수정합니다.

### 단계 5: Mock 코드 영향 분석

업데이트된 스펙을 기반으로 현재 mock 코드와의 차이를 분석합니다.

비교 대상 (토스 기준):
- `mock-toss/src/.../controller/TossPaymentController.java` — endpoint, 요청 파라미터
- `mock-toss/src/.../response/TossPaymentResponse.java` — 응답 필드
- `mock-toss/src/.../error/TossErrorTrigger.java` — 에러 코드
- `mock-toss/src/.../auth/TossAuthValidator.java` — 인증 방식
- `mock-toss/src/.../domain/Payment.java` — 도메인 모델, 상태값

비교 대상 (나이스 기준):
- `mock-nice/src/.../controller/NicePaymentController.java`
- `mock-nice/src/.../response/NicePaymentResponse.java`
- `mock-nice/src/.../error/NiceErrorTrigger.java`
- `mock-nice/src/.../auth/NiceAuthValidator.java`
- `mock-nice/src/.../domain/Payment.java`

### 단계 6: 결과 보고

사용자에게 다음을 보고합니다:

1. **스펙 변경 요약**: 추가/삭제/변경된 항목
2. **Mock 영향도**: mock 코드에서 수정이 필요한 부분
3. **권장 조치**:
   - High Fidelity 항목 (응답 필드명, 에러 코드, 상태값 등) → 반드시 mock 코드 수정
   - Low Fidelity 항목 (메시지 텍스트, 부가 필드 등) → 선택적 수정
4. **수정 안 해도 되는 것**: fidelity-policy.md 기준 Low Fidelity 항목

mock 코드 수정 자체는 이 스킬의 범위가 아닙니다. 분석 결과를 바탕으로 사용자가 직접 수정하거나 별도로 요청합니다.

## 예시

### 예시 1: 전체 스펙 동기화

사용자: "PG 스펙 동기화해줘"

동작:
1. 토스 + 나이스 공식 문서 크롤링
2. 기존 spec/ 파일과 diff 비교
3. 변경 사항 보고
4. 확인 후 spec/ 업데이트
5. mock 코드 영향 분석

### 예시 2: 토스만 확인

사용자: "토스 API 스펙 변경된 거 있나 확인해줘"

동작:
1. 토스 공식 문서 4개 크롤링
2. 현재 spec/ 파일과 비교
3. 변경 사항 보고 (업데이트는 사용자 확인 후)

### 예시 3: 나이스 에러코드만

사용자: "나이스 에러코드 최신화해줘"

동작:
1. nicepay-manual/common/code.md 크롤링
2. 현재 에러코드와 비교
3. NiceErrorTrigger.java에 없는 새 에러코드 보고

## 트러블슈팅

### Playwright MCP 연결 실패
**원인:** MCP 서버가 실행되지 않았거나 .mcp.json 설정이 없음
**해결:** `.mcp.json`에 playwright MCP 설정이 있는지 확인. 없으면 나이스는 WebFetch로 raw GitHub URL 접근 가능.

### 토스 공식 문서 구조 변경
**원인:** docs.tosspayments.com 페이지 레이아웃이 변경됨
**해결:** browser_snapshot으로 현재 페이지 구조를 확인하고, 추출 로직을 조정. 토스는 SPA라서 snapshot이 필수.

### 스펙은 변경됐지만 mock 수정 불필요한 경우
**원인:** Low Fidelity 항목만 변경됨 (메시지 텍스트, 부가 필드 등)
**해결:** `docs/{pg}/mock/fidelity-policy.md`를 참고하여 High/Low Fidelity 구분. Low Fidelity 변경은 무시해도 됨.
