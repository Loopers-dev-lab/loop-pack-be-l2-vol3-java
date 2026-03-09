package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.cart.CartItemModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.cart.CartItemJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.product.ProductStockJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Cart API V1 E2E 테스트")
class AdminCartV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CartItemJpaRepository cartItemJpaRepository;
    @Autowired ProductJpaRepository productJpaRepository;
    @Autowired ProductStockJpaRepository productStockJpaRepository;
    @Autowired BrandJpaRepository brandJpaRepository;

    @Test
    @DisplayName("관리자가 특정 사용자의 장바구니를 조회할 수 있다")
    void GET_userCart_ShouldReturn200WithCartItems() throws Exception {
        BrandModel brand = brandJpaRepository.save(BrandModel.create("테스트브랜드", "설명", "서울"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create("테스트상품", brand.getBrandId(), BigDecimal.valueOf(10000),
                        null, null, null, null, null, null, null));
        productStockJpaRepository.save(ProductStockModel.create(product.getProductId(), 100));
        cartItemJpaRepository.save(CartItemModel.create("user-1", product.getProductId(), 3));

        mockMvc.perform(get("/api-admin/v1/users/user-1/cart")
                        .header(ADMIN_HEADER, ADMIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].quantity").value(3));
    }
}
