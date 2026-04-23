package ru.yandex.practicum.warehouse.mapper;

import lombok.experimental.UtilityClass;
import ru.yandex.practicum.dto.warehouse.DimensionDto;
import ru.yandex.practicum.dto.warehouse.NewProductInWarehouseRequest;
import ru.yandex.practicum.warehouse.model.Dimension;
import ru.yandex.practicum.warehouse.model.WarehouseProduct;

@UtilityClass
public class WarehouseMapper {
    public WarehouseProduct toEntity(NewProductInWarehouseRequest request) {
        Dimension dimension = Dimension.builder()
                .width(request.getDimension().getWidth())
                .height(request.getDimension().getHeight())
                .depth(request.getDimension().getDepth())
                .build();

        return WarehouseProduct.builder()
                .productId(request.getProductId())
                .quantity(0L)
                .fragile(request.getFragile() != null && request.getFragile())
                .dimension(dimension)
                .weight(request.getWeight())
                .build();
    }

    public DimensionDto toDto(Dimension dimension) {
        if (dimension == null) return null;
        return DimensionDto.builder()
                .width(dimension.getWidth())
                .height(dimension.getHeight())
                .depth(dimension.getDepth())
                .build();
    }
}