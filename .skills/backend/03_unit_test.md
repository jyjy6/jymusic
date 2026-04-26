# 단위 테스트 작성

> **참조**: `.skills/_common/00_project_context.md`

## 개요
Service 레이어 단위 테스트 표준. JUnit 5 + Mockito. 커버리지 목표 70%+.

## 입력
- 대상 서비스 클래스명, 테스트할 메서드, 의존성 목록

## 기본 구조

```java
package jymusic.jym_{서비스명}.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jymusic.jym_{서비스명}.common.GlobalErrorHandler.GlobalException;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @InjectMocks
    private XxxService xxxService;

    @Mock
    private XxxRepository xxxRepository;

    @Mock
    private EventPublisher eventPublisher;

    @Nested
    @DisplayName("getXxx()")
    class GetXxx {

        @Test
        @DisplayName("정상 — 존재하는 ID로 조회하면 응답을 반환한다")
        void success() {
            // given
            Long id = 1L;
            XxxEntity entity = XxxEntity.builder()
                    .id(id).title("Test").build();
            given(xxxRepository.findById(id)).willReturn(Optional.of(entity));

            // when
            XxxResponse result = xxxService.getXxx(id);

            // then
            assertThat(result.getId()).isEqualTo(id);
            assertThat(result.getTitle()).isEqualTo("Test");
            then(xxxRepository).should().findById(id);
        }

        @Test
        @DisplayName("예외 — 존재하지 않는 ID로 조회하면 GlobalException 발생")
        void notFound() {
            // given
            given(xxxRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> xxxService.getXxx(999L))
                    .isInstanceOf(GlobalException.class)
                    .satisfies(ex -> {
                        GlobalException ge = (GlobalException) ex;
                        assertThat(ge.getErrorCode()).isEqualTo("ERR_XXX_NOT_FOUND");
                    });
        }
    }

    @Nested
    @DisplayName("createXxx()")
    class CreateXxx {

        @Test
        @DisplayName("정상 — 유효한 요청으로 생성하면 저장된 엔티티를 반환한다")
        void success() {
            // given
            Long memberId = 1L;
            XxxCreateRequest request = new XxxCreateRequest();
            // request 필드 설정 (reflection 또는 Builder)

            XxxEntity saved = XxxEntity.builder()
                    .id(1L).memberId(memberId).title("New").build();
            given(xxxRepository.save(any(XxxEntity.class))).willReturn(saved);

            // when
            XxxResponse result = xxxService.createXxx(memberId, request);

            // then
            assertThat(result.getId()).isEqualTo(1L);
            then(xxxRepository).should().save(any(XxxEntity.class));
        }
    }
}
```

## 테스트 3단 구조

| 구분 | 설명 | 예시 |
|------|------|------|
| **정상 케이스** | Happy path | 존재하는 ID 조회 → 성공 |
| **경계값** | 엣지 케이스 | 빈 리스트 반환, null 입력, 최대값 |
| **예외 케이스** | 에러 상황 | 존재하지 않는 ID → GlobalException |

## GlobalException 검증 패턴
```java
assertThatThrownBy(() -> service.method(input))
    .isInstanceOf(GlobalException.class)
    .satisfies(ex -> {
        GlobalException ge = (GlobalException) ex;
        assertThat(ge.getErrorCode()).isEqualTo("ERR_CODE");
        assertThat(ge.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    });
```

## Kafka 이벤트 발행 검증
```java
then(eventPublisher).should().publish(
    eq(KafkaTopics.ORDER_EVENTS),
    anyString(),
    eq(EventTypes.ORDER_CREATED),
    any(OrderCreatedPayload.class)
);
```

## verify() 호출 검증
```java
then(repository).should().save(any(Entity.class));         // 호출됨
then(repository).should(never()).delete(any());             // 호출 안 됨
then(service).should(times(2)).process(any());              // 정확히 2번
```

## 체크리스트
- [ ] `@ExtendWith(MockitoExtension.class)` 적용
- [ ] `@InjectMocks` / `@Mock` 올바르게 설정
- [ ] given/when/then (BDD 스타일) 구조 준수
- [ ] 정상/경계값/예외 3단 케이스 포함
- [ ] `GlobalException` errorCode, httpStatus 검증
- [ ] 의존성 호출 verify 포함
- [ ] `@DisplayName` 한글로 의미 전달

## 관련 스킬
- `backend/01_new_api_endpoint.md` — 테스트 대상 API 코드
