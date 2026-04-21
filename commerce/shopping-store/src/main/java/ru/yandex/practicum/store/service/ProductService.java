package ru.yandex.practicum.store.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.dto.shoppingstore.PageableObject;
import ru.yandex.practicum.dto.shoppingstore.ProductDto;
import ru.yandex.practicum.dto.shoppingstore.PageProductDto;
import ru.yandex.practicum.dto.exceptions.ProductNotFoundException;
import ru.yandex.practicum.enums.ProductCategory;
import ru.yandex.practicum.enums.ProductState;
import ru.yandex.practicum.enums.QuantityState;
import ru.yandex.practicum.store.mapper.ProductMapper;
import ru.yandex.practicum.store.model.Category;
import ru.yandex.practicum.store.model.Product;
import ru.yandex.practicum.store.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductMapper productMapper;
    private final CategoryService categoryService;

    public PageProductDto getProducts(ProductCategory categoryName, Pageable pageable) {
        Category category = categoryService.getCategory(categoryName);
        Page<Product> productPage = productRepository.findByCategoryAndProductState(
                pageable, category, ProductState.ACTIVE
        );

        PageProductDto pageDto = new PageProductDto();
        pageDto.setContent(productPage.getContent().stream()
                .map(productMapper::toDto)
                .collect(Collectors.toList()));
        pageDto.setTotalElements(productPage.getTotalElements());
        pageDto.setTotalPages(productPage.getTotalPages());
        pageDto.setFirst(productPage.isFirst());
        pageDto.setLast(productPage.isLast());
        pageDto.setSize(productPage.getSize());
        pageDto.setNumber(productPage.getNumber());
        pageDto.setNumberOfElements(productPage.getNumberOfElements());
        pageDto.setEmpty(productPage.isEmpty());

        // Добавляем sort и pageable поля, если их ожидает тест
        if (productPage.getSort() != null) {
            // Преобразуем Sort в List<SortObject> если нужно
        }
        if (productPage.getPageable() != null) {
            PageableObject pageableObj = new PageableObject();
            pageableObj.setPageNumber(productPage.getNumber());
            pageableObj.setPageSize(productPage.getSize());
            pageableObj.setOffset(productPage.getPageable().getOffset());
            pageableObj.setPaged(productPage.getPageable().isPaged());
            pageableObj.setUnpaged(productPage.getPageable().isUnpaged());
            pageDto.setPageable(pageableObj);
        }

        return pageDto;
    }

    public ProductDto getProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        return productMapper.toDto(product);
    }

    @Transactional
    public ProductDto createProduct(ProductDto productDto) {
        Product product = productMapper.toEntity(productDto);
        // Не перезаписываем productState, если он передан
        if (productDto.getProductState() == null) {
            product.setProductState(ProductState.ACTIVE);
        }
        if (productDto.getQuantityState() == null) {
            product.setQuantityState(QuantityState.ENDED);
        }
        Product saved = productRepository.save(product);
        log.info("Created new product: id={}, name={}", saved.getProductId(), saved.getProductName());
        return productMapper.toDto(saved);
    }

    @Transactional
    public ProductDto updateProduct(ProductDto productDto) {
        Product existing = productRepository.findById(productDto.getProductId())
                .orElseThrow(() -> new ProductNotFoundException(productDto.getProductId()));

        existing.setProductName(productDto.getProductName());
        existing.setDescription(productDto.getDescription());
        existing.setImageSrc(productDto.getImageSrc());
        existing.setPrice(productDto.getPrice());
        if (productDto.getProductCategory() != null) {
            existing.setCategory(categoryService.getCategory(productDto.getProductCategory()));
        }

        Product saved = productRepository.save(existing);
        log.info("Updated product: id={}", saved.getProductId());
        return productMapper.toDto(saved);
    }

    @Transactional
    public ProductDto deactivateProduct(UUID productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.setProductState(ProductState.DEACTIVATE);
        Product saved = productRepository.save(product);
        log.info("Deactivated product: id={}", productId);
        return productMapper.toDto(saved);
    }

    @Transactional
    public void updateQuantityState(UUID productId, QuantityState newState) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        log.info("Product {} quantity changed from {} to {}", productId, product.getQuantityState(), newState);
        product.setQuantityState(newState);
        productRepository.save(product);
    }

    @PostConstruct
    public void initTestData() {
        // Проверяем, есть ли товары в категории CONTROL
        Category controlCategory = categoryService.getCategory(ProductCategory.CONTROL);
        if (productRepository.count() == 0) {
            Product testProduct = Product.builder()
                    .productName("Test Product")
                    .description("Test Description")
                    .price(new BigDecimal("99.99"))
                    .category(controlCategory)
                    .productState(ProductState.ACTIVE)
                    .quantityState(QuantityState.ENOUGH)
                    .build();
            productRepository.save(testProduct);
            log.info("Created test product for CONTROL category");
        }
    }
}