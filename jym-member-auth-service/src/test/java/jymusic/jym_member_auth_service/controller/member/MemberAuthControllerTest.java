package jymusic.jym_member_auth_service.controller.member;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.SpringSecurityConfig;
import jymusic.jym_member_auth_service.domain.member.Role;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.service.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemberAuthController.class)
@Import(SpringSecurityConfig.class)
@DisplayName("MemberAuthController 단위 테스트")
class MemberAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    // Spring Boot 4.x @WebMvcTest 슬라이스는 ObjectMapper 빈을 포함하지 않으므로 직접 생성
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── 공통 픽스처 ─────────────────────────────────────────────────

    private MemberProfileResponse sampleProfileResponse() {
        return MemberProfileResponse.builder()
                .id(1L)
                .username("testuser")
                .nickname("테스트유저")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();
    }

    // ── POST /api/v1/auth/register ───────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/register")
    class Register {

        private static final String VALID_REQUEST_JSON = """
                {
                  "username": "testuser",
                  "password": "pass1234",
                  "nickname": "테스트유저",
                  "email": "test@example.com"
                }
                """;

        @Test
        @DisplayName("R-01: 유효한 요청 → 201 Created + MemberProfileResponse")
        void register_validRequest_returns201WithProfile() throws Exception {
            given(memberService.register(any())).willReturn(sampleProfileResponse());

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value("testuser"))
                    .andExpect(jsonPath("$.nickname").value("테스트유저"))
                    .andExpect(jsonPath("$.email").value("test@example.com"));
        }

        @Test
        @DisplayName("R-02: 중복 username → 409 Conflict + ERR_DUPLICATE_USERNAME")
        void register_duplicateUsername_returns409() throws Exception {
            given(memberService.register(any()))
                    .willThrow(new GlobalException(
                            "이미 사용 중인 아이디입니다.", "ERR_DUPLICATE_USERNAME", HttpStatus.CONFLICT));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ERR_DUPLICATE_USERNAME"));
        }

        @Test
        @DisplayName("R-03: 중복 email → 409 Conflict + ERR_DUPLICATE_EMAIL")
        void register_duplicateEmail_returns409() throws Exception {
            given(memberService.register(any()))
                    .willThrow(new GlobalException(
                            "이미 등록된 이메일입니다.", "ERR_DUPLICATE_EMAIL", HttpStatus.CONFLICT));

            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_REQUEST_JSON))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ERR_DUPLICATE_EMAIL"));
        }

        @Test
        @DisplayName("R-04: nickname 누락 → 400 Bad Request")
        void register_missingNickname_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser", "password": "pass1234"}
                                    """))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("R-05: username 누락 → 400 Bad Request")
        void register_missingUsername_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"password": "pass1234", "nickname": "테스트유저"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/v1/auth/login ──────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        private static final String VALID_LOGIN_JSON = """
                {"username": "testuser", "password": "pass1234"}
                """;

        @Test
        @DisplayName("L-01: 유효한 자격증명 → 200 OK + accessToken 바디 + refreshToken HttpOnly 쿠키")
        void login_validCredentials_returns200WithTokenAndCookie() throws Exception {
            given(memberService.login(any()))
                    .willReturn(Map.of(
                            "accessToken", "mock.access.token",
                            "refreshToken", "mock.refresh.token"));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_LOGIN_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("mock.access.token"))
                    .andExpect(cookie().value("refreshToken", "mock.refresh.token"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }

        @Test
        @DisplayName("L-02: 잘못된 비밀번호 → 401 Unauthorized + ERR_LOGIN_FAILED")
        void login_wrongPassword_returns401() throws Exception {
            given(memberService.login(any()))
                    .willThrow(new GlobalException(
                            "아이디 또는 비밀번호가 올바르지 않습니다.", "ERR_LOGIN_FAILED", HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_LOGIN_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR_LOGIN_FAILED"));
        }

        @Test
        @DisplayName("L-03: 존재하지 않는 username → 401 Unauthorized + ERR_LOGIN_FAILED")
        void login_unknownUsername_returns401() throws Exception {
            given(memberService.login(any()))
                    .willThrow(new GlobalException(
                            "아이디 또는 비밀번호가 올바르지 않습니다.", "ERR_LOGIN_FAILED", HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_LOGIN_JSON))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR_LOGIN_FAILED"));
        }

        @Test
        @DisplayName("L-05: password 필드 누락 → 400 Bad Request")
        void login_missingPassword_returns400() throws Exception {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username": "testuser"}
                                    """))
                    .andExpect(status().isBadRequest());
        }
    }

    // ── POST /api/v1/auth/refresh-token ─────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/refresh-token")
    class RefreshToken {

        @Test
        @DisplayName("RF-01: 유효한 refreshToken 쿠키 → 200 OK + 새 accessToken + 새 refreshToken 쿠키")
        void refreshToken_validCookie_returns200WithNewTokens() throws Exception {
            given(memberService.refreshTokens("valid.refresh.token"))
                    .willReturn(Map.of(
                            "accessToken", "new.access.token",
                            "refreshToken", "new.refresh.token"));

            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .cookie(new Cookie("refreshToken", "valid.refresh.token")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accessToken").value("new.access.token"))
                    .andExpect(cookie().value("refreshToken", "new.refresh.token"));
        }

        @Test
        @DisplayName("RF-02: refreshToken 쿠키 없음 → 401 Unauthorized + ERR_MISSING_REFRESH_TOKEN")
        void refreshToken_missingCookie_returns401() throws Exception {
            mockMvc.perform(post("/api/v1/auth/refresh-token"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR_MISSING_REFRESH_TOKEN"));
        }

        @Test
        @DisplayName("RF-03: 위·변조된 refreshToken → 401 Unauthorized + ERR_INVALID_REFRESH_TOKEN")
        void refreshToken_tamperedToken_returns401() throws Exception {
            given(memberService.refreshTokens(anyString()))
                    .willThrow(new GlobalException(
                            "유효하지 않거나 만료된 리프레시 토큰입니다.", "ERR_INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post("/api/v1/auth/refresh-token")
                            .cookie(new Cookie("refreshToken", "tampered.token")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR_INVALID_REFRESH_TOKEN"));
        }
    }

    // ── POST /api/v1/auth/logout ─────────────────────────────────────

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("LO-01: 정상 로그아웃 → 200 OK + refreshToken 쿠키 만료(maxAge=0)")
        void logout_withCookie_returns200AndExpiresCookie() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout")
                            .cookie(new Cookie("refreshToken", "valid.refresh.token")))
                    .andExpect(status().isOk())
                    .andExpect(cookie().maxAge("refreshToken", 0));
        }

        @Test
        @DisplayName("LO-02: 쿠키 없이 로그아웃 → 200 OK (토큰 없어도 정상 처리)")
        void logout_withoutCookie_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk());
        }
    }
}
