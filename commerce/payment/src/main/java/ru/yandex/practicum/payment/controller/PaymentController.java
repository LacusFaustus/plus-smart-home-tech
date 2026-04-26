package ru.yandex.practicum.payment.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.PaymentClient;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.payment.PaymentDto;
import ru.yandex.practicum.payment.service.PaymentService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class PaymentController implements PaymentClient {
    private final PaymentService paymentService;

    @Override
    @PostMapping("/pay")
    public PaymentDto paying(@RequestBody OrderDto order) {
        log.info("POST /api/v1/pay: orderId={}", order.getOrderId());
        return paymentService.paying(order);
    }

    @Override
    @PostMapping("/paying/totalCost")
    public Double getTotalCost(@RequestBody OrderDto order) {
        log.info("POST /api/v1/paying/totalCost: orderId={}", order.getOrderId());
        return paymentService.getTotalCost(order);
    }

    @Override
    @PostMapping("/pay/productCost")
    public Double productCost(@RequestBody OrderDto order) {
        log.info("POST /api/v1/pay/productCost: orderId={}", order.getOrderId());
        return paymentService.productCost(order);
    }

    @Override
    @PostMapping("/paying/refund")
    public void paySuccess(@RequestBody UUID paymentId) {
        log.info("POST /api/v1/paying/refund: paymentId={}", paymentId);
        paymentService.paySuccess(paymentId);
    }

    @Override
    @PostMapping("/paying/failed")
    public void payFailed(@RequestBody UUID paymentId) {
        log.info("POST /api/v1/paying/failed: paymentId={}", paymentId);
        paymentService.payFailed(paymentId);
    }
}