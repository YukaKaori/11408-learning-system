package com.yuka.learning.auth;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yuka.learning.auth.dto.AuthUserResponse;
import com.yuka.learning.auth.dto.LoginRequest;
import com.yuka.learning.auth.dto.LoginResponse;
import com.yuka.learning.auth.dto.TokenPairResponse;
import com.yuka.learning.auth.dto.UpdateProfileRequest;
import com.yuka.learning.auth.security.AuthenticatedUser;
import com.yuka.learning.auth.security.UserPrincipal;
import com.yuka.learning.auth.token.TokenService;
import com.yuka.learning.auth.token.TokenService.ClientInfo;
import com.yuka.learning.auth.token.TokenService.IssuedTokens;
import com.yuka.learning.common.api.CommonErrorCode;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.user.entity.User;
import com.yuka.learning.user.mapper.UserMapper;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Authentication use-cases. Credential verification is delegated to Spring
 * Security's AuthenticationManager (BCrypt via DaoAuthenticationProvider);
 * token lifecycles are delegated to {@link TokenService}.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    public AuthService(AuthenticationManager authenticationManager,
                       TokenService tokenService,
                       UserMapper userMapper) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    public LoginResponse login(LoginRequest request, ClientInfo client) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.usernameOrEmail(), request.password()));
        } catch (LockedException ex) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_LOCKED);
        } catch (DisabledException ex) {
            throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        } catch (BadCredentialsException ex) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        User user = ((UserPrincipal) authentication.getPrincipal()).user();
        recordLogin(user.getId(), client);
        IssuedTokens tokens = tokenService.issue(user, client);
        return new LoginResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds(),
                AuthUserResponse.from(user));
    }

    public TokenPairResponse refresh(String refreshToken, ClientInfo client) {
        IssuedTokens tokens = tokenService.rotate(refreshToken, client);
        return new TokenPairResponse(tokens.accessToken(), tokens.refreshToken(), tokens.expiresInSeconds());
    }

    /**
     * Idempotent by design: revoking an unknown or already-revoked token is a
     * no-op, so logout never fails client-side cleanup.
     */
    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    public AuthUserResponse currentUser(AuthenticatedUser principal) {
        return AuthUserResponse.from(requireUser(principal.id()));
    }

    /**
     * Minimal self-service profile edit (nickname/avatar). Written through an
     * explicit update wrapper because blank input clears a column to null,
     * which {@code updateById}'s default null-skipping strategy would drop.
     */
    public AuthUserResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.nickname() != null) {
            user.setNickname(request.nickname().isBlank() ? null : request.nickname().trim());
        }
        if (request.avatar() != null) {
            user.setAvatar(request.avatar().isBlank() ? null : request.avatar().trim());
        }
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getNickname, user.getNickname())
                .set(User::getAvatar, user.getAvatar()));
        return AuthUserResponse.from(user);
    }

    private User requireUser(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            // Token is valid but the account is gone (deleted since issuance).
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    private void recordLogin(Long userId, ClientInfo client) {
        User update = new User();
        update.setId(userId);
        update.setLastLoginAt(LocalDateTime.now());
        update.setLastLoginIp(client.ip());
        userMapper.updateById(update);
    }
}
