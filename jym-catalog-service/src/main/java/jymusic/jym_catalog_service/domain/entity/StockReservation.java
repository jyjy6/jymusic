package jymusic.jym_catalog_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "stock_reservations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class StockReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockReservationStatus status;

    @OneToMany(mappedBy = "stockReservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StockReservationItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 예약 해제 (보상 트랜잭션).
     */
    public void release() {
        this.status = StockReservationStatus.RELEASED;
    }

    /**
     * 예약 확정 (결제 완료).
     */
    public void confirm() {
        this.status = StockReservationStatus.CONFIRMED;
    }
}
