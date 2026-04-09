package com.loopers.application.product;

import com.loopers.application.brand.BrandService;
import com.loopers.domain.event.ProductViewedEvent;
import com.loopers.application.queue.ModeManager;
import com.loopers.application.stock.StockService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.stock.Stock;
import com.loopers.infrastructure.product.ProductCacheManager;
import com.loopers.domain.viewer.BotDetector;
import com.loopers.domain.viewer.ViewerIdResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductFacadeTest {

    private ProductService productService;
    private BrandService brandService;
    private StockService stockService;
    private ProductCacheManager productCacheManager;
    private ApplicationEventPublisher eventPublisher;
    private ModeManager modeManager;
    private BotDetector botDetector;
    private ViewerIdResolver viewerIdResolver;

    private ProductFacade facade;

    private static final Long PRODUCT_ID = 100L;
    private static final String NORMAL_UA = "Mozilla/5.0";
    private static final String BOT_UA = "Googlebot/2.1";

    @BeforeEach
    void setUp() {
        productService = mock(ProductService.class);
        brandService = mock(BrandService.class);
        stockService = mock(StockService.class);
        productCacheManager = mock(ProductCacheManager.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        modeManager = mock(ModeManager.class);
        botDetector = mock(BotDetector.class);
        viewerIdResolver = mock(ViewerIdResolver.class);

        facade = new ProductFacade(
                productService, brandService, stockService,
                productCacheManager, eventPublisher, modeManager,
                botDetector, viewerIdResolver
        );

        Product product = mock(Product.class);
        when(product.getId()).thenReturn(PRODUCT_ID);
        when(product.getBrandId()).thenReturn(10L);
        when(product.getName()).thenReturn("p");
        when(product.getDescription()).thenReturn("d");
        when(product.getPrice()).thenReturn(BigDecimal.ONE);
        when(product.getLikeCount()).thenReturn(0);
        when(product.isDeleted()).thenReturn(false);
        ZonedDateTime now = ZonedDateTime.now();
        when(product.getCreatedAt()).thenReturn(now);
        when(product.getUpdatedAt()).thenReturn(now);
        when(product.getDeletedAt()).thenReturn(null);
        Brand brand = mock(Brand.class);
        when(brand.getName()).thenReturn("b");
        Stock stock = mock(Stock.class);
        when(stock.getQuantity()).thenReturn(5);

        when(productService.getActiveProduct(PRODUCT_ID)).thenReturn(product);
        when(brandService.getBrand(10L)).thenReturn(brand);
        when(stockService.getStock(PRODUCT_ID)).thenReturn(stock);
    }

    @Test
    @DisplayName("bot User-Agent면 ProductViewedEvent를 발행하지 않는다")
    void bot_doesNotPublishEvent() {
        when(botDetector.isBot(BOT_UA)).thenReturn(true);
        when(productCacheManager.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());

        facade.getActiveDetail(PRODUCT_ID, null, "anon-1", BOT_UA);

        verify(eventPublisher, never()).publishEvent(any(ProductViewedEvent.class));
    }

    @Test
    @DisplayName("정상 UA + 캐시 hit이면 ProductViewedEvent를 발행하지 않는다")
    void cacheHit_doesNotPublishEvent() {
        when(botDetector.isBot(NORMAL_UA)).thenReturn(false);
        ProductInfo cached = mock(ProductInfo.class);
        when(productCacheManager.getDetail(PRODUCT_ID)).thenReturn(Optional.of(cached));

        facade.getActiveDetail(PRODUCT_ID, null, "anon-1", NORMAL_UA);

        verify(eventPublisher, never()).publishEvent(any(ProductViewedEvent.class));
    }

    @Test
    @DisplayName("정상 UA + 캐시 miss + 로그인 사용자면 viewerId가 'u:{userId}'로 발행된다")
    void cacheMiss_loginUser_publishesUserViewerId() {
        when(botDetector.isBot(NORMAL_UA)).thenReturn(false);
        when(productCacheManager.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());
        when(viewerIdResolver.resolve(123L, null)).thenReturn("u:123");

        facade.getActiveDetail(PRODUCT_ID, 123L, null, NORMAL_UA);

        ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProductViewedEvent event = captor.getValue();
        assertThat(event.viewerId()).isEqualTo("u:123");
        assertThat(event.productId()).isEqualTo(PRODUCT_ID);
        assertThat(event.occurredAt()).isNotNull();
    }

    @Test
    @DisplayName("정상 UA + 캐시 miss + 익명 사용자면 viewerId가 'a:{anonymousId}'로 발행된다")
    void cacheMiss_anonymous_publishesAnonViewerId() {
        when(botDetector.isBot(NORMAL_UA)).thenReturn(false);
        when(productCacheManager.getDetail(PRODUCT_ID)).thenReturn(Optional.empty());
        when(viewerIdResolver.resolve(null, "anon-uuid")).thenReturn("a:anon-uuid");

        facade.getActiveDetail(PRODUCT_ID, null, "anon-uuid", NORMAL_UA);

        ArgumentCaptor<ProductViewedEvent> captor = ArgumentCaptor.forClass(ProductViewedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        ProductViewedEvent event = captor.getValue();
        assertThat(event.viewerId()).isEqualTo("a:anon-uuid");
        assertThat(event.productId()).isEqualTo(PRODUCT_ID);
    }
}
