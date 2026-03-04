package jymusic.jym_member_auth_service.common.redis;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisService 단위 테스트")
class RedisServiceTest {

    @InjectMocks
    private RedisService redisService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @BeforeEach
    void setUp() {
        // deleteValue()는 opsForValue()를 호출하지 않으므로 lenient로 설정
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ── setValue() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("setValue()")
    class SetValue {

        @Test
        @DisplayName("RD-01: 키·값·TTL 전달 → valueOps.set() 호출")
        void setValue_storesKeyValueWithTtl() {
            Duration ttl = Duration.ofSeconds(3600);

            redisService.setValue("RT:testuser", "refresh.token.value", ttl);

            verify(valueOps).set("RT:testuser", "refresh.token.value", ttl);
        }
    }

    // ── getValue() ──────────────────────────────────────────────────

    @Nested
    @DisplayName("getValue()")
    class GetValue {

        @Test
        @DisplayName("RD-02: 존재하는 키 → 저장된 값 반환")
        void getValue_existingKey_returnsValue() {
            given(valueOps.get("RT:testuser")).willReturn("stored.refresh.token");

            String result = redisService.getValue("RT:testuser");

            assertThat(result).isEqualTo("stored.refresh.token");
        }

        @Test
        @DisplayName("RD-03: 존재하지 않는 키 → null 반환")
        void getValue_nonExistingKey_returnsNull() {
            given(valueOps.get("RT:unknown")).willReturn(null);

            String result = redisService.getValue("RT:unknown");

            assertThat(result).isNull();
        }
    }

    // ── deleteValue() ───────────────────────────────────────────────

    @Nested
    @DisplayName("deleteValue()")
    class DeleteValue {

        @Test
        @DisplayName("RD-04: 키 전달 → redisTemplate.delete() 호출")
        void deleteValue_callsRedisTemplateDelete() {
            redisService.deleteValue("RT:testuser");

            verify(redisTemplate).delete("RT:testuser");
        }
    }
}
