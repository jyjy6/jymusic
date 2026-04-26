package jymusic.jym_order_service.dto.request;

import jymusic.jym_order_service.domain.entity.OrderStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class AdminOrderSearchRequest {
    private String keyword;
    private String productTitle;
    private OrderStatus status;
    private List<OrderStatus> statuses;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    private Long minAmount;
    private Long maxAmount;
}
