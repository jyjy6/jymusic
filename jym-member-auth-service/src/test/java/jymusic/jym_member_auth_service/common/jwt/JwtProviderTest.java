package jymusic.jym_member_auth_service.common.jwt;

import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtProvider 단위 테스트")
class JwtProviderTest {

    private JwtProvider jwtProvider;

    @Mock
    private RedisService redisService;

    private static final Long   USER_ID   = 1L;
    private static final String USERNAME  = "testuser";
    private static final String ROLE      = "ROLE_USER";
    private static final String NICKNAME  = "테스트유저";

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        Resource privateKeyResource = toPemResource("PRIVATE KEY", kp.getPrivate().getEncoded());
        Resource publicKeyResource  = toPemResource("PUBLIC KEY",  kp.getPublic().getEncoded());

        jwtProvider = new JwtProvider(redisService, privateKeyResource, publicKeyResource);
    }

    /**
     * 바이트 배열 키를 PEM 형식의 ByteArrayResource로 변환합니다.
     * JwtProvider 생성자가 Resource의 InputStream을 통해 PEM을 읽으므로
     * 실제 파일 없이도 단위 테스트가 가능합니다.
     */
    private Resource toPemResource(String type, byte[] keyBytes) {
        String pem = "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.UTF_8)).encodeToString(keyBytes)
                + "\n-----END " + type + "-----\n";
        return new ByteArrayResource(pem.getBytes(StandardCharsets.UTF_8));
    }

    // ── createAccessToken() ─────────────────────────────────────────

    @Nested
    @DisplayName("createAccessToken()")
    class CreateAccessToken {

        @Test
        @DisplayName("J-01: Access Token 생성 → 빈 문자열이 아닌 JWT 반환")
        void createAccessToken_returnsNonBlankJwt() {
            String token = jwtProvider.createAccessToken(USER_ID, USERNAME, ROLE, NICKNAME);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
        }

        @Test
        @DisplayName("J-02: Access Token claims 검증 → sub=username, userId, role 포함")
        void createAccessToken_containsCorrectClaims() {
            String token = jwtProvider.createAccessToken(USER_ID, USERNAME, ROLE, NICKNAME);

            // extractUsername은 publicKey로 서명 검증 후 subject를 반환함
            String subject = jwtProvider.extractUsername(token);
            assertThat(subject).isEqualTo(USERNAME);
        }
    }

    // ── createRefreshToken() ────────────────────────────────────────

    @Nested
    @DisplayName("createRefreshToken()")
    class CreateRefreshToken {

        @Test
        @DisplayName("J-04: Refresh Token 생성 → 빈 문자열이 아닌 토큰 반환")
        void createRefreshToken_returnsNonBlankToken() {
            String token = jwtProvider.createRefreshToken(USERNAME);

            assertThat(token).isNotBlank();
            assertThat(token.split("\\.")).hasSize(3);
        }

        @Test
        @DisplayName("J-05: Refresh Token 생성 시 Redis에 RT:{username} 키로 저장됨")
        void createRefreshToken_storesTokenInRedis() {
            jwtProvider.createRefreshToken(USERNAME);

            verify(redisService).setValue(
                    eq("RT:" + USERNAME),
                    anyString(),
                    eq(Duration.ofMillis(7 * 24 * 60 * 60 * 1000L))
            );
        }
    }

    // ── extractUsername() ───────────────────────────────────────────

    @Nested
    @DisplayName("extractUsername()")
    class ExtractUsername {

        @Test
        @DisplayName("J-03: 유효한 토큰 → username(subject) 정상 추출")
        void extractUsername_validToken_returnsUsername() {
            String token = jwtProvider.createAccessToken(USER_ID, USERNAME, ROLE, NICKNAME);

            String extracted = jwtProvider.extractUsername(token);

            assertThat(extracted).isEqualTo(USERNAME);
        }

        @Test
        @DisplayName("위·변조된 토큰 → GlobalException(UNAUTHORIZED) 발생")
        void extractUsername_tamperedToken_throwsUnauthorized() {
            String validToken = jwtProvider.createAccessToken(USER_ID, USERNAME, ROLE, NICKNAME);
            String tampered = validToken.substring(0, validToken.length() - 5) + "XXXXX";

            assertThatThrownBy(() -> jwtProvider.extractUsername(tampered))
                    .isInstanceOf(GlobalException.class);
        }
    }

    // ── rotateRefreshToken() ────────────────────────────────────────

    @Nested
    @DisplayName("rotateRefreshToken()")
    class RotateRefreshToken {

        @Test
        @DisplayName("RD-05: Redis에 저장된 토큰과 일치 → 새 Refresh Token이 Redis에 저장됨")
        void rotateRefreshToken_matchingToken_savesNewTokenToRedis() {
            String oldToken = jwtProvider.createRefreshToken(USERNAME);
            given(redisService.getValue("RT:" + USERNAME)).willReturn(oldToken);

            String newToken = jwtProvider.rotateRefreshToken(oldToken, USERNAME);

            assertThat(newToken).isNotBlank();
            // 최초 createRefreshToken(setUp) + rotate 내부 createRefreshToken = 총 2회 setValue 호출
            verify(redisService, times(2)).setValue(
                    eq("RT:" + USERNAME),
                    anyString(),
                    eq(Duration.ofMillis(7 * 24 * 60 * 60 * 1000L))
            );
        }

        @Test
        @DisplayName("Redis 저장 토큰과 불일치 → GlobalException(UNAUTHORIZED) + RT 즉시 삭제")
        void rotateRefreshToken_mismatchedToken_throwsAndDeletesToken() {
            String oldToken = jwtProvider.createRefreshToken(USERNAME);
            given(redisService.getValue("RT:" + USERNAME)).willReturn("different.token.in.redis");

            assertThatThrownBy(() -> jwtProvider.rotateRefreshToken(oldToken, USERNAME))
                    .isInstanceOf(GlobalException.class);

            verify(redisService).deleteValue("RT:" + USERNAME);
        }
    }

    // ── deleteRefreshToken() ────────────────────────────────────────

    @Nested
    @DisplayName("deleteRefreshToken()")
    class DeleteRefreshToken {

        @Test
        @DisplayName("LO-01: deleteRefreshToken() → Redis에서 RT:{username} 삭제")
        void deleteRefreshToken_deletesRedisKey() {
            jwtProvider.deleteRefreshToken(USERNAME);

            verify(redisService).deleteValue("RT:" + USERNAME);
        }
    }
}
