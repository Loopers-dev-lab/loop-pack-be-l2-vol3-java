# 토큰 검증 위치 — Filter vs Interceptor vs AOP

## 요청 처리 순서

```
HTTP 요청
    ↓
[Filter]          ← Servlet 컨테이너 레벨 (Spring 밖)
    ↓
DispatcherServlet
    ↓
[Interceptor]     ← Spring MVC 레벨 (Spring 안)
    ↓
[AOP]             ← 메서드 레벨
    ↓
Controller
    ↓
Service
```

## 각 레벨 특징

### Filter (Spring 밖)
- Spring이 뜨기 전, Tomcat 레벨에서 실행
- Spring Bean(`@Autowired`) 주입 불편
- 예외를 던져도 `@ControllerAdvice`가 못 잡음 → 직접 응답 작성해야 함
- 주로 쓰는 곳: JWT 파싱, 로깅, CORS 처리

### Interceptor (Spring 안)
- Spring MVC가 Controller에 요청을 넘기기 직전/직후 실행
- Spring Bean 자유롭게 주입 가능
- 예외 던지면 `@ControllerAdvice`가 정상 처리
- URL 패턴으로 적용 범위 설정 쉬움

### AOP (메서드 레벨)
- 특정 메서드 실행 전후에 코드 삽입
- 어노테이션 기반 세밀한 제어 가능
- 토큰 검증처럼 "특정 URL 전체 차단"에는 과도한 설계

## 왜 대기열 토큰 검증은 Interceptor?

| | Filter | Interceptor | AOP |
|-|--------|-------------|-----|
| Spring Bean 주입 | ❌ 불편 | ✅ 가능 | ✅ 가능 |
| 예외 처리 (ControllerAdvice) | ❌ 직접 | ✅ 자동 | ✅ 자동 |
| URL 패턴 적용 | △ 복잡 | ✅ 간단 | ❌ 어노테이션 필요 |

Redis 조회(Spring Bean) + `CoreException`(ControllerAdvice) + `/order/**` 패턴 → **Interceptor**

## 코드 예시

```java
public class QueueTokenInterceptor implements HandlerInterceptor {
    private final QueueTokenService queueTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, ...) {
        String token = request.getHeader("X-Queue-Token");
        if (!queueTokenService.isValid(token)) {
            throw new CoreException(ErrorType.INVALID_QUEUE_TOKEN);
        }
        return true;
    }
}

// WebMvcConfigurer에서 등록
registry.addInterceptor(queueTokenInterceptor)
        .addPathPatterns("/order/**");
```

## 퀴즈 Q&A

**Q. JWT 파싱은 Filter, 대기열 토큰 검증은 Interceptor를 쓰는 이유는?**
A. JWT 파싱은 Spring Bean 불필요(문자열 디코딩)하고 모든 요청에 적용. 대기열 토큰은 Redis 조회(Spring Bean 필요) + 특정 URL에만 적용 + ControllerAdvice로 예외 처리 필요 → Interceptor.

**Q. `/order/**` 진입 전 Redis 토큰 검증, 실패 시 CoreException → 어디에 구현?**
A. Interceptor. Spring Bean(Redis) 주입 가능 + URL 패턴 적용 + ControllerAdvice 예외 처리 모두 충족.