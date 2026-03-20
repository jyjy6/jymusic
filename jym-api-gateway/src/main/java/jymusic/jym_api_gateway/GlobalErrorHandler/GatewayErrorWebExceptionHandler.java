package jymusic.jym_api_gateway.GlobalErrorHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Slf4j
@Component
@Order(-2) // Higher priority than DefaultErrorWebExceptionHandler(-1)
public class GatewayErrorWebExceptionHandler implements WebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        HttpStatus status;
        String code;
        String message;

        if (ex instanceof GlobalException ge) {
            status = ge.getHttpStatus();
            code = ge.getErrorCode();
            message = ge.getMessage();
            log.error("Business Exception: {} - {}", code, message);

            if ("RATE_LIMIT_EXCEEDED".equals(code)) {
                status = HttpStatus.TOO_MANY_REQUESTS;
            }
        } else if (ex instanceof AccessDeniedException || ex instanceof AuthorizationDeniedException) {
            status = HttpStatus.UNAUTHORIZED;
            code = "HTTP_UNAUTHORIZED_ERROR";
            message = "접근 권한이 없습니다.";
            log.error("Unauthorized access attempt: {}", ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            code = "INTERNAL_SERVER_ERROR";
            message = "시스템 오류가 발생했습니다";
            log.error("Unexpected Exception: ", ex);
        }

        response.setStatusCode(status);
        String body = String.format(
                "{\"code\":\"%s\",\"message\":\"%s\",\"status\":%d,\"timestamp\":\"%s\"}",
                code, message, status.value(), LocalDateTime.now()
        );
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }
}
