package ru.yandex.practicum.store.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.yandex.practicum.client.ShoppingStoreClient;
import ru.yandex.practicum.dto.shoppingstore.ProductDto;
import ru.yandex.practicum.dto.shoppingstore.SetProductQuantityStateRequest;
import ru.yandex.practicum.dto.shoppingstore.PageProductDto;
import ru.yandex.practicum.dto.exceptions.ProductNotFoundException;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.store.service.ProductService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shopping-store")
@RequiredArgsConstructor
@Slf4j
public class ProductController implements ShoppingStoreClient {
    private final ProductService productService;

    @Override
    @GetMapping
    public PageProductDto getProducts(
            @RequestParam("category") String category,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("sort") List<String> sort) {
        log.info("GET /api/v1/shopping-store: category={}, page={}, size={}, sort={}", category, page, size, sort);
        ProductCategory productCategory;
        try {
            productCategory = ProductCategory.valueOf(category);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid category: " + category);
        }
        Sort sortObj = parseSort(sort);
        return productService.getProducts(productCategory, PageRequest.of(page, size, sortObj));
    }

    @Override
    @GetMapping("/{productId}")
    public ProductDto getProduct(@PathVariable("productId") UUID productId) {
        log.info("GET /api/v1/shopping-store/{}", productId);
        return productService.getProduct(productId);
    }

    @Override
    @PutMapping
    public ProductDto createNewProduct(@RequestBody ProductDto product) {
        log.info("PUT /api/v1/shopping-store: {}", product);
        return productService.createProduct(product);
    }

    @Override
    @PostMapping
    public ProductDto updateProduct(@RequestBody ProductDto product) {
        log.info("POST /api/v1/shopping-store: {}", product);
        return productService.updateProduct(product);
    }

    @Override
    @PostMapping("/removeProductFromStore")
    public boolean removeProductFromStore(@RequestBody UUID productId) {
        log.info("POST /api/v1/shopping-store/removeProductFromStore: {}", productId);
        productService.deactivateProduct(productId);
        return true;
    }

    @Override
    @PostMapping("/quantityState")
    public boolean setProductQuantityState(@RequestBody SetProductQuantityStateRequest request) {
        log.info("POST /api/v1/shopping-store/quantityState: {}", request);
        productService.updateQuantityState(request.getProductId(), request.getQuantityState());
        return true;
    }

    private Sort parseSort(List<String> sortParams) {
        log.info("parseSort called with: {}", sortParams);
        if (sortParams == null || sortParams.isEmpty()) {
            return Sort.unsorted();
        }

        List<Sort.Order> orders = new ArrayList<>();

        // Проходим по параметрам, объединяя пары (поле, направление)
        for (int i = 0; i < sortParams.size(); i++) {
            String param = sortParams.get(i);
            if (param == null || param.trim().isEmpty()) {
                continue;
            }

            String property = param.trim();
            Sort.Direction direction = Sort.Direction.ASC;

            // Если следующий параметр - это направление (ASC/DESC)
            if (i + 1 < sortParams.size()) {
                String nextParam = sortParams.get(i + 1).trim().toUpperCase();
                if (nextParam.equals("ASC") || nextParam.equals("DESC")) {
                    direction = Sort.Direction.fromString(nextParam);
                    i++; // Пропускаем следующий параметр, так как мы его использовали
                }
            }

            log.info("Adding order: property={}, direction={}", property, direction);
            orders.add(new Sort.Order(direction, property));
        }

        Sort result = orders.isEmpty() ? Sort.unsorted() : Sort.by(orders);
        log.info("Final Sort: {}", result);
        return result;
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        log.error("Product not found: {}", ex.getMessage());
        return new ErrorResponse(ex.getUserMessage());
    }

    record ErrorResponse(String message) {}
}