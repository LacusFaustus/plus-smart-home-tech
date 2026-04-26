package ru.yandex.practicum.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.payment.PaymentDto;
import ru.yandex.practicum.dto.shoppingstore.ProductDto;
import ru.yandex.practicum.dto.exceptions.NotEnoughInfoInOrderToCalculateException;
import ru.yandex.practicum.enums.PaymentState;
import ru.yandex.practicum.payment.client.OrderPaymentFeignClient;
import ru.yandex.practicum.payment.client.ShoppingStorePaymentFeignClient;
import ru.yandex.practicum.payment.mapper.PaymentMapper;
import ru.yandex.practicum.payment.model.Payment;
import ru.yandex.practicum.payment.repository.PaymentRepository;

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

    public Double productCost(OrderDto order) {
        if (order.getProducts() == null || order.getProducts().isEmpty()) {
            throw new NotEnoughInfoInOrderToCalculateException(order.getOrderId(), "products");
        }

        double total = 0.0;
        for (Map.Entry<UUID, Long> entry : order.getProducts().entrySet()) {
            UUID productId = entry.getKey();
            Long quantity = entry.getValue();

            if (quantity == null || quantity <= 0) {
                throw new IllegalArgumentException("Invalid quantity for product: " + productId);
            }

            ProductDto product = shoppingStoreClient.getProduct(productId);
            if (product.getPrice() == null) {
                throw new NotEnoughInfoInOrderToCalculateException(order.getOrderId(), "price for product " + productId);
            }

            total += product.getPrice().doubleValue() * quantity;
        }
        log.info("Product cost calculated for order {}: {}", order.getOrderId(), total);
        return total;
    }

    public Double getTotalCost(OrderDto order) {
        if (order.getDeliveryPrice() == null) {
            throw new NotEnoughInfoInOrderToCalculateException(order.getOrderId(), "delivery price");
        }

        double productCost = productCost(order);
        double deliveryPrice = order.getDeliveryPrice();
        // НДС 10% от стоимости товаров
        double vat = productCost * 0.1;
        double total = productCost + vat + deliveryPrice;

        log.info("Total cost calculated for order {}: products={}, vat={}, delivery={}, total={}",
                order.getOrderId(), productCost, vat, deliveryPrice, total);
        return total;
    }

    @Transactional
    public PaymentDto paying(OrderDto order) {
        // Проверяем, что платеж еще не создан для этого заказа
        if (paymentRepository.findByOrderId(order.getOrderId()).isPresent()) {
            throw new IllegalStateException("Payment already exists for order: " + order.getOrderId());
        }

        double productCost = productCost(order);
        double deliveryPrice = order.getDeliveryPrice() != null ? order.getDeliveryPrice() : 0.0;
        double vat = productCost * 0.1;
        double total = productCost + vat + deliveryPrice;

        Payment payment = Payment.builder()
                .orderId(order.getOrderId())
                .totalPayment(total)
                .deliveryTotal(deliveryPrice)
                .feeTotal(vat)
                .state(PaymentState.PENDING)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Created payment {} for order {}: total={}", payment.getPaymentId(), order.getOrderId(), total);

        return paymentMapper.toDto(payment);
    }

    @Transactional
    public void paySuccess(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        // Проверяем, что платеж в ожидании
        if (payment.getState() != PaymentState.PENDING) {
            throw new IllegalStateException("Payment must be PENDING to mark as SUCCESS, current state: " + payment.getState());
        }

        payment.setState(PaymentState.SUCCESS);
        paymentRepository.save(payment);

        // Уведомляем order об успешной оплате
        orderClient.payment(payment.getOrderId());

        log.info("Payment successful: {}", paymentId);
    }

    @Transactional
    public void payFailed(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found: " + paymentId));

        // Проверяем, что платеж в ожидании
        if (payment.getState() != PaymentState.PENDING) {
            throw new IllegalStateException("Payment must be PENDING to mark as FAILED, current state: " + payment.getState());
        }

        payment.setState(PaymentState.FAILED);
        paymentRepository.save(payment);

        // Уведомляем order об ошибке оплаты
        orderClient.paymentFailed(payment.getOrderId());

        log.info("Payment failed: {}", paymentId);
    }
}