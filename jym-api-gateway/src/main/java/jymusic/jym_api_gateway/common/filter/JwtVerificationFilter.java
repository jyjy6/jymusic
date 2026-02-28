package jymusic.jym_api_gateway.common.filter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jymusic.jym_api_gateway.common.jwt.JwtValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVerificationFilter extends OncePerRequestFilter {

    private final JwtValidator jwtValidator;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Skip authentication for these paths
    private final List<String> EXCLUDE_PATHS = Arrays.asList(
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

        if (token != null && jwtValidator.validateToken(token)) {
            Claims claims = jwtValidator.getClaims(token);

            // 1. Extract information from token
            String userId = String.valueOf(claims.get("userId"));
            String username = claims.getSubject();
            String role = (String) claims.get("role");

            // 2. Wrap request with custom headers
            // In Spring Cloud Gateway WebMvc, we can use request attributes or modify the request if possible
            // But for simple forwarding, we rely on the filter to pass information downstream
            request.setAttribute("X-User-Id", userId);
            request.setAttribute("X-User-Name", username);
            request.setAttribute("X-User-Role", role);

            log.debug("Authenticated user: {}, role: {}", username, role);
            filterChain.doFilter(request, response);
        } else {
            log.warn("Invalid or missing JWT token for path: {}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json; charset=UTF-8");
            response.getWriter().write("{\"status\": 401, \"code\": \"ERR_UNAUTHORIZED\", \"message\": \"인증 정보가 유효하지 않습니다.\"}");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
