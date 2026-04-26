package ru.yandex.practicum.warehouse.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "order_bookings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID orderId;

    private UUID deliveryId;

    @ElementCollection
    @CollectionTable(name = "booking_products", joinColumns = @JoinColumn(name = "booking_id"))
    @MapKeyColumn(name = "product_id")
    @Column(name = "quantity")
    private Map<UUID, Long> products;

    @Column(nullable = false)
    private Double totalWeight;

    @Column(nullable = false)
    private Double totalVolume;

    private boolean fragile;
}