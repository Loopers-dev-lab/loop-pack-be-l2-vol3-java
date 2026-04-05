package com.loopers.interfaces.auth;

import com.loopers.application.queue.QueueFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String ENTRY_TOKEN_HEADER = "X-Loopers-EntryToken";

    private final QueueFacade queueFacade;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }

        if (!method.hasMethodAnnotation(EntryTokenRequired.class)) {
            return true;
        }

        AuthenticatedUser authenticatedUser = (AuthenticatedUser) request.getAttribute("authenticatedUser");
        String token = request.getHeader(ENTRY_TOKEN_HEADER);

        if (authenticatedUser == null || token == null || !queueFacade.validateToken(authenticatedUser.id(), token)) {
            throw new CoreException(ErrorType.FORBIDDEN);
        }

        return true;
    }
}
