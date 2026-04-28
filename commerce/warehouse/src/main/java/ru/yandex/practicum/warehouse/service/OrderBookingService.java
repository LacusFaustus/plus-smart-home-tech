package ru.yandex.practicum.warehouse.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.warehouse.AssemblyProductsForOrderRequest;
import ru.yandex.practicum.dto.warehouse.BookedProductsDto;
import ru.yandex.practicum.dto.exceptions.ProductInShoppingCartLowQuantityInWarehouse;
import ru.yandex.practicum.dto.exceptions.NoSpecifiedProductInWarehouseException;
import ru.yandex.practicum.warehouse.model.OrderBooking;
import ru.yandex.practicum.warehouse.model.WarehouseProduct;
import ru.yandex.practicum.warehouse.repository.OrderBookingRepository;
import ru.yandex.practicum.warehouse.repository.WarehouseProductRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBookingService {
    private final WarehouseProductRepository productRepository;
    private final OrderBookingRepository orderBookingRepository;

    @Transactional
    public BookedProductsDto assembleOrder(AssemblyProductsForOrderRequest request) {
        double totalWeight = 0.0;
        double totalVolume = 0.0;
        boolean fragile = false;
        Map<UUID, Long> bookedProducts = new HashMap<>();

        for (Map.Entry<UUID, Long> entry : request.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            WarehouseProduct product = productRepository.findByProductId(productId)
                    .orElseThrow(() -> new NoSpecifiedProductInWarehouseException(productId));

            if (product.getQuantity() < quantity) {
                throw new ProductInShoppingCartLowQuantityInWarehouse(productId, product.getQuantity(), quantity);
            }

            product.setQuantity(product.getQuantity() - quantity);
            productRepository.save(product);

            totalWeight += product.getWeight() * quantity;
            totalVolume += product.getDimension().getWidth() *
                    product.getDimension().getHeight() *
                    product.getDimension().getDepth() * quantity;

            if (product.getFragile()) {
                fragile = true;
            }

            bookedProducts.put(productId, quantity);
        }

        OrderBooking booking = OrderBooking.builder()
                .orderId(request.getOrderId())
                .products(bookedProducts)
                .totalWeight(totalWeight)
                .totalVolume(totalVolume)
                .fragile(fragile)
                .build();
        orderBookingRepository.save(booking);

        log.info("Assembled order {}, weight={}, volume={}, fragile={}",
                request.getOrderId(), totalWeight, totalVolume, fragile);

        return BookedProductsDto.builder()
                .deliveryWeight(totalWeight)
                .deliveryVolume(totalVolume)
                .fragile(fragile)
                .build();
    }

    @Transactional
    public void updateDeliveryId(UUID orderId, UUID deliveryId) {
        OrderBooking booking = orderBookingRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Order booking not found: " + orderId));
        booking.setDeliveryId(deliveryId);
        orderBookingRepository.save(booking);
        log.info("Updated deliveryId={} for order {}", deliveryId, orderId);
    }
}