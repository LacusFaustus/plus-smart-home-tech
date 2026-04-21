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
        if (sortParams == null || sortParams.isEmpty()) {
            return Sort.unsorted();
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (String param : sortParams) {
            // Разделяем по запятой: "productName,DESC" -> ["productName", "DESC"]
            String[] parts = param.split(",");
            String property = parts[0].trim();
            Sort.Direction direction = Sort.Direction.ASC;
            if (parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc")) {
                direction = Sort.Direction.DESC;
            }
            orders.add(new Sort.Order(direction, property));
        }
        return Sort.by(orders);
    }

    @ExceptionHandler(ProductNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleProductNotFound(ProductNotFoundException ex) {
        log.error("Product not found: {}", ex.getMessage());
        return new ErrorResponse(ex.getUserMessage());
    }

    record ErrorResponse(String message) {}
}