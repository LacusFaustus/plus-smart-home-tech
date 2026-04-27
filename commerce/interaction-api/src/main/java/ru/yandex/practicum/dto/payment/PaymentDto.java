package ru.yandex.practicum.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Представление платежа в системе.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentDto {

    /**
     * Уникальный идентификатор платежа.
     */
    private UUID paymentId;

    /**
     * Общая сумма к оплате (включая товары, доставку и налоги).
     */
    private BigDecimal totalPayment;

    /**
     * Стоимость доставки.
     */
    private BigDecimal deliveryTotal;

    /**
     * Сумма налогов и сборов.
     */
    private BigDecimal feeTotal;

    /**
     * Статус платежа.
     * Возможные значения: PENDING, SUCCESS, FAILED
     */
    private String state;
}