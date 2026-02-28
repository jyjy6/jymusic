package jy.Job_Flow_Agent.Auth.JWT;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jy.Job_Flow_Agent.Member.DTO.MemberDto;
import jy.Job_Flow_Agent.Member.Entity.Member;
import jy.Job_Flow_Agent.Member.Service.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class JWTFilter extends OncePerRequestFilter {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    private final JWTUtil jwtUtil;

    /**
     * 액세스토큰 뭔가의 이상으로 빠졌을때용
     * */
    @Value("${app.production}")
    private String appEnv;
    boolean isProduction = "production".equalsIgnoreCase(appEnv);
    @Value("${app.cookie.domain}")
    private String cookieDomain;


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // 제외할 경로들
        List<String> excludePaths = Arrays.asList(
                "/actuator/**",
                "/api/v1/login/jwt",
                "/api/v1/auth/**",
                "/api/v1/auth/csrf",
                "/api/v1/auth/refresh-token",
                "/api/v1/oauth/user/me"
        );
        // 하나라도 매칭되면 true 리턴 -> 필터 건너뜀
        return excludePaths.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String jwt = getJwtFromRequest(request);
        String refreshJwt = getRefreshJwtFromRequest(request);

        if (jwt != null) {
            try {
                // JWT 유효성 검증
                if (!jwtUtil.isTokenExpired(jwt)) {
                    // JWT에서 Claims 추출
                    Claims claims = jwtUtil.extractClaims(jwt);
                    String userInfoJson = claims.get("userInfo", String.class);

                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.registerModule(new JavaTimeModule());
                    objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    MemberDto memberDto = objectMapper.readValue(userInfoJson, MemberDto.class);

                    Member member = Member.builder()
                            .id(memberDto.getId())
                            .username(memberDto.getUsername())
                            .displayName(memberDto.getDisplayName())
                            .email(memberDto.getEmail())
                            .roles(memberDto.getRoleSet() != null ? memberDto.getRoleSet() : new HashSet<>(Set.of("ROLE_USER")))
                            .build();

                    // CustomUserDetails 생성 (Refresh Token과 동일한 방식)
                    CustomUserDetails userDetails = new CustomUserDetails(member);

                    // Authentication 객체 생성 (이제 일관성 있게 CustomUserDetails 사용)
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails,                    // CustomUserDetails 객체 (기존: memberDto.getUsername())
                            null,
                            userDetails.getAuthorities()    // CustomUserDetails에서 권한 가져오기 (기존: authorities)
                    );

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                } else {
                    // CORS 헤더 추가 (프론트엔드가 응답을 받을 수 있도록)
                    response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
                    response.setHeader("Access-Control-Allow-Credentials", "true");
                    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
                    
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json; charset=UTF-8");
                    response.getWriter().write("{\"message\": \"Access token expired\"}");
                    return;
                }
            } catch (Exception e) {
                log.info("JWT 검증 실패: {}", e.getMessage());
                e.printStackTrace();
                
                // CORS 헤더 추가
                response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
                response.setHeader("Access-Control-Allow-Credentials", "true");
                response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
                response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
                
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json; charset=UTF-8");
                response.getWriter().write("{\"message\": \"Invalid JWT token\"}");
                return; // 필터 체인 종료
            }
        } else if (refreshJwt != null){
            // CORS 헤더 추가
            response.setHeader("Access-Control-Allow-Origin", request.getHeader("Origin"));
            response.setHeader("Access-Control-Allow-Credentials", "true");
            
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"message\": \"accessToken Is Null Refresh\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    // 요청에서 JWT를 추출하는 메서드
    private String getJwtFromRequest(HttpServletRequest request) {
        // 1. 쿠키에서 accessToken 찾기
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        // 2. Authorization 헤더에서 Bearer 토큰 찾기 (기존 로직 유지)
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // "Bearer " 제거
        }
        return null;
    }

    private String getRefreshJwtFromRequest(HttpServletRequest request) {
        // 1. 쿠키에서 refreshToken 찾기
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private Collection<GrantedAuthority> extractAuthoritiesFromMemberDto(MemberDto memberDto) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        // memberDto에서 권한 정보를 추출하여 authorities에 추가
        // 예: memberDto.getRoles()가 권한 목록을 반환한다고 가정
        if (memberDto.getRoleSet() != null) {
            for (String role : memberDto.getRoleSet()) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
        }
        return authorities;
    }
}