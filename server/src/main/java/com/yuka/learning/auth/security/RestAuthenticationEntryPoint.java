package com.yuka.learning.auth.security;

import com.yuka.learning.common.api.CommonErrorCode;
import com.yuka.learning.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Emits the standard response envelope for unauthenticated requests by routing
 * through {@code GlobalExceptionHandler} — the security layer never builds
 * response bodies of its own. Reports the precise token failure when
 * {@link JwtAuthenticationFilter} recorded one.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final HandlerExceptionResolver exceptionResolver;

    public RestAuthenticationEntryPoint(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) {
        BusinessException cause = request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE)
                instanceof BusinessException tokenError
                ? tokenError
                : new BusinessException(CommonErrorCode.UNAUTHORIZED);
        exceptionResolver.resolveException(request, response, null, cause);
    }
}
