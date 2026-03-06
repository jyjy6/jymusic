package jymusic.jym_api_gateway.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JwtValidator 단위 테스트")
class JwtValidatorTest {

    private JwtValidator jwtValidator;

    /** 토큰 서명용 — 공개키와 쌍을 이루는 개인키 */
    private PrivateKey signingKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        signingKey = kp.getPrivate();
        Resource publicKeyResource = toPemResource("PUBLIC KEY", kp.getPublic().getEncoded());
        jwtValidator = new JwtValidator(publicKeyResource);
    }

    /**
     * 바이트 배열 키를 PEM 형식의 ByteArrayResource로 변환합니다.
     * JwtValidator 생성자가 Resource의 InputStream으로 PEM을 읽으므로
     * 실제 파일 없이 단위 테스트가 가능합니다.
     */
    private Resource toPemResource(String type, byte[] keyBytes) {
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyBytes)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }

    /** 지정된 만료 시간으로 테스트용 JWT를 생성합니다. */
    private String buildToken(long expiryOffsetMillis) {
        return Jwts.builder()
                .subject("testuser")
                .claim("userId", 1L)
                .claim("role", "ROLE_USER")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryOffsetMillis))
                .signWith(signingKey)
                .compact();
    }

    // ── validateToken() ─────────────────────────────────────────────

    @Nested
    @DisplayName("validateToken()")
    class ValidateToken {

        @Test
        @DisplayName("JV-01: 유효한 토큰 → true 반환")
        void validateToken_validToken_returnsTrue() {
            String token = buildToken(30 * 60 * 1000L);

            assertThat(jwtValidator.validateToken(token)).isTrue();
        }

        @Test
        @DisplayName("JV-02: 만료된 토큰 → false 반환")
        void validateToken_expiredToken_returnsFalse() {
            // 과거 시간으로 만료 설정
            String token = Jwts.builder()
                    .subject("testuser")
                    .issuedAt(new Date(System.currentTimeMillis() - 60_000))
                    .expiration(new Date(System.currentTimeMillis() - 1))
                    .signWith(signingKey)
                    .compact();

            assertThat(jwtValidator.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("JV-03: 다른 RSA 키쌍으로 서명된 토큰 → false 반환")
        void validateToken_differentKeyToken_returnsFalse() throws Exception {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            PrivateKey differentPrivateKey = kpg.generateKeyPair().getPrivate();

            String token = Jwts.builder()
                    .subject("testuser")
                    .issuedAt(new Date())
                    .expiration(new Date(System.currentTimeMillis() + 60_000))
                    .signWith(differentPrivateKey)
                    .compact();

            assertThat(jwtValidator.validateToken(token)).isFalse();
        }

        @Test
        @DisplayName("JV-04: 위·변조된 토큰 문자열 → false 반환")
        void validateToken_tamperedToken_returnsFalse() {
            String valid = buildToken(30 * 60 * 1000L);
            String tampered = valid.substring(0, valid.length() - 5) + "XXXXX";

            assertThat(jwtValidator.validateToken(tampered)).isFalse();
        }

        @Test
        @DisplayName("JV-05: null → false 반환")
        void validateToken_nullToken_returnsFalse() {
            assertThat(jwtValidator.validateToken(null)).isFalse();
        }

        @Test
        @DisplayName("JV-05: 빈 문자열 → false 반환")
        void validateToken_blankToken_returnsFalse() {
            assertThat(jwtValidator.validateToken("")).isFalse();
        }
    }

    // ── getClaims() ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getClaims()")
    class GetClaims {

        @Test
        @DisplayName("JC-01: 유효한 토큰 → claims 정확히 추출")
        void getClaims_validToken_returnsCorrectClaims() {
            String token = buildToken(30 * 60 * 1000L);

            Claims claims = jwtValidator.getClaims(token);

            assertThat(claims.getSubject()).isEqualTo("testuser");
            assertThat(claims.get("userId", Long.class)).isEqualTo(1L);
            assertThat(claims.get("role", String.class)).isEqualTo("ROLE_USER");
        }

        @Test
        @DisplayName("JC-02: 유효하지 않은 토큰 → 예외 발생")
        void getClaims_invalidToken_throwsException() {
            assertThatThrownBy(() -> jwtValidator.getClaims("not.a.jwt"))
                    .isInstanceOf(Exception.class);
        }
    }
}
