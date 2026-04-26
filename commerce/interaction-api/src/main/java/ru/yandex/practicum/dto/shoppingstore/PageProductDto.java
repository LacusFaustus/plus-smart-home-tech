package ru.yandex.practicum.dto.shoppingstore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageProductDto {
    @Builder.Default
    private List<ProductDto> content = new ArrayList<>();

    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private int size;
    private int number;
    private int numberOfElements;
    private boolean empty;

    @Builder.Default
    private PageableObject pageable = new PageableObject();

    @Builder.Default
    private List<SortObject> sort = new ArrayList<>();
}