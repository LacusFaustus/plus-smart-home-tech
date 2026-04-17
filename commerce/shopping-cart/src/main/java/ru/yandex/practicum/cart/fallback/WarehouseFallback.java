package ru.yandex.practicum.cart.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.dto.shopping-cart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.*;
import ru.yandex.practicum.cart.client.WarehouseFeignClient;

@Component
@Slf4j
public class WarehouseFallback implements WarehouseFeignClient {

    @Override
    public void newProductInWarehouse(NewProductInWarehouseRequest request) {
        log.error("Fallback: newProductInWarehouse called when warehouse is unavailable");
        throw new RuntimeException("Warehouse service is temporarily unavailable");
    }

    @Override
    public BookedProductsDto checkProductQuantityEnoughForShoppingCart(ShoppingCartDto cart) {
        log.warn("Fallback: Warehouse service unavailable, proceeding without stock check for cart {}",
                cart.getShoppingCartId());
        return BookedProductsDto.builder()
                .deliveryWeight(0.0)
                .deliveryVolume(0.0)
                .fragile(false)
                .build();
    }

    @Override
    public void addProductToWarehouse(AddProductToWarehouseRequest request) {
        log.error("Fallback: addProductToWarehouse called when warehouse is unavailable");
        throw new RuntimeException("Warehouse service is temporarily unavailable");
    }

    @Override
    public AddressDto getWarehouseAddress() {
        log.error("Fallback: getWarehouseAddress called when warehouse is unavailable");
        return AddressDto.builder()
                .country("UNKNOWN")
                .city("UNKNOWN")
                .street("UNKNOWN")
                .house("UNKNOWN")
                .flat("UNKNOWN")
                .build();
    }
}