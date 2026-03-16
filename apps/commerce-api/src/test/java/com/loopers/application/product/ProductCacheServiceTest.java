package com.loopers.application.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCacheServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductCacheService productCacheService;

    @Test
    @DisplayName("getDetail에서 Redis 예외 발생 시 Optional.empty()를 반환해 DB 폴백을 허용한다.")
    void getDetail_whenRedisFails_shouldReturnEmpty() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        Optional<ProductDetailInfo> result = productCacheService.getDetail(1L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getList에서 Redis 예외 발생 시 Optional.empty()를 반환해 DB 폴백을 허용한다.")
    void getList_whenRedisFails_shouldReturnEmpty() {
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("redis down"));

        Optional<Page<ProductListItemInfo>> result = productCacheService.getList(null, "latest", 20);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("putList에서 Redis 예외 발생 시 예외를 전파하지 않는다.")
    void putList_whenRedisFails_shouldNotThrow() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), org.mockito.Mockito.any());

        Page<ProductListItemInfo> page = new PageImpl<>(List.of(), org.springframework.data.domain.PageRequest.of(0, 20), 0);
        when(objectMapper.writeValueAsString(org.mockito.Mockito.any())).thenReturn("{}");

        productCacheService.putList(null, "latest", 20, page);
    }

    @Test
    @DisplayName("putDetail에서 Redis 예외 발생 시 예외를 전파하지 않는다.")
    void putDetail_whenRedisFails_shouldNotThrow() throws Exception {
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        doThrow(new RuntimeException("redis down")).when(ops).set(anyString(), anyString(), org.mockito.Mockito.any());

        when(objectMapper.writeValueAsString(org.mockito.Mockito.any())).thenReturn("{}");

        productCacheService.putDetail(1L, mock(ProductDetailInfo.class));
    }
}

