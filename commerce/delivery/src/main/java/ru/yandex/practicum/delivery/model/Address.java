package ru.yandex.practicum.delivery.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Address {
    private String country;
    private String city;
    private String street;
    private String house;
    private String flat;
}