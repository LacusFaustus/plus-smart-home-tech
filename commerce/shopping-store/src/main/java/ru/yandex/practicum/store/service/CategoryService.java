package ru.yandex.practicum.store.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.store.model.Category;
import ru.yandex.practicum.store.repository.CategoryRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {
    private final CategoryRepository categoryRepository;

    @PostConstruct
    @Transactional
    public void initCategories() {
        for (ProductCategory pc : ProductCategory.values()) {
            try {
                if (categoryRepository.findByName(pc).isEmpty()) {
                    Category category = Category.builder().name(pc).build();
                    categoryRepository.save(category);
                    log.info("Created category: {}", pc);
                }
            } catch (Exception e) {
                log.warn("Could not create category {}: {}", pc, e.getMessage());
            }
        }
    }

    public Category getCategory(ProductCategory name) {
        return categoryRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Category not found: " + name));
    }
}