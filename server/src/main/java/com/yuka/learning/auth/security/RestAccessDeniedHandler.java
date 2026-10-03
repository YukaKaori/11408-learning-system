package com.yuka.learning.auth.security;

import com.yuka.learning.common.api.CommonErrorCode;
import com.yuka.learning.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Emits the standard response envelope when an authenticated request lacks
 * permission (relevant once RBAC lands; wired now so the behaviour is uniform).
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final HandlerExceptionResolver exceptionResolver;

    public RestAccessDeniedHandler(
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) {
        exceptionResolver.resolveException(request, response, null,
                new BusinessException(CommonErrorCode.FORBIDDEN));
    }
}
