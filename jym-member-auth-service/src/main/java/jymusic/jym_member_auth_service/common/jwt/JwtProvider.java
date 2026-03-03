package jymusic.jym_member_auth_service.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.common.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;

@Slf4j
@Component
public class JwtProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final RedisService redisService;

    private static final long ACCESS_TOKEN_VALIDITY  = 30 * 60 * 1000L;           // 30분
    private static final long REFRESH_TOKEN_VALIDITY = 7 * 24 * 60 * 60 * 1000L; // 7일

    public JwtProvider(
            RedisService redisService,
            @Value("${jwt.private-key-path}") Resource privateKeyResource,
            @Value("${jwt.public-key-path}")  Resource publicKeyResource
    ) throws Exception {
        this.redisService = redisService;
        this.privateKey   = loadPrivateKey(privateKeyResource);
        this.publicKey    = loadPublicKey(publicKeyResource);
    }

    // ── 키 로딩 ──────────────────────────────────────────────────────

    private PrivateKey loadPrivateKey(Resource resource) throws Exception {
        String pem = readPem(resource)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(decoded));
    }

    private PublicKey loadPublicKey(Resource resource) throws Exception {
        String pem = readPem(resource)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] decoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(decoded));
    }

    private String readPem(Resource resource) throws Exception {
        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
    }

    // ── 토큰 생성 ─────────────────────────────────────────────────────

    public String createAccessToken(Long userId, String username, String role, String nickname) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .claim("role", role)
                .claim("nickname", nickname)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(privateKey)
                .compact();
    }

    public String createRefreshToken(String username) {
        String token = Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY))
                .signWith(privateKey)
                .compact();

        redisService.setValue("RT:" + username, token, Duration.ofMillis(REFRESH_TOKEN_VALIDITY));
        return token;
    }

    // ── 토큰 파싱 ─────────────────────────────────────────────────────

    /**
     * 토큰에서 username(subject)을 추출합니다.
     * RS256 서명을 공개키로 검증하므로, 위·변조된 토큰은 예외가 발생합니다.
     */
    public String extractUsername(String token) {
        return getClaims(token).getSubject();
    }

    private Claims getClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(publicKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("JWT 파싱 실패: {}", e.getMessage());
            throw new GlobalException("유효하지 않은 토큰입니다.", "ERR_INVALID_TOKEN", HttpStatus.UNAUTHORIZED);
        }
    }

    // ── Refresh Token Rotation (RTR) ─────────────────────────────────

    /**
     * 기존 Refresh Token을 검증하고 새 Refresh Token으로 교체합니다.
     * Redis에 저장된 토큰과 불일치 시 탈취 가능성으로 간주하고 해당 유저의 토큰을 즉시 삭제합니다.
     */
    public String rotateRefreshToken(String formerToken, String username) {
        String storedToken = redisService.getValue("RT:" + username);

        if (storedToken == null || !storedToken.equals(formerToken)) {
            redisService.deleteValue("RT:" + username);
            throw new GlobalException("유효하지 않거나 만료된 리프레시 토큰입니다. 다시 로그인해주세요.", "ERR_INVALID_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }

        return createRefreshToken(username);
    }

    /**
     * 로그아웃 시 Redis에서 Refresh Token을 삭제합니다.
     */
    public void deleteRefreshToken(String username) {
        redisService.deleteValue("RT:" + username);
    }
}
