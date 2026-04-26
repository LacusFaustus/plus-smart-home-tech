package ru.yandex.practicum.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.payment.PaymentDto;

import java.util.UUID;

@FeignClient(name = "payment")
public interface PaymentClient {

    @PostMapping("/api/v1/pay")
    PaymentDto paying(@RequestBody OrderDto order);

    @PostMapping("/api/v1/paying/totalCost")
    Double getTotalCost(@RequestBody OrderDto order);

    @PostMapping("/api/v1/pay/productCost")
    Double productCost(@RequestBody OrderDto order);

    @PostMapping("/api/v1/paying/refund")
    void paySuccess(@RequestBody UUID paymentId);

    @PostMapping("/api/v1/paying/failed")
    void payFailed(@RequestBody UUID paymentId);
}