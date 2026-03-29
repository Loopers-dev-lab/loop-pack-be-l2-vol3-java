package com.loopers.interfaces.api.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.outbox.KafkaOutboxMessage;
import com.loopers.domain.outbox.OutboxEventTopics;
import com.loopers.domain.product.ProductViewedEvent;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZonedDateTime;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductsV1Controller implements ProductsV1ApiSpec {

    private final ProductFacade productFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @GetMapping("")
    @Override
    public ApiResponse<ProductV1Dto.PageResponse<ProductV1Dto.ProductListItemResponse>> getProductList(
        @RequestParam(required = false) Long brandId,
        @RequestParam(required = false, defaultValue = "latest") String sort,
        @RequestParam(required = false, defaultValue = "0") int page,
        @RequestParam(required = false, defaultValue = "20") int size
    ) {
        Page<ProductV1Dto.ProductListItemResponse> responsePage = productFacade.getProductList(brandId, sort, page, size)
            .toPage()
            .map(ProductV1Dto.ProductListItemResponse::from);
        return ApiResponse.success(ProductV1Dto.PageResponse.from(responsePage));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<ProductV1Dto.ProductDetailResponse> getProduct(
        @PathVariable(value = "productId") Long productId,
        @RequestHeader(value = "X-Loopers-LoginId", required = false) String loginId,
        @RequestHeader(value = "User-Agent", required = false) String userAgent
    ) {
        String userId = (loginId != null) ? loginId : "unknown";
        eventPublisher.publishEvent(new ProductViewedEvent(userId, productId, userAgent));
        try {
            ViewPayload viewPayload = new ViewPayload(productId, userId, userAgent);
            String payloadJson = objectMapper.writeValueAsString(viewPayload);
            KafkaOutboxMessage message = new KafkaOutboxMessage(
                UUID.randomUUID().toString(),
                "PRODUCT_VIEWED",
                payloadJson,
                ZonedDateTime.now().toString()
            );
            kafkaTemplate.send(OutboxEventTopics.PRODUCT_VIEW, productId.toString(), message);
        } catch (JsonProcessingException e) {
            log.warn("상품 조회 Kafka 이벤트 직렬화 실패. productId={}, 이유={}", productId, e.getMessage());
        } catch (Exception e) {
            log.warn("상품 조회 Kafka 이벤트 발행 실패. productId={}, 이유={}", productId, e.getMessage());
        }
        return ApiResponse.success(
            ProductV1Dto.ProductDetailResponse.from(productFacade.getProductDetail(productId))
        );
    }

    private record ViewPayload(Long productId, String userId, String userAgent) {}
}
