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

import java.math.BigDecimal;
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

        AddressDto warehouseAddress = warehouseClient.getWarehouseAddress();

        DeliveryDto deliveryRequest = DeliveryDto.builder()
                .fromAddress(warehouseAddress)
                .toAddress(request.getDeliveryAddress())
                .orderId(null)
                .build();

        Order order = Order.builder()
                .shoppingCartId(cart.getShoppingCartId())
                .products(cart.getProducts())
                .state(OrderState.NEW)
                .build();

        order = orderRepository.save(order);

        deliveryRequest.setOrderId(order.getOrderId());
        DeliveryDto delivery = deliveryClient.planDelivery(deliveryRequest);

        OrderDto orderDto = orderMapper.toDto(order);
        orderDto.setDeliveryPrice(0.0);
        PaymentDto payment = paymentClient.paying(orderDto);

        order.setDeliveryId(delivery.getDeliveryId());
        order.setPaymentId(payment.getPaymentId());
        order.setState(OrderState.ON_PAYMENT);

        order = orderRepository.save(order);
        log.info("Created order: {}", order.getOrderId());

        return orderMapper.toDto(order);
    }

    public List<OrderDto> getClientOrders(String username) {
        ShoppingCartDto cart = shoppingCartClient.getShoppingCart(username);
        List<Order> orders = orderRepository.findByShoppingCartId(cart.getShoppingCartId());
        return orders.stream().map(orderMapper::toDto).collect(Collectors.toList());
    }

    @Transactional
    public OrderDto payment(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        order.setState(OrderState.ON_PAYMENT);
        order = orderRepository.save(order);

        OrderDto orderDto = orderMapper.toDto(order);
        paymentClient.paying(orderDto);

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

        OrderDto orderDto = orderMapper.toDto(order);
        BigDecimal deliveryCost = deliveryClient.deliveryCost(orderDto);

        order.setDeliveryPrice(deliveryCost.doubleValue());
        order = orderRepository.save(order);

        log.info("Delivery cost calculated for order {}: {}", orderId, deliveryCost);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto calculateTotalCost(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

        OrderDto orderDto = orderMapper.toDto(order);

        BigDecimal productCost = paymentClient.productCost(orderDto);
        BigDecimal totalCost = paymentClient.getTotalCost(orderDto);

        order.setProductPrice(productCost.doubleValue());
        order.setTotalPrice(totalCost.doubleValue());
        order = orderRepository.save(order);

        log.info("Total cost calculated for order {}: total={}, products={}", orderId, totalCost, productCost);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto delivery(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoOrderFoundException(orderId));

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

        order.setState(OrderState.COMPLETED);
        order = orderRepository.save(order);

        log.info("Order completed: {}", orderId);
        return orderMapper.toDto(order);
    }

    @Transactional
    public OrderDto productReturn(ProductReturnRequest request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new NoOrderFoundException(request.getOrderId()));

        order.setState(OrderState.PRODUCT_RETURNED);
        order = orderRepository.save(order);

        log.info("Product return for order: {}", request.getOrderId());
        return orderMapper.toDto(order);
    }
}