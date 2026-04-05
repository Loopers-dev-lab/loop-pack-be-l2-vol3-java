package com.loopers.interfaces.api.queue;

import com.loopers.domain.member.Member;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.QueueTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// 주문 API(/api/v1/orders)에 대한 입장 토큰 검증 인터셉터.
// WebMvcConfig에서 /api/v1/orders 경로에 등록되어 POST 요청에만 동작한다.
//
// 동작 흐름:
// [preHandle] 주문 생성(POST) 요청 시 토큰을 원자적으로 소모(GETDEL)한다.
//   - 대기열이 비활성 상태이면 토큰 없이도 통과시킨다.
//   - 대기열이 활성 상태이면 토큰을 GETDEL로 소모하고, 없으면 거부한다.
//   - 동시 요청 시 GETDEL의 원자성으로 하나만 통과한다.
//
// [afterCompletion] 주문이 실패(non-2xx 또는 예외)하면 토큰을 재발급하여 재시도 기회를 제공한다.
//   - 주문 성공 시 아무 작업도 하지 않는다 (preHandle에서 이미 소모됨).
@Slf4j
@RequiredArgsConstructor
@Component
@ConditionalOnBean(QueueService.class)
public class QueueTokenInterceptor implements HandlerInterceptor {

    // MemberAuthInterceptor가 request에 설정하는 로그인 유저 정보 attribute 키
    private static final String LOGIN_MEMBER_ATTRIBUTE = "loginMember";

    // preHandle에서 설정하고 afterCompletion에서 참조하기 위한 대기열 활성화 상태 attribute 키.
    // preHandle과 afterCompletion 사이에 피처 플래그가 변경되는 race condition을 방지한다.
    private static final String QUEUE_ENABLED_ATTRIBUTE = "queueEnabled";

    // preHandle에서 토큰이 소모되었음을 afterCompletion에 전달하기 위한 attribute 키.
    // 주문 실패 시 토큰 재발급 여부를 판단하는 데 사용된다.
    private static final String TOKEN_CONSUMED_ATTRIBUTE = "tokenConsumed";

    private final QueueService queueService;
    private final QueueTokenService queueTokenService;

    // POST 요청에 대해서만 토큰 검증을 수행한다.
    // GET 등 조회 요청은 대기열 없이 자유롭게 접근 가능하다.
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 대기열 활성화 상태를 request attribute에 저장하여
        // afterCompletion에서 동일한 값을 참조할 수 있도록 한다.
        boolean queueEnabled = queueService.isQueueEnabled();
        request.setAttribute(QUEUE_ENABLED_ATTRIBUTE, queueEnabled);

        if (!queueEnabled) {
            return true;
        }

        Member member = getMember(request);
        if (member == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 필요합니다.");
        }

        // GETDEL로 토큰을 원자적으로 소모한다.
        // 동시 요청 시 하나만 토큰 값을 받고, 나머지는 empty로 차단된다.
        if (queueTokenService.consumeToken(member.getId()).isEmpty()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 없습니다. 대기열에 먼저 진입해주세요.");
        }

        request.setAttribute(TOKEN_CONSUMED_ATTRIBUTE, true);
        return true;
    }

    // 주문 요청 처리 완료 후 호출된다.
    // preHandle에서 토큰이 이미 소모(GETDEL)되었으므로, 성공 시 추가 작업이 필요 없다.
    // 주문 실패(non-2xx 또는 예외) 시 토큰을 재발급하여 재시도 기회를 제공한다.
    //
    // 안전성 참고:
    // ApiControllerAdvice(@RestControllerAdvice)가 예외를 처리하면 ex는 null이 될 수 있으나,
    // response.getStatus()에는 예외 핸들러가 설정한 올바른 HTTP 상태 코드(4xx/5xx)가 반영되므로
    // 2xx 범위 체크로 실패 케이스가 정확히 필터링된다.
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        Boolean queueEnabled = (Boolean) request.getAttribute(QUEUE_ENABLED_ATTRIBUTE);
        if (!Boolean.TRUE.equals(queueEnabled)) {
            return;
        }

        Boolean tokenConsumed = (Boolean) request.getAttribute(TOKEN_CONSUMED_ATTRIBUTE);
        if (!Boolean.TRUE.equals(tokenConsumed)) {
            return;
        }

        Member member = getMember(request);
        if (member == null) {
            return;
        }

        boolean isSuccess = ex == null && response.getStatus() >= 200 && response.getStatus() < 300;
        if (isSuccess) {
            log.info("주문 성공, 토큰 소모 완료 memberId={}, status={}", member.getId(), response.getStatus());
        } else {
            // 주문 실패 시 토큰을 재발급하여 재시도 기회를 제공한다.
            try {
                queueTokenService.issueToken(member.getId());
                log.info("주문 실패, 토큰 재발급 memberId={}, status={}", member.getId(), response.getStatus());
            } catch (Exception restoreEx) {
                log.error("주문 실패 후 토큰 재발급도 실패 memberId={}", member.getId(), restoreEx);
            }
        }
    }

    // request attribute에서 MemberAuthInterceptor가 설정한 로그인 유저 정보를 꺼낸다.
    // MemberAuthInterceptor가 먼저 실행되므로 인증된 유저 정보가 이미 설정되어 있다.
    private Member getMember(HttpServletRequest request) {
        Object attribute = request.getAttribute(LOGIN_MEMBER_ATTRIBUTE);
        return attribute instanceof Member ? (Member) attribute : null;
    }
}
