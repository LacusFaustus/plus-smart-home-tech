package ru.yandex.practicum.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {
    private UUID paymentId;
    private Double totalPayment;
    private Double deliveryTotal;
    private Double feeTotal;
    private String state;  // PaymentState enum
}