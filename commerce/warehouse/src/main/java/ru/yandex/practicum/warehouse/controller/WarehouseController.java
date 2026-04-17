package ru.yandex.practicum.warehouse.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.WarehouseClient;
import ru.yandex.practicum.dto.shopping-cart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.*;
import ru.yandex.practicum.warehouse.service.AddressService;
import ru.yandex.practicum.warehouse.service.WarehouseService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
@Slf4j
public class WarehouseController implements WarehouseClient {
    private final WarehouseService warehouseService;
    private final AddressService addressService;

    @Override
    public void newProductInWarehouse(@RequestBody NewProductInWarehouseRequest request) {
        log.info("PUT /api/v1/warehouse: productId={}", request.getProductId());
        warehouseService.registerNewProduct(request);
    }

    @Override
    public BookedProductsDto checkProductQuantityEnoughForShoppingCart(@RequestBody ShoppingCartDto cart) {
        log.info("POST /api/v1/warehouse/check: cartId={}", cart.getShoppingCartId());
        return warehouseService.checkAvailability(cart);
    }

    @Override
    public void addProductToWarehouse(@RequestBody AddProductToWarehouseRequest request) {
        log.info("POST /api/v1/warehouse/add: productId={}, quantity={}", request.getProductId(), request.getQuantity());
        warehouseService.addStock(request.getProductId(), request.getQuantity());
    }

    @Override
    public AddressDto getWarehouseAddress() {
        log.info("GET /api/v1/warehouse/address");
        return addressService.getAddress();
    }
}