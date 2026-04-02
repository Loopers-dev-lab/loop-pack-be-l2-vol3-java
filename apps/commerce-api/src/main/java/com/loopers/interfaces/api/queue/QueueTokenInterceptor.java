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
// [preHandle] 주문 생성(POST) 요청 시 토큰 보유 여부를 검증한다.
//   - 대기열이 비활성 상태이면 토큰 없이도 통과시킨다.
//   - 대기열이 활성 상태이면 입장 토큰이 없는 유저의 요청을 거부한다.
//
// [afterCompletion] 주문이 성공(2xx)하면 사용된 토큰을 삭제하여 1회성 사용을 보장한다.
//   - 주문 실패 시 토큰을 유지하여 유저가 재시도할 수 있도록 한다.
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

        // Redis에서 해당 유저의 입장 토큰 존재 여부를 확인한다.
        // 토큰이 없거나 TTL 만료된 경우 주문을 거부한다.
        if (!queueTokenService.hasToken(member.getId())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 없습니다. 대기열에 먼저 진입해주세요.");
        }

        return true;
    }

    // 주문 요청 처리 완료 후 호출된다.
    // 주문이 성공(2xx, 예외 없음)한 경우에만 토큰을 삭제하여 1회성 사용을 보장한다.
    // 주문 실패 시 토큰을 유지하여 TTL 내에서 재시도할 수 있도록 한다.
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

        Member member = getMember(request);
        if (member == null) {
            return;
        }

        boolean isSuccess = ex == null && response.getStatus() >= 200 && response.getStatus() < 300;
        if (isSuccess) {
            queueTokenService.deleteToken(member.getId());
            log.info("주문 완료 후 토큰 삭제 memberId={}, status={}", member.getId(), response.getStatus());
        }
    }

    // request attribute에서 MemberAuthInterceptor가 설정한 로그인 유저 정보를 꺼낸다.
    // MemberAuthInterceptor가 먼저 실행되므로 인증된 유저 정보가 이미 설정되어 있다.
    private Member getMember(HttpServletRequest request) {
        Object attribute = request.getAttribute(LOGIN_MEMBER_ATTRIBUTE);
        return attribute instanceof Member ? (Member) attribute : null;
    }
}
