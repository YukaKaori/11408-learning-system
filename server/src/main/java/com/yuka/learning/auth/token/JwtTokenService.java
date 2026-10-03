package com.yuka.learning.auth.token;

import com.yuka.learning.auth.AuthErrorCode;
import com.yuka.learning.auth.entity.RefreshToken;
import com.yuka.learning.auth.mapper.RefreshTokenMapper;
import com.yuka.learning.common.exception.BusinessException;
import com.yuka.learning.config.AppProperties;
import com.yuka.learning.user.entity.User;
import com.yuka.learning.user.entity.UserStatus;
import com.yuka.learning.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.UUID;

/**
 * {@link TokenService} backed by HMAC-SHA256 JWTs (jjwt) and database-persisted,
 * hashed refresh tokens.
 */
@Slf4j
@Service
public class JwtTokenService implements TokenService {

    private static final int MIN_SECRET_BYTES = 32;
    private static final int REFRESH_TOKEN_BYTES = 32;
    private static final String CLAIM_USERNAME = "username";

    private final SecretKey signingKey;
    private final JwtParser parser;
    private final String issuer;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtTokenService(AppProperties properties,
                           RefreshTokenMapper refreshTokenMapper,
                           UserMapper userMapper) {
        AppProperties.Security.Jwt jwt = properties.security().jwt();
        byte[] secret = jwt.secret() == null ? new byte[0] : jwt.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.security.jwt.secret must be at least %d bytes — set the JWT_SECRET environment variable"
                            .formatted(MIN_SECRET_BYTES));
        }
        this.signingKey = Keys.hmacShaKeyFor(secret);
        this.parser = Jwts.parser().verifyWith(signingKey).requireIssuer(jwt.issuer()).build();
        this.issuer = jwt.issuer();
        this.accessTokenTtl = jwt.accessTokenTtl();
        this.refreshTokenTtl = jwt.refreshTokenTtl();
        this.refreshTokenMapper = refreshTokenMapper;
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public IssuedTokens issue(User user, ClientInfo client) {
        String rawRefreshToken = newOpaqueToken();
        persistRefreshToken(user.getId(), rawRefreshToken, client);
        return new IssuedTokens(createAccessToken(user), accessTokenTtl.toSeconds(), rawRefreshToken, user);
    }

    /**
     * {@code noRollbackFor}: revocations performed while rejecting a token (reuse
     * detection, expiry) must survive the thrown BusinessException.
     */
    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens rotate(String rawRefreshToken, ClientInfo client) {
        LocalDateTime now = LocalDateTime.now();
        RefreshToken current = refreshTokenMapper.findByTokenHash(sha256Hex(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        if (current.isRevoked()) {
            // A rotated token was presented again — assume theft, kill the whole session chain.
            log.warn("Refresh token reuse detected for user {}", current.getUserId());
            refreshTokenMapper.revokeAllByUserId(current.getUserId(), now);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED);
        }
        if (current.isExpired(now)) {
            markRevoked(current, now, null);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        User user = userMapper.selectById(current.getUserId());
        if (user == null) {
            markRevoked(current, now, null);
            throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            refreshTokenMapper.revokeAllByUserId(user.getId(), now);
            throw new BusinessException(user.getStatus() == UserStatus.LOCKED
                    ? AuthErrorCode.ACCOUNT_LOCKED
                    : AuthErrorCode.ACCOUNT_DISABLED);
        }

        String nextRawToken = newOpaqueToken();
        RefreshToken next = persistRefreshToken(user.getId(), nextRawToken, client);
        markRevoked(current, now, next.getId());
        return new IssuedTokens(createAccessToken(user), accessTokenTtl.toSeconds(), nextRawToken, user);
    }

    @Override
    @Transactional
    public void revoke(String rawRefreshToken) {
        refreshTokenMapper.findByTokenHash(sha256Hex(rawRefreshToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> markRevoked(token, LocalDateTime.now(), null));
    }

    @Override
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenMapper.revokeAllByUserId(userId, LocalDateTime.now());
    }

    @Override
    public AccessTokenClaims parseAccessToken(String accessToken) {
        try {
            Claims claims = parser.parseSignedClaims(accessToken).getPayload();
            return new AccessTokenClaims(Long.parseLong(claims.getSubject()),
                    claims.get(CLAIM_USERNAME, String.class));
        } catch (ExpiredJwtException ex) {
            throw new BusinessException(AuthErrorCode.TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new BusinessException(AuthErrorCode.TOKEN_INVALID);
        }
    }

    private String createAccessToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    private RefreshToken persistRefreshToken(Long userId, String rawToken, ClientInfo client) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(sha256Hex(rawToken));
        token.setExpiresAt(LocalDateTime.now().plus(refreshTokenTtl));
        token.setClientIp(client.ip());
        token.setUserAgent(client.userAgent());
        refreshTokenMapper.insert(token);
        return token;
    }

    private void markRevoked(RefreshToken token, LocalDateTime now, Long replacedById) {
        token.setRevokedAt(now);
        token.setReplacedById(replacedById);
        refreshTokenMapper.updateById(token);
    }

    private String newOpaqueToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
