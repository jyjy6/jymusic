package jymusic.jym_api_gateway.common.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
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
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtVerificationFilter 단위 테스트")
class JwtVerificationFilterTest {

    @InjectMocks
    private JwtVerificationFilter filter;

    @Mock
    private JwtValidator jwtValidator;

    @Mock
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        // chain.filter() returns Mono.empty() by default when mocked, but explicit is better
        // given(chain.filter(any(ServerWebExchange.class))).willReturn(Mono.empty());
    }

    // ── shouldNotFilter() logic ──────────────────────────────────────

    @Nested
    @DisplayName("경로 제외 로직")
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
        void excludedPaths_noAuthHeader_chainPasses(String path) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);
            
            given(chain.filter(exchange)).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(exchange);
        }

        @ParameterizedTest(name = "보호 경로: {0} → Authorization 없으면 401")
        @ValueSource(strings = {
                "/api/v1/members/me",
                "/api/v1/orders",
                "/api/v1/products"
        })
        @DisplayName("SF-05~07: 보호 경로 → Authorization 없으면 필터 적용 (401)")
        void protectedPaths_noAuthHeader_returns401(String path) {
            MockServerHttpRequest request = MockServerHttpRequest.get(path).build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete(); // Filter completes (writes response)

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any()); // Chain should NOT be called
        }
    }

    // ── filter() logic — JWT verification & Header Injection ─────────

    @Nested
    @DisplayName("JWT 검증 및 헤더 주입")
    class DoFilter {

        @Test
        @DisplayName("FI-01: Authorization 헤더 없음 → 401 + ERR_UNAUTHORIZED")
        void noAuthorizationHeader_returns401() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/members/me").build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            // Verify response body content if needed (requires inspecting DataBuffer)
            // Ideally we check if chain was not called
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("FI-02: Bearer 접두사 없는 헤더 → 401")
        void authHeaderWithoutBearerPrefix_returns401() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/members/me")
                    .header("Authorization", "Token some-value")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("FI-03: 유효하지 않은 토큰 → 401")
        void invalidToken_returns401() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/members/me")
                    .header("Authorization", "Bearer invalid.token.here")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            given(jwtValidator.validateToken("invalid.token.here")).willReturn(false);

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
            verify(chain, never()).filter(any());
        }

        @Test
        @DisplayName("FI-04: 유효한 토큰 → 필터 통과, chain.filter() 호출됨")
        void validToken_chainInvoked() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/members/me")
                    .header("Authorization", "Bearer valid.token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            given(jwtValidator.validateToken("valid.token")).willReturn(true);
            given(jwtValidator.getClaims("valid.token")).willReturn(buildClaims("1", "testuser", "ROLE_USER"));
            given(chain.filter(any())).willReturn(Mono.empty());

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();

            verify(chain).filter(any()); // Argument is a mutated exchange
        }

        @Test
        @DisplayName("FI-05: 유효한 토큰 → X-User-Id, X-User-Name, X-User-Role 헤더가 다운스트림 요청에 주입됨")
        void validToken_userContextHeadersInjected() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/members/me")
                    .header("Authorization", "Bearer valid.token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            given(jwtValidator.validateToken("valid.token")).willReturn(true);
            given(jwtValidator.getClaims("valid.token")).willReturn(buildClaims("1", "testuser", "ROLE_USER"));
            given(chain.filter(any())).willAnswer(invocation -> {
                MockServerWebExchange mutatedExchange = invocation.getArgument(0);
                ServerHttpRequest downstream = mutatedExchange.getRequest();
                
                assertThat(downstream.getHeaders().getFirst("X-User-Id")).isEqualTo("1");
                assertThat(downstream.getHeaders().getFirst("X-User-Name")).isEqualTo("testuser");
                assertThat(downstream.getHeaders().getFirst("X-User-Role")).isEqualTo("ROLE_USER");
                
                return Mono.empty();
            });

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            verify(chain).filter(any());
        }

        @Test
        @DisplayName("FI-05: 헤더 주입 — ROLE_ADMIN 권한도 정확히 전달됨")
        void validToken_adminRole_headerInjectedCorrectly() {
            MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/orders")
                    .header("Authorization", "Bearer admin.token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(request);

            given(jwtValidator.validateToken("admin.token")).willReturn(true);
            given(jwtValidator.getClaims("admin.token")).willReturn(buildClaims("99", "adminuser", "ROLE_ADMIN"));
            
            given(chain.filter(any())).willAnswer(invocation -> {
                MockServerWebExchange mutatedExchange = invocation.getArgument(0);
                ServerHttpRequest downstream = mutatedExchange.getRequest();

                assertThat(downstream.getHeaders().getFirst("X-User-Id")).isEqualTo("99");
                assertThat(downstream.getHeaders().getFirst("X-User-Name")).isEqualTo("adminuser");
                assertThat(downstream.getHeaders().getFirst("X-User-Role")).isEqualTo("ROLE_ADMIN");

                return Mono.empty();
            });

            StepVerifier.create(filter.filter(exchange, chain))
                    .verifyComplete();
            
            verify(chain).filter(any());
        }
    }

    private Claims buildClaims(String userId, String username, String role) {
        return Jwts.claims()
                .subject(username)
                .add("userId", Long.parseLong(userId))
                .add("role", role)
                .build();
    }
}
