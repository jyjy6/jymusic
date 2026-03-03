package jymusic.jym_api_gateway.common.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jymusic.jym_api_gateway.common.jwt.JwtValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerificationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final List<String> EXCLUDE_PATHS = List.of(
            "/api/v1/auth/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/openapi.yaml"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDE_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token == null) {
            writeUnauthorizedResponse(response, "인증 토큰이 누락되었습니다.");
            return;
        }

        if (!jwtValidator.validateToken(token)) {
            writeUnauthorizedResponse(response, "인증 정보가 유효하지 않거나 만료되었습니다.");
            return;
        }

        Claims claims = jwtValidator.getClaims(token);
        String userId   = String.valueOf(claims.get("userId"));
        String username = claims.getSubject();
        String role     = String.valueOf(claims.get("role"));

        log.debug("인증된 사용자 → userId: {}, username: {}, role: {}", userId, username, role);

        // HttpServletRequestWrapper를 통해 다운스트림 서비스로 사용자 정보 헤더를 주입합니다.
        HttpServletRequest mutatedRequest = new UserContextRequestWrapper(request, userId, username, role);
        filterChain.doFilter(mutatedRequest, response);
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private void writeUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        log.warn("JWT 검증 실패: {}", message);
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json; charset=UTF-8");
        response.getWriter().write(String.format(
                "{\"status\":401,\"code\":\"ERR_UNAUTHORIZED\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                message, LocalDateTime.now()
        ));
    }

    /**
     * JWT 클레임에서 추출한 사용자 정보를 HTTP 헤더로 추가하는 래퍼.
     * 다운스트림 마이크로서비스는 이 헤더를 통해 인증된 사용자 컨텍스트를 수신합니다.
     */
    private static class UserContextRequestWrapper extends HttpServletRequestWrapper {

        private final Map<String, String> extraHeaders;

        UserContextRequestWrapper(HttpServletRequest request, String userId, String username, String role) {
            super(request);
            this.extraHeaders = Map.of(
                    "X-User-Id",   userId,
                    "X-User-Name", username,
                    "X-User-Role", role
            );
        }

        @Override
        public String getHeader(String name) {
            if (extraHeaders.containsKey(name)) {
                return extraHeaders.get(name);
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (extraHeaders.containsKey(name)) {
                return Collections.enumeration(List.of(extraHeaders.get(name)));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = new ArrayList<>(Collections.list(super.getHeaderNames()));
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }
    }
}
