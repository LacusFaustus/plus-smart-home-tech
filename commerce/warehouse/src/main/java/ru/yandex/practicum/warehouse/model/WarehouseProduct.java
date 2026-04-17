package ru.yandex.practicum.warehouse.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "warehouse_products", schema = "warehouse_db")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseProduct {
    @Id
    private UUID productId;

    @Column(nullable = false)
    private Long quantity;

    @Column(nullable = false)
    private Boolean fragile;

    @Embedded
    private Dimension dimension;

    @Column(nullable = false)
    private Double weight;

    @Version
    private Long version;
}