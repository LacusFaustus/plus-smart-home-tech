package ru.yandex.practicum.cart.fallback;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.client.WarehouseClient;
import ru.yandex.practicum.dto.shoppingcart.ShoppingCartDto;
import ru.yandex.practicum.dto.warehouse.*;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class WarehouseFallback implements WarehouseClient {

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

    @Override
    public BookedProductsDto assemblyProductsForOrder(AssemblyProductsForOrderRequest request) {
        log.error("Fallback: assemblyProductsForOrder called when warehouse is unavailable");
        throw new RuntimeException("Warehouse service is temporarily unavailable");
    }

    @Override
    public void shippedToDelivery(ShippedToDeliveryRequest request) {
        log.error("Fallback: shippedToDelivery called when warehouse is unavailable");
        throw new RuntimeException("Warehouse service is temporarily unavailable");
    }

    @Override
    public void acceptReturn(Map<UUID, Long> products) {
        log.error("Fallback: acceptReturn called when warehouse is unavailable");
        throw new RuntimeException("Warehouse service is temporarily unavailable");
    }
}