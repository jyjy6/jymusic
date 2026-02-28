package jymusic.jym_member_auth_service.controller.member;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jymusic.jym_member_auth_service.dto.member.AuthTokenResponse;
import jymusic.jym_member_auth_service.dto.member.MemberLoginRequest;
import jymusic.jym_member_auth_service.dto.member.MemberProfileResponse;
import jymusic.jym_member_auth_service.dto.member.MemberRegistrationRequest;
import jymusic.jym_member_auth_service.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class MemberAuthController {

    private final MemberService memberService;

    @PostMapping("/register")
    public ResponseEntity<MemberProfileResponse> register(@Valid @RequestBody MemberRegistrationRequest request) {
        MemberProfileResponse response = memberService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthTokenResponse> login(@Valid @RequestBody MemberLoginRequest request, HttpServletResponse response) {
        Map<String, String> tokens = memberService.login(request);
        
        // 1. Set Refresh Token in Cookie (Security Best Practice)
        Cookie refreshCookie = new Cookie("refreshToken", tokens.get("refreshToken"));
        refreshCookie.setHttpOnly(true);
        refreshCookie.setSecure(false); // Enable this in production with HTTPS
        refreshCookie.setPath("/");
        refreshCookie.setMaxAge(7 * 24 * 60 * 60); // 7 days
        response.addCookie(refreshCookie);

        // 2. Return Access Token in Response Body
        AuthTokenResponse authResponse = AuthTokenResponse.builder()
                .accessToken(tokens.get("accessToken"))
                .build();

        return ResponseEntity.ok(authResponse);
    }
}
