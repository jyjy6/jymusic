package jymusic.jym_catalog_service.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "stock_reservation_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class StockReservationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_reservation_id", nullable = false)
    private StockReservation stockReservation;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;
}
