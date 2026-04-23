package ru.yandex.practicum.warehouse.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.dto.warehouse.AddressDto;

import java.security.SecureRandom;
import java.util.Random;

@Service
@Slf4j
public class AddressService {
    private static final String[] ADDRESSES = new String[] {"ADDRESS_1", "ADDRESS_2"};
    private static final String CURRENT_ADDRESS = ADDRESSES[new Random().nextInt(ADDRESSES.length)];

    static {
        log.info("Warehouse initialized with address: {}", CURRENT_ADDRESS);
    }

    public AddressDto getAddress() {
        return AddressDto.builder()
                .country(CURRENT_ADDRESS)
                .city(CURRENT_ADDRESS)
                .street(CURRENT_ADDRESS)
                .house(CURRENT_ADDRESS)
                .flat(CURRENT_ADDRESS)
                .build();
    }
}