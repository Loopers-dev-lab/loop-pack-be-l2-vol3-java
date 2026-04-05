package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String USER_ID_HEADER = "X-Loopers-User-Id";
    private static final String TOKEN_HEADER = "X-Entry-Token";

    private static final Set<ErrorType> NON_RETRYABLE_ERRORS = Set.of(
        ErrorType.PRODUCT_NOT_FOUND,
        ErrorType.PRODUCT_INSUFFICIENT_STOCK,
        ErrorType.COUPON_NOT_FOUND,
        ErrorType.COUPON_NOT_AVAILABLE
    );

    private final QueueRepository queueRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String userIdHeader = request.getHeader(USER_ID_HEADER);
        String token = request.getHeader(TOKEN_HEADER);

        if (userIdHeader == null || token == null) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_NOT_FOUND);
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new CoreException(ErrorType.BAD_REQUEST);
        }
        String storedToken = queueRepository.getToken(userId);

        if (storedToken == null) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_NOT_FOUND);
        }

        if (!storedToken.equals(token)) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_INVALID);
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return;
        }

        int status = response.getStatus();
        boolean isSuccess = (status >= 200 && status < 300);

        if (isSuccess && ex == null) {
            removeTokenAndReleaseSlot(request, "주문 성공 - 토큰 삭제");
            return;
        }

        ErrorType errorType = (ErrorType) request.getAttribute("errorType");
        if (errorType != null && NON_RETRYABLE_ERRORS.contains(errorType)) {
            removeTokenAndReleaseSlot(request, "비재시도 실패(" + errorType.getCode() + ") - 토큰 삭제, 슬롯 반환");
        }
    }

    private void removeTokenAndReleaseSlot(HttpServletRequest request, String reason) {
        String userIdHeader = request.getHeader(USER_ID_HEADER);
        if (userIdHeader != null) {
            Long userId = Long.parseLong(userIdHeader);
            queueRepository.removeToken(userId);
            queueRepository.decrementActiveTokenCount();
            log.debug("{} - userId:{}", reason, userId);
        }
    }
}
