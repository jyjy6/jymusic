package jymusic.jym_order_service.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jymusic.jym_order_service.domain.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AdminStatusUpdateRequest {

    @NotNull(message = "변경할 상태는 필수입니다.")
    private OrderStatus status;

    @Size(max = 255, message = "reason은 255자를 초과할 수 없습니다.")
    private String reason;
}
