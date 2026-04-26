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
public class PageableObject {
    private long offset;

    @Builder.Default
    private List<SortObject> sort = new ArrayList<>();

    private boolean unpaged;
    private boolean paged;
    private int pageNumber;
    private int pageSize;
}