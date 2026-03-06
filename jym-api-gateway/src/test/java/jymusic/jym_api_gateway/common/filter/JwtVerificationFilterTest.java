package jymusic.jym_api_gateway.common.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import jymusic.jym_api_gateway.common.jwt.JwtValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtVerificationFilter 단위 테스트")
class JwtVerificationFilterTest {

    @InjectMocks
    private JwtVerificationFilter filter;

    @Mock
    private JwtValidator jwtValidator;

    private MockHttpServletResponse response;
    private MockFilterChain chain;

    @BeforeEach
    void setUp() {
        response = new MockHttpServletResponse();
        chain   = new MockFilterChain();
    }

    // ── shouldNotFilter() — 경로 제외 로직 ──────────────────────────
    //
    // shouldNotFilter()는 protected이므로 직접 호출 불가.
    // 제외 경로에서 Authorization 헤더 없이 요청 시 → 필터가 건너뜀 → 401이 아닌 200(chain 통과)
    // 비제외 경로에서 Authorization 헤더 없이 요청 시 → 필터가 동작 → 401 반환

    @Nested
    @DisplayName("shouldNotFilter() — 경로 제외 로직")
    class ShouldNotFilter {

        @ParameterizedTest(name = "제외 경로: {0} → 필터 건너뜀")
        @ValueSource(strings = {
                "/api/v1/auth/login",
                "/api/v1/auth/register",
                "/api/v1/auth/refresh-token",
                "/api/v1/auth/logout",
                "/swagger-ui/index.html",
                "/swagger-ui/swagger-ui.css",
                "/v3/api-docs/swagger-config"
        })
        @DisplayName("SF-01~04: 공개 경로 → Authorization 없어도 chain 통과")
        void excludedPaths_noAuthHeader_chainPasses(String path) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            filter.doFilter(request, response, chain);

            // 필터가 건너뛰어졌으면 chain.doFilter()가 호출되어 request가 저장됨
            assertThat(chain.getRequest()).isNotNull();
            assertThat(response.getStatus()).isNotEqualTo(401);
        }

        @ParameterizedTest(name = "보호 경로: {0} → Authorization 없으면 401")
        @ValueSource(strings = {
                "/api/v1/members/me",
                "/api/v1/orders",
                "/api/v1/products"
        })
        @DisplayName("SF-05~07: 보호 경로 → Authorization 없으면 필터 적용")
        void protectedPaths_noAuthHeader_returns401(String path) throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull(); // chain이 호출되지 않음
        }
    }

    // ── doFilterInternal() — JWT 검증 및 헤더 주입 ──────────────────

    @Nested
    @DisplayName("doFilterInternal() — JWT 검증 및 헤더 주입")
    class DoFilterInternal {

        @Test
        @DisplayName("FI-01: Authorization 헤더 없음 → 401 + ERR_UNAUTHORIZED")
        void noAuthorizationHeader_returns401() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getContentAsString()).contains("ERR_UNAUTHORIZED");
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("FI-02: Bearer 접두사 없는 헤더 → 401")
        void authHeaderWithoutBearerPrefix_returns401() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
            request.addHeader("Authorization", "Token some-value");

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("FI-03: 유효하지 않은 토큰 → 401")
        void invalidToken_returns401() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
            request.addHeader("Authorization", "Bearer invalid.token.here");
            given(jwtValidator.validateToken("invalid.token.here")).willReturn(false);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(chain.getRequest()).isNull();
        }

        @Test
        @DisplayName("FI-04: 유효한 토큰 → 필터 통과, chain.doFilter() 호출됨")
        void validToken_chainInvoked() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
            request.addHeader("Authorization", "Bearer valid.token");
            given(jwtValidator.validateToken("valid.token")).willReturn(true);
            given(jwtValidator.getClaims("valid.token")).willReturn(buildClaims("1", "testuser", "ROLE_USER"));

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isNotEqualTo(401);
            assertThat(chain.getRequest()).isNotNull();
        }

        @Test
        @DisplayName("FI-05: 유효한 토큰 → X-User-Id, X-User-Name, X-User-Role 헤더가 다운스트림 요청에 주입됨")
        void validToken_userContextHeadersInjected() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/members/me");
            request.addHeader("Authorization", "Bearer valid.token");
            given(jwtValidator.validateToken("valid.token")).willReturn(true);
            given(jwtValidator.getClaims("valid.token")).willReturn(buildClaims("1", "testuser", "ROLE_USER"));

            filter.doFilter(request, response, chain);

            HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
            assertThat(downstream).isNotNull();
            assertThat(downstream.getHeader("X-User-Id")).isEqualTo("1");
            assertThat(downstream.getHeader("X-User-Name")).isEqualTo("testuser");
            assertThat(downstream.getHeader("X-User-Role")).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("FI-05: 헤더 주입 — ROLE_ADMIN 권한도 정확히 전달됨")
        void validToken_adminRole_headerInjectedCorrectly() throws Exception {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
            request.addHeader("Authorization", "Bearer admin.token");
            given(jwtValidator.validateToken("admin.token")).willReturn(true);
            given(jwtValidator.getClaims("admin.token")).willReturn(buildClaims("99", "adminuser", "ROLE_ADMIN"));

            filter.doFilter(request, response, chain);

            HttpServletRequest downstream = (HttpServletRequest) chain.getRequest();
            assertThat(downstream.getHeader("X-User-Id")).isEqualTo("99");
            assertThat(downstream.getHeader("X-User-Name")).isEqualTo("adminuser");
            assertThat(downstream.getHeader("X-User-Role")).isEqualTo("ROLE_ADMIN");
        }
    }

    /**
     * Mockito Claims 반환값을 직접 구성하는 헬퍼.
     * JJWT Claims는 인터페이스이므로 Jwts.claims()로 빌드하거나 Map으로 구성합니다.
     */
    private Claims buildClaims(String userId, String username, String role) {
        return Jwts.claims()
                .subject(username)
                .add("userId", Long.parseLong(userId))
                .add("role", role)
                .build();
    }
}
