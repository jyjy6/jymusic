package jy.Job_Flow_Agent.Auth.JWT;



import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jy.Job_Flow_Agent.Member.DTO.MemberDto;
import jy.Job_Flow_Agent.Member.Entity.Member;
import jy.Job_Flow_Agent.Member.Repository.MemberRepository;
import jy.Job_Flow_Agent.Member.Service.CustomUserDetails;
import jy.Job_Flow_Agent.Member.Service.MemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.util.*;


@RestController
@Slf4j
@RequiredArgsConstructor // Lombok을 사용하여 생성자 주입
@RequestMapping("/api/v1/auth")
public class JWTController {

    private final MemberService memberService;
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final AuthenticationManager authenticationManager;
    private final JWTUtil jwtUtil;
    @Value("${app.production}")
    private String appEnv;

    boolean isProduction = "production".equalsIgnoreCase(appEnv);
    @Value("${app.cookie.domain}")
    private String cookieDomain;

    @Operation(summary = "로그인", description = "사용자 인증을 통해 JWT 토큰을 발급하고 쿠키에 리프레시 토큰을 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그인 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"accessToken\": \"eyJ...\", \"userInfo\": {\"username\": \"test\", \"email\": \"test@example.com\"}}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"로그인 실패: Bad credentials\"}")))
    })
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> loginJWT(@RequestBody Map<String, String> data, HttpServletResponse response) {
        try {

            var authToken = new UsernamePasswordAuthenticationToken(
                    data.get("username"), data.get("password")
            );

            // AuthenticationManager를 사용하여 인증 수행
            Authentication auth = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(auth);

            // JWT 생성
            String accessToken = jwtUtil.createAccessToken(auth);
            String refreshToken = jwtUtil.createRefreshToken(auth.getName());

            // 🔐 RefreshToken을 Redis에 저장 (Rotation을 위한 저장)
            jwtUtil.storeRefreshToken(auth.getName(), refreshToken);
            /*
             * "여러 기기에서 동시 로그인"을 허용하고싶을때엔
             * storeRefreshToken을 변경해야함.
             * Refresh Token을 단일 값이 아닌, 리스트(List)나 세트(Set) 형태로 저장해야 합니다.
             * 로그인 시: 새로운 Refresh Token을 기존 리스트에 추가합니다.
             * 토큰 갱신 시: 요청으로 들어온 Refresh Token이 해당 유저의 Token 리스트에 포함되어 있는지 확인합니다.
             * 로그아웃 시: 리스트에서 해당 Refresh Token을 삭제합니다. (특정 기기 로그아웃)
             * 모든 기기 로그아웃 시: 해당 유저의 Token 리스트 전체를 비웁니다.
             * 이 경우, 각 Refresh Token에 기기 정보(User-Agent), IP 주소, 마지막 사용일 등을 함께 저장하여 관리하면 더욱 정교한 제어가 가능해집니다.
             *
             * */

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .maxAge(Duration.ofDays(7))
                    .httpOnly(true)
                    .secure(isProduction)
                    .path("/")
                    .domain(cookieDomain)
                    .sameSite("Strict") // 가장 보안 강한 설정
                    .build();
            response.addHeader("Set-Cookie", refreshCookie.toString());


            // Authorization 헤더 사용으로 accessToken 쿠키는 더 이상 설정하지 않음
            // 응답 바디 구성
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("accessToken", accessToken);

            CustomUserDetails loginUser = (CustomUserDetails) auth.getPrincipal();
            MemberDto memberDto = memberService.getUserInfo(loginUser);
            responseBody.put("userInfo", memberDto);


            return ResponseEntity.ok(responseBody);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "게스트 로그인", description = "임시 게스트 계정을 생성하고 JWT 토큰을 발급하며 쿠키에 리프레시 토큰을 설정합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "게스트 로그인 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"accessToken\": \"eyJ...\", \"userInfo\": {\"username\": \"GUEST...\", \"email\": \"guest@guest.guest\"}}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"로그인 실패: ...\"}")))
    })
    @PostMapping("/login/guest")
    public ResponseEntity<Map<String, Object>> guestLoginJWT(HttpServletResponse response) {
        try {
            String guestMemberCode = "GUEST" + UUID.randomUUID().toString().substring(0, 8);
            String guestPassword = UUID.randomUUID().toString().replace("-", "").substring(0, 16);


            Member guestMember = Member.builder()
                    .roles(new HashSet<>(Set.of("ROLE_USER", "ROLE_GUEST")))
                    .password(passwordEncoder.encode(guestPassword))
                    .email("guest@guest.guest")
                    .username(guestMemberCode)
                    .displayName(guestMemberCode)
                    .build();

            memberRepository.save(guestMember);

            var authToken = new UsernamePasswordAuthenticationToken(
                    guestMemberCode, guestPassword
            );

            // AuthenticationManager를 사용하여 인증 수행
            Authentication auth = authenticationManager.authenticate(authToken);
            SecurityContextHolder.getContext().setAuthentication(auth);


            // JWT 생성
            String accessToken = jwtUtil.createAccessToken(auth);
            String refreshToken = jwtUtil.createRefreshToken(auth.getName());

            // 🔐 RefreshToken을 Redis에 저장 (Rotation을 위한 저장)
            jwtUtil.storeRefreshToken(auth.getName(), refreshToken);

            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", refreshToken)
                    .maxAge(Duration.ofDays(7))
                    .httpOnly(true)
                    .secure(isProduction)
                    .path("/")
                    .domain(cookieDomain)
                    .sameSite("Strict") // 가장 보안 강한 설정
                    .build();
            response.addHeader("Set-Cookie", refreshCookie.toString());

// 🔐 Access Token 쿠키 - 1시간
            // Authorization 헤더 사용으로 accessToken 쿠키는 더 이상 설정하지 않음
            // 응답 바디 구성
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("accessToken", accessToken);

            CustomUserDetails loginUser = (CustomUserDetails) auth.getPrincipal();

            MemberDto memberDto = memberService.getUserInfo(loginUser);
            responseBody.put("userInfo", memberDto);
            log.info("유저정보");
            log.info(String.valueOf(memberDto));
            log.info("유저정보");
            log.info(memberDto.toString());

            return ResponseEntity.ok(responseBody);
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "로그인 실패: " + e.getMessage()));
        }
    }

    @Operation(summary = "액세스 토큰 갱신", description = "리프레시 토큰을 사용하여 새로운 액세스 토큰과 리프레시 토큰을 발급합니다. 리프레시 토큰 로테이션이 적용됩니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "토큰 갱신 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"새 토큰들이 발급되었습니다. (RefreshToken Rotation 적용)\", \"accessToken\": \"eyJ...\"}"))),
            @ApiResponse(responseCode = "401", description = "인증 실패 (리프레시 토큰 없음, 만료 또는 유효하지 않음)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"리프레시 토큰이 존재하지 않습니다.\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"서버 오류로 인해 토큰을 갱신할 수 없습니다.\"}")))
    })
    @GetMapping("/refresh-token")
    public ResponseEntity<Map<String, String>> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken, HttpServletResponse response) {
        log.info("새 액세스 토큰 요청됨");

        try {
            // 리프레시 토큰이 없는 경우
            if (refreshToken == null || refreshToken.isEmpty()) {
                log.info("리프레시 토큰이 없음");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "리프레시 토큰이 존재하지 않습니다."));
            }

            // 리프레시 토큰 만료 확인
            if (jwtUtil.isTokenExpired(refreshToken)) {
                log.info("토큰 만료됨");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "리프레시 토큰이 만료되었습니다."));
            }

            // 리프레시 토큰에서 사용자 정보(username 또는 userId) 추출
            String username = jwtUtil.extractUsername(refreshToken);
            log.info("필터:유저네임" + username);

            // 🔄 RefreshToken Rotation 적용: 기존 토큰을 새 토큰으로 교체
            String newRefreshToken = jwtUtil.refreshTokenRotation(refreshToken, username);

            // 새 accessToken 생성
            String newAccessToken = jwtUtil.refreshAccessToken(username);

            // 🔐 새로운 RefreshToken 쿠키 설정 (7일)
            ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", newRefreshToken)
                    .maxAge(Duration.ofDays(7))
                    .httpOnly(true)
                    .secure(isProduction)
                    .path("/")
                    .domain(cookieDomain)
                    .sameSite("Strict")
                    .build();
            response.addHeader("Set-Cookie", refreshCookie.toString());

            // AccessToken은 쿠키 대신 응답 바디로 반환

            log.info("토큰 로테이션 완료 - 새 RefreshToken과 AccessToken 발급");
            return ResponseEntity.ok(Map.of(
                    "message", "새 토큰들이 발급되었습니다. (RefreshToken Rotation 적용)",
                    "accessToken", newAccessToken
            ));

        } catch (Exception e) {
            log.error("토큰 갱신 중 오류 발생: " + e.getMessage());

            // RefreshToken 관련 오류인 경우 쿠키 삭제
            if (e.getMessage().contains("리프레시 토큰") || e.getMessage().contains("REFRESH_TOKEN")) {
                // RefreshToken 쿠키 삭제
                ResponseCookie expiredRefreshCookie = ResponseCookie.from("refreshToken", "")
                        .maxAge(0)
                        .httpOnly(true)
                        .secure(isProduction)
                        .path("/")
                        .domain(cookieDomain)
                        .sameSite("Strict")
                        .build();
                response.addHeader("Set-Cookie", expiredRefreshCookie.toString());

                // AccessToken 쿠키도 삭제
                ResponseCookie expiredAccessCookie = ResponseCookie.from("accessToken", "")
                        .maxAge(0)
                        .httpOnly(true)
                        .secure(isProduction)
                        .path("/")
                        .domain(cookieDomain)
                        .sameSite("Strict")
                        .build();
                response.addHeader("Set-Cookie", expiredAccessCookie.toString());

                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "인증이 만료되었습니다. 다시 로그인해주세요."));
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("message", "서버 오류로 인해 토큰을 갱신할 수 없습니다."));
        }
    }



    @Operation(summary = "로그아웃", description = "사용자의 리프레시 토큰을 무효화하고 관련 쿠키를 삭제하여 로그아웃 처리합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "로그아웃 성공",
                    content = @Content(mediaType = "text/plain",
                            schema = @Schema(example = "로그아웃 성공")))
    })
    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {
        log.info("로그아웃요청됨");
        try {
            // 🔐 RefreshToken이 있다면 사용자명을 추출하고 Redis에서 삭제
            if (refreshToken != null && !refreshToken.isEmpty()) {
                try {
                    String username = jwtUtil.extractUsername(refreshToken);
                    jwtUtil.removeRefreshToken(username);
                    log.info("Redis에서 RefreshToken 삭제 완료: {}", username);
                } catch (Exception e) {
                    log.info("RefreshToken 삭제 중 오류 (무시함): {}", e.getMessage());
                    // 로그아웃은 계속 진행 (토큰이 이미 만료되었을 수 있음)
                }
            }
        } catch (Exception e) {
            log.error("로그아웃 처리 중 오류 (무시함): {}", e.getMessage());
        }

        // RefreshToken 쿠키 삭제
        Cookie refreshCookie = new Cookie("refreshToken", null);
        refreshCookie.setMaxAge(0);
        refreshCookie.setHttpOnly(true);
        refreshCookie.setPath("/");
        refreshCookie.setDomain(cookieDomain);
        response.addCookie(refreshCookie);

        // AccessToken 쿠키 삭제
        Cookie accessCookie = new Cookie("accessToken", null);
        accessCookie.setMaxAge(0);
        accessCookie.setHttpOnly(true);
        accessCookie.setSecure(isProduction);
        accessCookie.setPath("/");
        accessCookie.setDomain(cookieDomain);
        response.addCookie(accessCookie);

        log.info("로그아웃 완료 - 쿠키 및 Redis RefreshToken 삭제");

        return ResponseEntity.ok("로그아웃 성공");
    }

    @Operation(summary = "사용자 정보 조회", description = "현재 인증된 사용자의 정보를 조회합니다.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "사용자 정보 조회 성공",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MemberDto.class))),
            @ApiResponse(responseCode = "401", description = "인증되지 않은 사용자",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(example = "{\"message\": \"Unauthorized\"}")))
    })
    @GetMapping("/api/members/userinfo")
    public MemberDto getUserInfo(@AuthenticationPrincipal CustomUserDetails customUserDetails) {

        return memberService.getUserInfo(customUserDetails);
    }
}