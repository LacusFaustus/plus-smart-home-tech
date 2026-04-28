package ru.yandex.practicum.warehouse.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.WarehouseClient;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.*;
import ru.yandex.practicum.warehouse.service.AddressService;
import ru.yandex.practicum.warehouse.service.WarehouseService;
import ru.yandex.practicum.warehouse.service.OrderBookingService;  // ← импорт

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
@Slf4j
public class WarehouseController implements WarehouseClient {
    private final WarehouseService warehouseService;
    private final AddressService addressService;
    private final OrderBookingService orderBookingService;  // ← добавлено

    @Override
    @PutMapping
    public void newProductInWarehouse(@RequestBody NewProductInWarehouseRequest request) {
        log.info("PUT /api/v1/warehouse: productId={}", request.getProductId());
        warehouseService.registerNewProduct(request);
    }

    @Override
    @PostMapping("/check")
    public BookedProductsDto checkProductQuantityEnoughForShoppingCart(@RequestBody ShoppingCartDto cart) {
        log.info("POST /api/v1/warehouse/check: cartId={}", cart.getShoppingCartId());
        return warehouseService.checkAvailability(cart);
    }

    @Override
    @PostMapping("/add")
    public void addProductToWarehouse(@RequestBody AddProductToWarehouseRequest request) {
        log.info("POST /api/v1/warehouse/add: productId={}, quantity={}", request.getProductId(), request.getQuantity());
        warehouseService.addStock(request.getProductId(), request.getQuantity());
    }

    @Override
    @GetMapping("/address")
    public AddressDto getWarehouseAddress() {
        log.info("GET /api/v1/warehouse/address");
        return addressService.getAddress();
    }

    @Override
    @PostMapping("/assembly")
    public BookedProductsDto assemblyProductsForOrder(@RequestBody AssemblyProductsForOrderRequest request) {
        log.info("POST /api/v1/warehouse/assembly: orderId={}", request.getOrderId());
        return orderBookingService.assembleOrder(request);
    }

    @Override
    @PostMapping("/shipped")
    public void shippedToDelivery(@RequestBody ShippedToDeliveryRequest request) {
        log.info("POST /api/v1/warehouse/shipped: orderId={}, deliveryId={}", request.getOrderId(), request.getDeliveryId());
        orderBookingService.updateDeliveryId(request.getOrderId(), request.getDeliveryId());
    }

    @Override
    @PostMapping("/return")
    public void acceptReturn(@RequestBody Map<UUID, Long> products) {
        log.info("POST /api/v1/warehouse/return: products={}", products);
        warehouseService.returnProducts(products);
    }
}