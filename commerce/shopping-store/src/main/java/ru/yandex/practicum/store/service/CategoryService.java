package ru.yandex.practicum.store.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.store.model.Category;
import ru.yandex.practicum.store.repository.CategoryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;

    @PostConstruct
    public void initCategories() {
        for (ProductCategory pc : ProductCategory.values()) {
            if (categoryRepository.findByName(pc).isEmpty()) {
                Category category = Category.builder().name(pc).build();
                categoryRepository.save(category);
                log.info("Created category: {}", pc);
            }
        }
    }

    public Category getCategory(ProductCategory name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Category not found: " + name));
    }
}