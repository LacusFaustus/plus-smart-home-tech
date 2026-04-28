package ru.yandex.practicum.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.payment.PaymentDto;

import java.math.BigDecimal;

@FeignClient(name = "payment")
public interface PaymentOrderFeignClient {

    @PostMapping("/api/v1/pay")
    PaymentDto paying(@RequestBody OrderDto order);

    @PostMapping("/api/v1/paying/totalCost")
    BigDecimal getTotalCost(@RequestBody OrderDto order);

    @PostMapping("/api/v1/pay/productCost")
    BigDecimal productCost(@RequestBody OrderDto order);
}