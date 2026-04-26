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

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {
    private final DeliveryRepository deliveryRepository;
    private final DeliveryMapper deliveryMapper;
    private final WarehouseDeliveryFeignClient warehouseClient;
    private final OrderDeliveryFeignClient orderClient;

    private static final double BASE_COST = 5.0;
    private static final double FRAGILE_MULTIPLIER = 0.2;
    private static final double WEIGHT_MULTIPLIER = 0.3;
    private static final double VOLUME_MULTIPLIER = 0.2;
    private static final double ADDRESS_MISMATCH_MULTIPLIER = 0.2;

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

    public Double deliveryCost(OrderDto order) {
        Delivery delivery = deliveryRepository.findByOrderId(order.getOrderId())
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + order.getOrderId()));

        // Расчет по алгоритму из ТЗ
        double cost = BASE_COST;

        // Учет адреса склада (fromAddress)
        String fromAddressKey = delivery.getFromAddress().getCountry();
        if (fromAddressKey != null && fromAddressKey.contains("ADDRESS_2")) {
            cost = cost * 2 + BASE_COST;  // умножаем на 2 и складываем с базовой
        }

        // Хрупкость
        if (Boolean.TRUE.equals(order.getFragile())) {
            cost += cost * FRAGILE_MULTIPLIER;
        }

        // Вес
        Double weight = order.getDeliveryWeight();
        if (weight != null && weight > 0) {
            cost += weight * WEIGHT_MULTIPLIER;
        }

        // Объем
        Double volume = order.getDeliveryVolume();
        if (volume != null && volume > 0) {
            cost += volume * VOLUME_MULTIPLIER;
        }

        // Адрес доставки (сравниваем улицы)
        String fromStreet = delivery.getFromAddress().getStreet();
        String toStreet = delivery.getToAddress().getStreet();
        if (fromStreet != null && toStreet != null && !fromStreet.equals(toStreet)) {
            cost += cost * ADDRESS_MISMATCH_MULTIPLIER;
        }

        log.info("Delivery cost calculated for order {}: {}", order.getOrderId(), cost);
        return cost;
    }

    @Transactional
    public void deliverySuccessful(UUID orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        // Проверяем, что доставка в процессе
        if (delivery.getDeliveryState() != DeliveryState.IN_PROGRESS) {
            throw new IllegalStateException("Delivery must be IN_PROGRESS to mark as successful, current state: " + delivery.getDeliveryState());
        }

        delivery.setDeliveryState(DeliveryState.DELIVERED);
        deliveryRepository.save(delivery);

        // Уведомляем order
        orderClient.delivery(orderId);

        log.info("Delivery successful for order: {}", orderId);
    }

    @Transactional
    public void deliveryPicked(UUID orderId) {
        Delivery delivery = deliveryRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Delivery not found for order: " + orderId));

        // Проверяем, что доставка создана
        if (delivery.getDeliveryState() != DeliveryState.CREATED) {
            throw new IllegalStateException("Delivery must be CREATED to start, current state: " + delivery.getDeliveryState());
        }

        delivery.setDeliveryState(DeliveryState.IN_PROGRESS);
        delivery = deliveryRepository.save(delivery);

        // Уведомляем warehouse, что товары переданы в доставку
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

        // Проверяем, что доставка в процессе или создана
        if (delivery.getDeliveryState() != DeliveryState.CREATED && delivery.getDeliveryState() != DeliveryState.IN_PROGRESS) {
            throw new IllegalStateException("Delivery must be CREATED or IN_PROGRESS to fail, current state: " + delivery.getDeliveryState());
        }

        delivery.setDeliveryState(DeliveryState.FAILED);
        deliveryRepository.save(delivery);

        // Уведомляем order
        orderClient.deliveryFailed(orderId);

        log.info("Delivery failed for order: {}", orderId);
    }
}