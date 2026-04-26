package ru.yandex.practicum.order.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.client.ShoppingCartClient;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.dto.exceptions.NoOrderFoundException;
import ru.yandex.practicum.dto.order.CreateNewOrderRequest;
import ru.yandex.practicum.dto.order.OrderDto;
import ru.yandex.practicum.dto.order.ProductReturnRequest;
import ru.yandex.practicum.dto.payment.PaymentDto;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.AddressDto;
import ru.yandex.practicum.dto.warehouse.AssemblyProductsForOrderRequest;
import ru.yandex.practicum.dto.warehouse.BookedProductsDto;
import ru.yandex.practicum.enums.OrderState;
import ru.yandex.practicum.order.client.DeliveryOrderFeignClient;
import ru.yandex.practicum.order.client.PaymentOrderFeignClient;
import ru.yandex.practicum.order.client.WarehouseOrderFeignClient;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.model.Order;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ShoppingCartClient shoppingCartClient;
    private final WarehouseOrderFeignClient warehouseClient;
    private final PaymentOrderFeignClient paymentClient;
    private final DeliveryOrderFeignClient deliveryClient;

    @Transactional
    public OrderDto createNewOrder(CreateNewOrderRequest request) {
        ShoppingCartDto cart = request.getShoppingCart();

        // Проверяем, что корзина не пуста
        if (cart.getProducts() == null || cart.getProducts().isEmpty()) {
            throw new IllegalArgumentException("Shopping cart is empty");
        }

        // Получаем адрес склада
        AddressDto warehouseAddress = warehouseClient.getWarehouseAddress();

        // Создаем заказ в статусе NEW
        Order order = Order.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(cart.getProducts())
                .state(OrderState.NEW)
                .build();

        order = orderRepository.save(order);
        log.info("Created order with ID: {} in state NEW", order.getOrderId());

        // Планируем доставку
        DeliveryDto deliveryRequest = DeliveryDto.builder()
                .fromAddress(warehouseAddress)
                .toAddress(request.getDeliveryAddress())
                .orderId(order.getOrderId())
                .build();

        DeliveryDto delivery = deliveryClient.planDelivery(deliveryRequest);
        order.setDeliveryId(delivery.getDeliveryId());
        log.info("Delivery planned for order: {}, deliveryId: {}", order.getOrderId(), delivery.getDeliveryId());

        // Переводим заказ в статус ожидания оплаты
        order.setState(OrderState.ON_PAYMENT);
        order = orderRepository.save(order);

        // Создаем платеж
        OrderDto orderDto = orderMapper.toDto(order);
        PaymentDto payment = paymentClient.paying(orderDto);
        order.setPaymentId(payment.getPaymentId());

        order = orderRepository.save(order);
        log.info("Payment created for order: {}, paymentId: {}", order.getOrderId(), payment.getPaymentId());

        return orderMapper.toDto(order);
    }

    public List<OrderDto> getClientOrders(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username cannot be empty");
        }

        ShoppingCartDto cart = shoppingCartClient.getShoppingCart(username);
        List<Order> orders = orderRepository.findByShoppingCartId(cart.getShoppingCartId());
        log.info("Found {} orders for user: {}", orders.size(), username);
        return orders.stream().map(orderMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public OrderDto payment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что заказ в правильном состоянии
        if (order.getState() != OrderState.ON_PAYMENT) {
            throw new IllegalStateException("Order is not in ON_PAYMENT state: " + order.getState());
        }

        order.setState(OrderState.PAID);
        order = orderRepository.save(order);

        log.info("Payment successful for order: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto paymentFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        order.setState(OrderState.PAYMENT_FAILED);
        order = orderRepository.save(order);

        log.info("Payment failed for order: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto assembly(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что заказ оплачен
        if (order.getState() != OrderState.PAID) {
            throw new IllegalStateException("Order must be PAID before assembly, current state: " + order.getState());
        }

        AssemblyProductsForOrderRequest assemblyRequest = AssemblyProductsForOrderRequest.builder()
                .products(order.getProducts())
                .orderId(orderId)
                .build();

        BookedProductsDto booked = warehouseClient.assemblyProductsForOrder(assemblyRequest);

        order.setDeliveryWeight(booked.getDeliveryWeight());
        order.setDeliveryVolume(booked.getDeliveryVolume());
        order.setFragile(booked.getFragile());
        order.setState(OrderState.ASSEMBLED);

        order = orderRepository.save(order);
        log.info("Assembly completed for order: {}", orderId);

        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto assemblyFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        order.setState(OrderState.ASSEMBLY_FAILED);
        order = orderRepository.save(order);

        log.info("Assembly failed for order: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto calculateDeliveryCost(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что заказ собран
        if (order.getState() != OrderState.ASSEMBLED) {
            throw new IllegalStateException("Order must be ASSEMBLED before calculating delivery cost, current state: " + order.getState());
        }

        OrderDto orderDto = orderMapper.toDto(order);
        Double deliveryCost = deliveryClient.deliveryCost(orderDto);

        order.setDeliveryPrice(deliveryCost);
        order = orderRepository.save(order);

        log.info("Delivery cost calculated for order {}: {}", orderId, deliveryCost);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto calculateTotalCost(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что цена доставки уже рассчитана
        if (order.getDeliveryPrice() == null) {
            throw new IllegalStateException("Delivery price must be calculated before total cost");
        }

        OrderDto orderDto = orderMapper.toDto(order);

        Double productCost = paymentClient.productCost(orderDto);
        Double totalCost = paymentClient.getTotalCost(orderDto);

        order.setProductPrice(productCost);
        order.setTotalPrice(totalCost);
        order = orderRepository.save(order);

        log.info("Total cost calculated for order {}: total={}, products={}", orderId, totalCost, productCost);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto delivery(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что заказ в доставке
        if (order.getState() != OrderState.ON_DELIVERY) {
            throw new IllegalStateException("Order must be ON_DELIVERY before completing delivery, current state: " + order.getState());
        }

        order.setState(OrderState.DELIVERED);
        order = orderRepository.save(order);

        log.info("Delivery completed for order: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto deliveryFailed(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        order.setState(OrderState.DELIVERY_FAILED);
        order = orderRepository.save(order);

        log.info("Delivery failed for order: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto complete(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        // Проверяем, что заказ доставлен
        if (order.getState() != OrderState.DELIVERED) {
            throw new IllegalStateException("Order must be DELIVERED before completion, current state: " + order.getState());
        }

        order.setState(OrderState.COMPLETED);
        order = orderRepository.save(order);

        log.info("Order completed: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto productReturn(ProductReturnRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NoOrderFoundException(request.getOrderId()));

        // Проверяем, что заказ завершен
        if (order.getState() != OrderState.COMPLETED) {
            throw new IllegalStateException("Only COMPLETED orders can be returned, current state: " + order.getState());
        }

        order.setState(OrderState.PRODUCT_RETURNED);
        order = orderRepository.save(order);

        log.info("Product return for order: {}", request.getOrderId());
        return orderMapper.toDto(order);
    }
}