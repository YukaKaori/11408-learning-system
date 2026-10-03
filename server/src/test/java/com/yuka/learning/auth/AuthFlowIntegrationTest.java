package com.yuka.learning.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yuka.learning.auth.entity.RefreshToken;
import com.yuka.learning.auth.mapper.RefreshTokenMapper;
import com.yuka.learning.user.entity.User;
import com.yuka.learning.user.entity.UserStatus;
import com.yuka.learning.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end auth lifecycle against the real security filter chain:
 * login → protected API → refresh (rotation + reuse detection) → logout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "Secret-123456";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private RefreshTokenMapper refreshTokenMapper;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User user;

    @BeforeEach
    void setUp() {
        // Physical deletes — mapper.delete() would only soft-delete and leave
        // rows behind that collide with the unique username/email indexes.
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM users");

        user = new User();
        user.setUsername("ada");
        user.setEmail("ada@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setNickname("Ada");
        user.setStatus(UserStatus.ACTIVE);
        userMapper.insert(user);
    }

    @Test
    void fullLifecycle_login_me_refresh_logout() throws Exception {
        // Login with username.
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail": "ada", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("ada"))
                .andExpect(jsonPath("$.data.user.id").value(String.valueOf(user.getId())))
                .andReturn();
        String accessToken = json(loginResult, "$.data.accessToken");
        String refreshToken = json(loginResult, "$.data.refreshToken");

        // Last-login audit fields were recorded.
        User reloaded = userMapper.selectById(user.getId());
        assertThat(reloaded.getLastLoginAt()).isNotNull();
        assertThat(reloaded.getLastLoginIp()).isNotBlank();

        // Protected endpoint with the access token.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("ada@example.com"));

        // Rotation: new pair issued, old refresh token revoked.
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn();
        String rotatedRefreshToken = json(refreshResult, "$.data.refreshToken");
        assertThat(rotatedRefreshToken).isNotEqualTo(refreshToken);

        // Reusing the rotated (revoked) token trips reuse detection and kills the chain.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_REUSED.code()));
        assertThat(refreshTokenMapper.selectList(new LambdaQueryWrapper<RefreshToken>()
                .eq(RefreshToken::getUserId, user.getId())
                .isNull(RefreshToken::getRevokedAt))).isEmpty();
    }

    @Test
    void logout_revokesRefreshToken() throws Exception {
        MvcResult loginResult = login("ada@example.com");
        String refreshToken = json(loginResult, "$.data.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}""".formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_REUSED.code()));
    }

    @Test
    void login_withWrongPassword_returnsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail": "ada", "password": "wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.code()));
    }

    @Test
    void login_withUnknownUser_returnsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail": "nobody", "password": "whatever-123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_CREDENTIALS.code()));
    }

    @Test
    void login_withLockedAccount_returnsAccountLocked() throws Exception {
        user.setStatus(UserStatus.LOCKED);
        userMapper.updateById(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail": "ada", "password": "%s"}""".formatted(PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.ACCOUNT_LOCKED.code()));
    }

    @Test
    void protectedEndpoint_withoutToken_returnsUnauthorizedEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void protectedEndpoint_withGarbageToken_returnsTokenInvalid() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.TOKEN_INVALID.code()));
    }

    @Test
    void refresh_withUnknownToken_returnsInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "definitely-not-issued"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.REFRESH_TOKEN_INVALID.code()));
    }

    @Test
    void profileUpdate_appliesPartialFieldsAndClearSentinel() throws Exception {
        MvcResult loginResult = login("ada");
        String accessToken = json(loginResult, "$.data.accessToken");

        // /me now exposes createdAt (member since) as epoch millis.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdAt").isNumber());

        // Update nickname only — avatar untouched.
        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": "Countess"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("Countess"));

        // Blank clears back to null, and the change is persisted.
        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nickname": ""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").doesNotExist());
        assertThat(userMapper.selectById(user.getId()).getNickname()).isNull();
    }

    @Test
    void publicEndpoint_staysReachableWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private MvcResult login(String identifier) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"usernameOrEmail": "%s", "password": "%s"}""".formatted(identifier, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private static String json(MvcResult result, String jsonPath) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), jsonPath);
    }
}
