package ru.yandex.practicum.store.mapper;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.dto.shopping-store.ProductDto;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.store.model.Category;
import ru.yandex.practicum.store.model.Product;
import ru.yandex.practicum.store.repository.CategoryRepository;

@Component
@RequiredArgsConstructor
public class ProductMapper {
    private final CategoryRepository categoryRepository;

    public ProductDto toDto(Product product) {
        if (product == null) return null;
        return ProductDto.builder()
                .productId(product.getProductId())
                .productName(product.getProductName())
                .description(product.getDescription())
                .imageSrc(product.getImageSrc())
                .quantityState(product.getQuantityState())
                .productState(product.getProductState())
                .productCategory(product.getCategory() != null ? product.getCategory().getName() : null)
                .price(product.getPrice())
                .build();
    }

    public Product toEntity(ProductDto dto) {
        if (dto == null) return null;
        Category category = null;
        if (dto.getProductCategory() != null) {
            category = categoryRepository.findByName(dto.getProductCategory())
                    .orElseThrow(() -> new RuntimeException("Category not found: " + dto.getProductCategory()));
        }
        return Product.builder()
                .productId(dto.getProductId())
                .productName(dto.getProductName())
                .description(dto.getDescription())
                .imageSrc(dto.getImageSrc())
                .quantityState(dto.getQuantityState() != null ? dto.getQuantityState() : ru.yandex.practicum.enums.QuantityState.ENDED)
                .productState(dto.getProductState() != null ? dto.getProductState() : ru.yandex.practicum.enums.ProductState.ACTIVE)
                .category(category)
                .price(dto.getPrice())
                .build();
    }
}