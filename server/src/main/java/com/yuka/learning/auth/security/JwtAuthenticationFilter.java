package com.yuka.learning.auth.security;

import com.yuka.learning.auth.token.TokenService;
import com.yuka.learning.common.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying a {@code Bearer} access token.
 * <p>
 * A missing or bad token never short-circuits the chain here — the request
 * simply continues unauthenticated and the authorization rules decide.
 * The precise failure (expired vs. invalid) is stashed as a request attribute
 * so {@link RestAuthenticationEntryPoint} can report an exact error code.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the {@link BusinessException} raised during token parsing. */
    public static final String TOKEN_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".tokenError";

    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenService tokenService;

    public JwtAuthenticationFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            try {
                TokenService.AccessTokenClaims claims =
                        tokenService.parseAccessToken(header.substring(BEARER_PREFIX.length()));
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        new AuthenticatedUser(claims.userId(), claims.username()),
                        null,
                        List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                SecurityContextHolder.clearContext();
                request.setAttribute(TOKEN_ERROR_ATTRIBUTE, ex);
            }
        }
        filterChain.doFilter(request, response);
    }
}
