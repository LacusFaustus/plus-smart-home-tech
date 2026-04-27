package ru.yandex.practicum.delivery.mapper;

import lombok.experimental.UtilityClass;
import ru.yandex.practicum.delivery.model.Address;
import ru.yandex.practicum.delivery.model.Delivery;
import ru.yandex.practicum.dto.delivery.DeliveryDto;
import ru.yandex.practicum.enums.DeliveryState;

@UtilityClass
public class DeliveryMapper {

    public DeliveryDto toDto(Delivery delivery) {
        if (delivery == null) {
            return null;
        }

        return DeliveryDto.builder()
                .deliveryId(delivery.getDeliveryId())
                .fromAddress(toAddressDto(delivery.getFromAddress()))
                .toAddress(toAddressDto(delivery.getToAddress()))
                .orderId(delivery.getOrderId())
                .deliveryState(delivery.getDeliveryState() != null ? delivery.getDeliveryState().name() : null)
                .build();
    }

    public Delivery toEntity(DeliveryDto dto) {
        if (dto == null) {
            return null;
        }

        Delivery delivery = Delivery.builder()
                .deliveryId(dto.getDeliveryId())
                .fromAddress(toAddressEntity(dto.getFromAddress()))
                .toAddress(toAddressEntity(dto.getToAddress()))
                .orderId(dto.getOrderId())
                .build();

        if (dto.getDeliveryState() != null) {
            delivery.setDeliveryState(DeliveryState.valueOf(dto.getDeliveryState()));
        }

        return delivery;
    }

    private ru.yandex.practicum.dto.warehouse.AddressDto toAddressDto(Address address) {
        if (address == null) {
            return null;
        }
        return ru.yandex.practicum.dto.warehouse.AddressDto.builder()
                .country(address.getCountry())
                .city(address.getCity())
                .street(address.getStreet())
                .house(address.getHouse())
                .flat(address.getFlat())
                .build();
    }

    private Address toAddressEntity(ru.yandex.practicum.dto.warehouse.AddressDto dto) {
        if (dto == null) {
            return null;
        }
        return Address.builder()
                .country(dto.getCountry())
                .city(dto.getCity())
                .street(dto.getStreet())
                .house(dto.getHouse())
                .flat(dto.getFlat())
                .build();
    }
}