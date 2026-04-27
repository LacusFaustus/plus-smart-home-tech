package ru.yandex.practicum.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.delivery.client.OrderDeliveryFeignClient;
import ru.yandex.practicum.delivery.client.WarehouseDeliveryFeignClient;
import ru.yandex.practicum.delivery.mapper.DeliveryMapper;
import ru.yandex.practicum.delivery.model.Delivery;
import ru.yandex.practicum.delivery.repository.DeliveryRepository;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.warehouse.ShippedToDeliveryRequest;
import ru.yandex.practicum.enums.DeliveryState;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final WarehouseDeliveryFeignClient warehouseClient;
    private final OrderDeliveryFeignClient orderClient;

    private static final BigDecimal BASE_COST = BigDecimal.valueOf(5.0);
    private static final BigDecimal ADDRESS_1_MULTIPLIER = BigDecimal.valueOf(1.0);
    private static final BigDecimal ADDRESS_2_MULTIPLIER = BigDecimal.valueOf(2.0);
    private static final BigDecimal FRAGILE_MULTIPLIER = BigDecimal.valueOf(0.2);
    private static final BigDecimal WEIGHT_MULTIPLIER = BigDecimal.valueOf(0.3);
    private static final BigDecimal VOLUME_MULTIPLIER = BigDecimal.valueOf(0.2);
    private static final BigDecimal ADDRESS_MISMATCH_MULTIPLIER = BigDecimal.valueOf(0.2);
    private static final String ADDRESS_1 = "ADDRESS_1";
    private static final String ADDRESS_2 = "ADDRESS_2";

    @Transactional
    public DeliveryDto planDelivery(DeliveryDto deliveryDto) {
        if (deliveryDto.getOrderId() == null) {
            throw new IllegalArgumentException("Order ID cannot be null");
        }

        Delivery delivery = deliveryMapper.toEntity(deliveryDto);
        delivery.setDeliveryState(DeliveryState.CREATED);
        delivery = deliveryRepository.save(delivery);

        log.info("Created delivery: {} for order: {}", delivery.getDeliveryId(), delivery.getOrderId());
        return deliveryMapper.toDto(delivery);
    }

    public BigDecimal deliveryCost(OrderDto order) {
        Delivery delivery = deliveryRepository.findByOrderId(order.getOrderId())
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + order.getOrderId()));

        BigDecimal cost = BASE_COST;

        String fromAddressKey = delivery.getFromAddress().getCountry();
        if (ADDRESS_2.equals(fromAddressKey)) {
            cost = cost.multiply(ADDRESS_2_MULTIPLIER).add(BASE_COST);
        }

        if (Boolean.TRUE.equals(order.getFragile())) {
            cost = cost.add(cost.multiply(FRAGILE_MULTIPLIER));
        }

        BigDecimal weight = order.getDeliveryWeight() != null
                ? BigDecimal.valueOf(order.getDeliveryWeight())
                : BigDecimal.ZERO;
        if (weight.compareTo(BigDecimal.ZERO) > 0) {
            cost = cost.add(weight.multiply(WEIGHT_MULTIPLIER));
        }

        BigDecimal volume = order.getDeliveryVolume() != null
                ? BigDecimal.valueOf(order.getDeliveryVolume())
                : BigDecimal.ZERO;
        if (volume.compareTo(BigDecimal.ZERO) > 0) {
            cost = cost.add(volume.multiply(VOLUME_MULTIPLIER));
        }

        String fromStreet = delivery.getFromAddress().getStreet();
        String toStreet = delivery.getToAddress().getStreet();
        if (fromStreet != null && toStreet != null && !fromStreet.equals(toStreet)) {
            cost = cost.add(cost.multiply(ADDRESS_MISMATCH_MULTIPLIER));
        }

        cost = cost.setScale(2, RoundingMode.HALF_UP);
        log.info("Delivery cost calculated for order {}: {}", order.getOrderId(), cost);
        return cost;
    }

    @Transactional
    public void deliverySuccessful(UUID orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        if (delivery.getDeliveryState() != DeliveryState.IN_PROGRESS) {
            throw new IllegalStateException(
                    String.format("Delivery must be IN_PROGRESS to mark as successful, current state: %s",
                            delivery.getDeliveryState()));
        }

        delivery.setDeliveryState(DeliveryState.DELIVERED);
        deliveryRepository.save(delivery);

        orderClient.delivery(orderId);
        log.info("Delivery successful for order: {}", orderId);
    }

    @Transactional
    public void deliveryPicked(UUID orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        if (delivery.getDeliveryState() != DeliveryState.CREATED) {
            throw new IllegalStateException(
                    String.format("Delivery must be CREATED to start, current state: %s",
                            delivery.getDeliveryState()));
        }

        delivery.setDeliveryState(DeliveryState.IN_PROGRESS);
        delivery = deliveryRepository.save(delivery);

        ShippedToDeliveryRequest request = ShippedToDeliveryRequest.builder()
                .orderId(orderId)
                .deliveryId(delivery.getDeliveryId())
                .build();
        warehouseClient.shippedToDelivery(request);

        log.info("Delivery picked up for order: {}", orderId);
    }

    @Transactional
    public void deliveryFailed(UUID orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        if (delivery.getDeliveryState() != DeliveryState.CREATED
                && delivery.getDeliveryState() != DeliveryState.IN_PROGRESS) {
            throw new IllegalStateException(
                    String.format("Delivery must be CREATED or IN_PROGRESS to fail, current state: %s",
                            delivery.getDeliveryState()));
        }

        delivery.setDeliveryState(DeliveryState.FAILED);
        deliveryRepository.save(delivery);

        orderClient.deliveryFailed(orderId);
        log.info("Delivery failed for order: {}", orderId);
    }
}