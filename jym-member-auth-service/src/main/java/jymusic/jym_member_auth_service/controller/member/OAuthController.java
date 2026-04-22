package jymusic.jym_member_auth_service.controller.member;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jymusic.jym_member_auth_service.config.AppProperties;
import jymusic.jym_member_auth_service.service.member.OAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * OAuth 2.0 소셜 로그인 Controller.
 *
 * <p>전체 흐름은 {@code sdd-spec-docs/feature/jym-member-auth-service/05_OAUTH_DESIGN_KR.md} 참고.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/oauth2")
@RequiredArgsConstructor
public class OAuthController {

    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일 (초)

    private final OAuthService oauthService;
    private final AppProperties appProperties;

    /**
     * 소셜 로그인 시작: 지정된 Provider의 인가 URL로 302 Redirect 합니다.
     */
    @GetMapping("/{provider}")
    public ResponseEntity<Void> startAuthorization(@PathVariable String provider) {
        String authorizationUrl = oauthService.buildAuthorizationUrl(provider);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, authorizationUrl)
                .build();
    }

    /**
     * OAuth 콜백 처리.
     * 성공 시 AT는 query parameter, RT는 HttpOnly Cookie로 프론트 success 페이지에 전달합니다.
     */
    @GetMapping("/callback/{provider}")
    public ResponseEntity<Void> callback(
            @PathVariable String provider,
            @RequestParam("code") String code,
            @RequestParam("state") String state,
            HttpServletResponse response
    ) {
        Map<String, String> tokens = oauthService.processCallback(provider, code, state);

        setRefreshTokenCookie(response, tokens.get("refreshToken"), REFRESH_TOKEN_MAX_AGE);

        String redirectUrl = appProperties.getFrontBaseUrl()
                + "/auth/oauth2/success?accessToken="
                + URLEncoder.encode(tokens.get("accessToken"), StandardCharsets.UTF_8);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(redirectUrl))
                .build();
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // HTTPS 환경에서는 true로 변경
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        response.addCookie(cookie);
    }
}
