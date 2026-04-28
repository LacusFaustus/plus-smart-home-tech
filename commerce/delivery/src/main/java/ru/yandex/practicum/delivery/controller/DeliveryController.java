package ru.yandex.practicum.delivery.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.DeliveryClient;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.delivery.service.DeliveryService;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
@Slf4j
public class DeliveryController implements DeliveryClient {
    private final DeliveryService deliveryService;

    @Override
    @PostMapping
    public DeliveryDto planDelivery(@RequestBody DeliveryDto delivery) {
        log.info("POST /api/v1/delivery: orderId={}", delivery.getOrderId());
        return deliveryService.planDelivery(delivery);
    }

    @Override
    @PostMapping("/cost")
    public BigDecimal deliveryCost(@RequestBody OrderDto order) {
        log.info("POST /api/v1/delivery/cost: orderId={}", order.getOrderId());
        return deliveryService.deliveryCost(order);
    }

    @Override
    @PostMapping("/successful")
    public void deliverySuccessful(@RequestBody UUID orderId) {
        log.info("POST /api/v1/delivery/successful: orderId={}", orderId);
        deliveryService.deliverySuccessful(orderId);
    }

    @Override
    @PostMapping("/picked")
    public void deliveryPicked(@RequestBody UUID orderId) {
        log.info("POST /api/v1/delivery/picked: orderId={}", orderId);
        deliveryService.deliveryPicked(orderId);
    }

    @Override
    @PostMapping("/failed")
    public void deliveryFailed(@RequestBody UUID orderId) {
        log.info("POST /api/v1/delivery/failed: orderId={}", orderId);
        deliveryService.deliveryFailed(orderId);
    }
}