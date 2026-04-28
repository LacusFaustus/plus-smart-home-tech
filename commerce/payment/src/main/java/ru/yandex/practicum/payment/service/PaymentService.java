package ru.yandex.practicum.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.payment.PaymentDto;
import ru.yandex.practicum.dto.shoppingstore.ProductDto;
import ru.yandex.practicum.enums.PaymentState;
import ru.yandex.practicum.payment.client.OrderPaymentFeignClient;
import ru.yandex.practicum.payment.client.ShoppingStorePaymentFeignClient;
import ru.yandex.practicum.payment.mapper.PaymentMapper;
import ru.yandex.practicum.payment.model.Payment;
import ru.yandex.practicum.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final ShoppingStorePaymentFeignClient shoppingStoreClient;
    private final OrderPaymentFeignClient orderClient;

    private static final BigDecimal VAT_RATE = new BigDecimal("0.1");

    public BigDecimal productCost(OrderDto order) {
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, Long> entry : order.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            ProductDto product = shoppingStoreClient.getProduct(productId);
            BigDecimal productPrice = product.getPrice();
            BigDecimal itemTotal = productPrice.multiply(BigDecimal.valueOf(quantity));
            total = total.add(itemTotal);
        }
        log.info("Product cost calculated: {}", total);
        return total;
    }

    public BigDecimal getTotalCost(OrderDto order) {
        BigDecimal productCost = productCost(order);
        BigDecimal deliveryPrice = order.getDeliveryPrice() != null
                ? BigDecimal.valueOf(order.getDeliveryPrice())
                : BigDecimal.ZERO;

        BigDecimal vat = productCost.multiply(VAT_RATE);
        BigDecimal total = productCost.add(vat).add(deliveryPrice);

        log.info("Total cost calculated: products={}, vat={}, delivery={}, total={}",
                productCost, vat, deliveryPrice, total);
        return total;
    }

    @Transactional
    public PaymentDto paying(OrderDto order) {
        BigDecimal productCost = productCost(order);
        BigDecimal deliveryPrice = order.getDeliveryPrice() != null
                ? BigDecimal.valueOf(order.getDeliveryPrice())
                : BigDecimal.ZERO;
        BigDecimal vat = productCost.multiply(VAT_RATE);
        BigDecimal total = productCost.add(vat).add(deliveryPrice);

        Payment payment = Payment.builder()
                .orderId(order.getOrderId())
                .totalPayment(total)
                .deliveryTotal(deliveryPrice)
                .feeTotal(vat)
                .state(PaymentState.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created payment {} for order {}: total={}",
                payment.getPaymentId(), order.getOrderId(), total);

        return paymentMapper.toDto(payment);
    }

    @Transactional
    public void paySuccess(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        payment.setState(PaymentState.SUCCESS);
        paymentRepository.save(payment);

        orderClient.payment(payment.getOrderId());
        log.info("Payment successful: {}", paymentId);
    }

    @Transactional
    public void payFailed(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        payment.setState(PaymentState.FAILED);
        paymentRepository.save(payment);

        orderClient.paymentFailed(payment.getOrderId());
        log.info("Payment failed: {}", paymentId);
    }
}