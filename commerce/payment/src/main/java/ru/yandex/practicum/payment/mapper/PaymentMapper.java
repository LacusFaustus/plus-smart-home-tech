package ru.yandex.practicum.payment.mapper;

import lombok.experimental.UtilityClass;
import ru.yandex.practicum.dto.payment.PaymentDto;
import ru.yandex.practicum.payment.model.Payment;

@UtilityClass
public class PaymentMapper {
    public PaymentDto toDto(Payment payment) {
        if (payment == null) return null;

        return PaymentDto.builder()
                .paymentId(payment.getPaymentId())
                .totalPayment(payment.getTotalPayment())
                .deliveryTotal(payment.getDeliveryTotal())
                .feeTotal(payment.getFeeTotal())
                .state(payment.getState() != null ? payment.getState().name() : null)
                .build();
    }
}