# Resilience4j PG 장애 대응 설계

## 요구사항
- Timeout: 외부 시스템 응답 지연 제어 (기존 RestTemplate 설정 존재)
- Retry: 일시적 실패 시 PG 호출만 재시도 (최대 3회)
- CircuitBreaker: PG 반복 실패 시 호출 차단, 시스템 보호
- Fallback: 실패 시 주문 PAYMENT_FAILED 처리 + 사용자 응답

## 의사결정
1. 라이브러리: Resilience4j (spring-boot3 + AOP)
2. 적용 레이어: PaymentFacade (비즈니스 상태 정리가 필요하므로)
3. Retry 범위: PG 호출 메서드만 분리하여 재시도 (주문/결제 레코드는 한 번만)
4. Retry 대상 예외: ResourceAccessException, HttpServerErrorException만
5. Fallback: 트랜잭션 롤백 + 별도 트랜잭션으로 주문 PAYMENT_FAILED 변경

## 구현 범위
1. build.gradle.kts에 resilience4j 의존성 추가
2. application.yml에 retry, circuitbreaker 설정
3. PaymentFacade 리팩터링 (Retry/CircuitBreaker/Fallback 적용)
4. PENDING 상태 복구용 폴링 API (콜백 미수신 대응)
5. 테스트 작성

## Resilience4j 설정값
```yaml
resilience4j:
  retry:
    instances:
      pg:
        max-attempts: 3
        wait-duration: 500ms
        retry-exceptions:
          - org.springframework.web.client.ResourceAccessException
          - org.springframework.web.client.HttpServerErrorException
        ignore-exceptions:
          - org.springframework.web.client.HttpClientErrorException
        fail-after-max-attempts: true

  circuitbreaker:
    instances:
      pg:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 2s
        slow-call-rate-threshold: 50
```
