package ru.yandex.practicum.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.*;
import ru.yandex.practicum.dto.exceptions.*;
import ru.yandex.practicum.warehouse.mapper.WarehouseMapper;
import ru.yandex.practicum.warehouse.model.WarehouseProduct;
import ru.yandex.practicum.warehouse.repository.WarehouseProductRepository;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseService {
    private final WarehouseProductRepository repository;

    @Transactional
    public void registerNewProduct(NewProductInWarehouseRequest request) {
        if (repository.existsByProductId(request.getProductId())) {
            throw new SpecifiedProductAlreadyInWarehouseException(request.getProductId());
        }
        WarehouseProduct product = WarehouseMapper.toEntity(request);
        repository.save(product);
        log.info("Registered new product in warehouse: productId={}", request.getProductId());
    }

    @Transactional
    public void addStock(UUID productId, Long quantity) {
        WarehouseProduct product = repository.findByProductId(productId)
                .orElseThrow(() -> new NoSpecifiedProductInWarehouseException(productId));
        product.setQuantity(product.getQuantity() + quantity);
        repository.save(product);
        log.info("Added {} units of product {}, new total: {}", quantity, productId, product.getQuantity());
    }

    public BookedProductsDto checkAvailability(ShoppingCartDto cart) {
        if (cart.getProducts() == null || cart.getProducts().isEmpty()) {
            return BookedProductsDto.builder()
                    .deliveryWeight(0.0)
                    .deliveryVolume(0.0)
                    .fragile(false)
                    .build();
        }

        double totalWeight = 0.0;
        double totalVolume = 0.0;
        boolean hasFragile = false;

        for (Map.Entry<UUID, Long> entry : cart.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long requestedQty = entry.getValue();

            WarehouseProduct product = repository.findByProductId(productId)
                    .orElseThrow(() -> new NoSpecifiedProductInWarehouseException(productId));

            if (product.getQuantity() < requestedQty) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(
                        productId, product.getQuantity(), requestedQty
                );
            }

            totalWeight += product.getWeight() * requestedQty;
            totalVolume += product.getDimension().getWidth() *
                    product.getDimension().getHeight() *
                    product.getDimension().getDepth() * requestedQty;

            if (product.getFragile()) {
                hasFragile = true;
            }
        }

        BookedProductsDto result = BookedProductsDto.builder()
                .deliveryWeight(totalWeight)
                .deliveryVolume(totalVolume)
                .fragile(hasFragile)
                .build();

        log.info("Stock check passed for cart {}, weight={}, volume={}, fragile={}",
                cart.getShoppingCartId(), totalWeight, totalVolume, hasFragile);

        return result;
    }
}