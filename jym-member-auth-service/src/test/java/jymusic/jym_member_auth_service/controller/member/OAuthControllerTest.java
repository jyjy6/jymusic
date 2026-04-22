package jymusic.jym_member_auth_service.controller.member;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.config.AppProperties;
import jymusic.jym_member_auth_service.config.SpringSecurityConfig;
import jymusic.jym_member_auth_service.service.member.OAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OAuthController.class)
@Import({SpringSecurityConfig.class, OAuthControllerTest.TestConfig.class})
@DisplayName("OAuthController 단위 테스트")
class OAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuthService oauthService;

    @Autowired
    private AppProperties appProperties;

    @BeforeEach
    void setUp() {
        appProperties.setFrontBaseUrl("http://localhost:3000");
    }

    // ── GET /api/v1/auth/oauth2/{provider} ──────────────────────────

    @Nested
    @DisplayName("GET /api/v1/auth/oauth2/{provider}")
    class StartAuthorization {

        @Test
        @DisplayName("OA-01: google → 302 Found + Google 인가 URL Location 헤더")
        void startAuthorization_google_returns302WithGoogleUrl() throws Exception {
            given(oauthService.buildAuthorizationUrl("google"))
                    .willReturn("https://accounts.google.com/o/oauth2/v2/auth?client_id=x&state=s");

            mockMvc.perform(get("/api/v1/auth/oauth2/google"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", startsWith("https://accounts.google.com/")));
        }

        @Test
        @DisplayName("OA-02: kakao → 302 Found + Kakao 인가 URL Location 헤더")
        void startAuthorization_kakao_returns302WithKakaoUrl() throws Exception {
            given(oauthService.buildAuthorizationUrl("kakao"))
                    .willReturn("https://kauth.kakao.com/oauth/authorize?client_id=x&state=s");

            mockMvc.perform(get("/api/v1/auth/oauth2/kakao"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location", startsWith("https://kauth.kakao.com/")));
        }

        @Test
        @DisplayName("OA-03: 미지원 provider(naver) → 400 Bad Request + ERR_UNSUPPORTED_PROVIDER")
        void startAuthorization_unsupportedProvider_returns400() throws Exception {
            given(oauthService.buildAuthorizationUrl("naver"))
                    .willThrow(new GlobalException(
                            "지원하지 않는 OAuth Provider 입니다: naver",
                            "ERR_UNSUPPORTED_PROVIDER",
                            HttpStatus.BAD_REQUEST));

            mockMvc.perform(get("/api/v1/auth/oauth2/naver"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("ERR_UNSUPPORTED_PROVIDER"));
        }
    }

    // ── GET /api/v1/auth/oauth2/callback/{provider} ─────────────────

    @Nested
    @DisplayName("GET /api/v1/auth/oauth2/callback/{provider}")
    class Callback {

        @Test
        @DisplayName("OC-01: 정상 콜백 → 302 Found + 프론트 success URL + refreshToken HttpOnly 쿠키")
        void callback_success_returns302WithFrontUrlAndCookie() throws Exception {
            given(oauthService.processCallback(eq("google"), eq("auth-code"), eq("state-xyz")))
                    .willReturn(Map.of(
                            "accessToken", "mock.access.token",
                            "refreshToken", "mock.refresh.token"));

            mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                            .param("code", "auth-code")
                            .param("state", "state-xyz"))
                    .andExpect(status().isFound())
                    .andExpect(header().string("Location",
                            startsWith("http://localhost:3000/auth/oauth2/success?accessToken=")))
                    .andExpect(header().string("Location", containsString("mock.access.token")))
                    .andExpect(cookie().value("refreshToken", "mock.refresh.token"))
                    .andExpect(cookie().httpOnly("refreshToken", true));
        }

        @Test
        @DisplayName("OC-03: State 불일치 → 401 Unauthorized + ERR_OAUTH_INVALID_STATE")
        void callback_invalidState_returns401() throws Exception {
            given(oauthService.processCallback(eq("google"), eq("auth-code"), anyString()))
                    .willThrow(new GlobalException(
                            "유효하지 않거나 만료된 state 입니다.",
                            "ERR_OAUTH_INVALID_STATE",
                            HttpStatus.UNAUTHORIZED));

            mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                            .param("code", "auth-code")
                            .param("state", "wrong-state"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("ERR_OAUTH_INVALID_STATE"));
        }

        @Test
        @DisplayName("OC-04: code 누락 → 400 Bad Request")
        void callback_missingCode_returns400() throws Exception {
            mockMvc.perform(get("/api/v1/auth/oauth2/callback/google")
                            .param("state", "state-xyz"))
                    .andExpect(status().isBadRequest());
        }
    }

    /** @WebMvcTest 슬라이스에서 @ConfigurationProperties 대신 수동 Bean 등록. */
    @TestConfiguration
    static class TestConfig {
        @Bean
        public AppProperties appProperties() {
            return new AppProperties();
        }
    }
}
