package jymusic.jym_api_gateway.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityConfig의 CORS 설정을 직접 단위 테스트합니다.
 * Spring 컨텍스트 불필요 — CorsConfigurationSource 빈을 직접 생성하여 검증합니다.
 */
@DisplayName("CORS 설정 단위 테스트")
class CorsConfigTest {

    private CorsConfigurationSource corsConfigSource;

    @BeforeEach
    void setUp() {
        SecurityConfig config = new SecurityConfig();
        corsConfigSource = config.corsConfigurationSource();
    }

    private CorsConfiguration getConfig() {
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/v1/auth/login").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        return corsConfigSource.getCorsConfiguration(exchange);
    }

    // ── 허용 Origin ────────────────────────────────────────────────

    @Nested
    @DisplayName("허용 Origin 검증")
    class AllowedOrigins {

        @Test
        @DisplayName("CO-01: Nuxt 개발 서버(http://localhost:3000)가 허용 Origin에 포함됨")
        void nuxtDevServerOrigin_isAllowed() {
            CorsConfiguration config = getConfig();

            assertThat(config.getAllowedOrigins())
                    .isNotNull()
                    .contains("http://localhost:3000");
        }

        @Test
        @DisplayName("CO-02: 허용되지 않은 Origin은 목록에 없음")
        void unknownOrigin_isNotAllowed() {
            CorsConfiguration config = getConfig();

            assertThat(config.getAllowedOrigins())
                    .doesNotContain("http://evil.com");
        }
    }

    // ── HTTP 메서드 ─────────────────────────────────────────────────

    @Nested
    @DisplayName("허용 HTTP 메서드 검증")
    class AllowedMethods {

        @Test
        @DisplayName("CO-04: REST CRUD 메서드(GET, POST, PUT, PATCH, DELETE, OPTIONS)가 모두 허용됨")
        void allRestMethods_areAllowed() {
            CorsConfiguration config = getConfig();

            assertThat(config.getAllowedMethods())
                    .containsExactlyInAnyOrder("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        }
    }

    // ── Credentials ────────────────────────────────────────────────

    @Nested
    @DisplayName("Credentials 설정 검증")
    class Credentials {

        @Test
        @DisplayName("CO-03: allowCredentials=true — 쿠키(refreshToken) 전송 허용")
        void allowCredentials_isTrue() {
            CorsConfiguration config = getConfig();

            assertThat(config.getAllowCredentials()).isTrue();
        }
    }

    // ── 헤더 ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("허용 헤더 검증")
    class Headers {

        @Test
        @DisplayName("모든 요청 헤더 허용 (*)")
        void allRequestHeaders_areAllowed() {
            CorsConfiguration config = getConfig();

            assertThat(config.getAllowedHeaders()).contains("*");
        }

        @Test
        @DisplayName("Authorization 헤더가 응답 노출 헤더(exposedHeaders)에 포함됨")
        void authorizationHeader_isExposed() {
            CorsConfiguration config = getConfig();

            assertThat(config.getExposedHeaders()).contains("Authorization");
        }
    }

    // ── MaxAge ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("Preflight 캐싱 시간 검증")
    class MaxAge {

        @Test
        @DisplayName("maxAge=3600 — Preflight 결과를 1시간 캐싱")
        void maxAge_isOneHour() {
            CorsConfiguration config = getConfig();

            assertThat(config.getMaxAge()).isEqualTo(3600L);
        }
    }
}
