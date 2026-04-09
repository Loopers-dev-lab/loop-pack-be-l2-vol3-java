package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.product.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.SortCondition;
import com.loopers.domain.product.BrandRepository;
import com.loopers.kafka.event.CatalogEvent;
import com.loopers.kafka.topic.KafkaTopics;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Cacheable(value = "product:detail", key = "#productId")
    public ProductDetailInfo getProductDetail(Long productId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + productId + "] 상품을 찾을 수 없습니다."));
        Brand brand = product.getBrandId() != null
            ? brandRepository.findById(product.getBrandId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "브랜드를 찾을 수 없습니다."))
            : null;
        return ProductDetailInfo.of(product, brand, product.getLikesCount());
    }

    /**
     * VIEWED 이벤트 발행 — getProductDetail()과 분리된 이유:
     * getProductDetail()은 @Cacheable이라 캐시 히트 시 메서드 자체가 실행되지 않음.
     * 조회 이벤트는 캐시 히트 여부와 무관하게 매번 발행해야 하므로 별도 메서드로 분리.
     */
    @SneakyThrows
    @Transactional
    public void publishViewedEvent(Long productId) {
        String eventId = UUID.randomUUID().toString();
        CatalogEvent event = CatalogEvent.of(eventId, CatalogEvent.Type.VIEWED, productId, null, Instant.now().toEpochMilli());
        outboxEventRepository.save(OutboxEvent.create(
            eventId, KafkaTopics.CATALOG_EVENTS, String.valueOf(productId),
            objectMapper.writeValueAsString(event)
        ));
    }

    @Cacheable(value = "product:list", key = "#sort.name()")
    public List<ProductListInfo> getProductList(SortCondition sort) {
        List<Product> products = productRepository.findAll(sort);
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> brandIds = products.stream()
            .map(Product::getBrandId)
            .filter(id -> id != null)
            .distinct()
            .toList();
        Map<Long, Brand> brandMap = brandIds.stream()
            .flatMap(id -> brandRepository.findById(id).stream())
            .collect(Collectors.toMap(Brand::getId, b -> b));

        return products.stream()
            .map(p -> {
                Brand brand = p.getBrandId() != null ? brandMap.get(p.getBrandId()) : null;
                return ProductListInfo.of(p, brand, p.getLikesCount());
            })
            .toList();
    }
}
