package jymusic.jym_member_auth_service.controller.member;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jymusic.jym_member_auth_service.common.GlobalErrorHandler.GlobalException;
import jymusic.jym_member_auth_service.dto.member.AuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.MemberLoginRequest;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.MemberRegistrationRequest;
import jymusic.jym_member_auth_service.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private static final int REFRESH_TOKEN_MAX_AGE = 7 * 24 * 60 * 60; // 7일 (초)

    private final MemberService memberService;

    @PostMapping("/register")
    public ResponseEntity<MemberProfileResponse> register(@Valid @RequestBody MemberRegistrationRequest request) {
        MemberProfileResponse response = memberService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody MemberLoginRequest request, HttpServletResponse response) {
        Map<String, String> tokens = memberService.login(request);

        setRefreshTokenCookie(response, tokens.get("refreshToken"), REFRESH_TOKEN_MAX_AGE);

        return ResponseEntity.ok(AuthTokenResponse.builder()
                .accessToken(tokens.get("accessToken"))
                .build());
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<AuthTokenResponse> refreshToken(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new GlobalException("리프레시 토큰이 없습니다. 다시 로그인해주세요.", "ERR_MISSING_REFRESH_TOKEN", HttpStatus.UNAUTHORIZED);
        }

        Map<String, String> tokens = memberService.refreshTokens(refreshToken);

        // RTR: 새 Refresh Token을 Cookie로 재설정
        setRefreshTokenCookie(response, tokens.get("refreshToken"), REFRESH_TOKEN_MAX_AGE);

        return ResponseEntity.ok(AuthTokenResponse.builder()
                .accessToken(tokens.get("accessToken"))
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        memberService.logout(refreshToken);

        // Cookie 만료 처리
        setRefreshTokenCookie(response, "", 0);

        return ResponseEntity.ok().build();
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
