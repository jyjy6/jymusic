package jymusic.jym_catalog_service.common.GlobalErrorHandler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.nio.file.AccessDeniedException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(GlobalException.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(GlobalException ex) {
        log.error("Business Exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        Map<String, Object> errorResponse = createErrorResponse(
                ex.getErrorCode(), ex.getMessage(), ex.getHttpStatus().value());
        return ResponseEntity.status(ex.getHttpStatus()).body(errorResponse);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        String firstMessage = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("유효하지 않은 요청입니다.");
        Map<String, Object> errorResponse = createErrorResponse(
                "ERR_VALIDATION_FAILED", firstMessage, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unexpected Exception: ", ex);
        Map<String, Object> errorResponse = createErrorResponse(
                "INTERNAL_SERVER_ERROR", "시스템 오류가 발생했습니다.", HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<Map<String, Object>> handleAccessDeniedException(Exception e) {
        log.error("권한 없는 접근 시도: {}", e.getMessage());
        Map<String, Object> errorResponse = createErrorResponse(
                "HTTP_UNAUTHORIZED_ERROR", "접근 권한이 없습니다.", HttpStatus.UNAUTHORIZED.value());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse);
    }

    private Map<String, Object> createErrorResponse(String errorCode, String message, int status) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("code", errorCode);
        errorResponse.put("message", message);
        errorResponse.put("status", status);
        errorResponse.put("timestamp", LocalDateTime.now().toString());
        return errorResponse;
    }
}
