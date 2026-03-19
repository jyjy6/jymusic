package jymusic.jym_order_service.dto.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UpdateCartItemRequest {

    @Min(value = 0, message = "수량은 0 이상이어야 합니다.")
    private int quantity;
}
