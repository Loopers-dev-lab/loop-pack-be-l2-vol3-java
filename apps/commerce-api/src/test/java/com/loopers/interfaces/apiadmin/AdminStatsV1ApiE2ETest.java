package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.like.LikeModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Stats API V1 E2E 테스트")
class AdminStatsV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderJpaRepository orderJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductStockJpaRepository productStockJpaRepository;
    @Autowired BrandJpaRepository brandJpaRepository;
    @Autowired LikeJpaRepository likeJpaRepository;

    private String today;
    private String monthAgo;

    @BeforeEach
    void setUp() {
        today = LocalDate.now().toString();
        monthAgo = LocalDate.now().minusMonths(1).toString();
    }

    @Test
    @DisplayName("주문 현황 overview 조회")
    void GET_overview_ShouldReturn200() throws Exception {
        orderJpaRepository.save(OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000)));

        mockMvc.perform(get("/api-admin/v1/stats/overview")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .param("startAt", monthAgo)
                        .param("endAt", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingCount").exists());
    }

    @Test
    @DisplayName("일별 주문 통계 조회")
    void GET_dailyOrderStats_ShouldReturn200() throws Exception {
        orderJpaRepository.save(OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000)));

        mockMvc.perform(get("/api-admin/v1/stats/orders/daily")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .param("startAt", monthAgo)
                        .param("endAt", today))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("좋아요 상위 상품 조회")
    void GET_topLikedProducts_ShouldReturn200() throws Exception {
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드", "설명", "서울"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create("인기상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        likeJpaRepository.save(LikeModel.create(1L, product.getProductId()));

        mockMvc.perform(get("/api-admin/v1/stats/products/top-liked")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("주문 상위 상품 조회")
    void GET_topOrderedProducts_ShouldReturn200() throws Exception {
        mockMvc.perform(get("/api-admin/v1/stats/products/top-ordered")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("재고 부족 상품 조회")
    void GET_lowStockProducts_ShouldReturn200() throws Exception {
        BrandModel brand = brandJpaRepository.save(BrandModel.create("브랜드", "설명", "서울"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create("부족상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        productStockJpaRepository.save(ProductStockModel.createWithReserved(product.getProductId(), 10, 8));

        mockMvc.perform(get("/api-admin/v1/stats/stocks/low")
                        .header(ADMIN_HEADER, ADMIN_VALUE)
                        .param("threshold", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
