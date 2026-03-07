package com.loopers.concurrency;

import com.loopers.application.brand.BrandCommand;
import com.loopers.application.brand.BrandService;
import com.loopers.application.order.OrderCommand;
import com.loopers.application.order.OrderFacade;
import com.loopers.application.product.ProductCommand;
import com.loopers.application.product.ProductService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class OrderConcurrencyTest {

    @Autowired
    private BrandService brandService;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    void 동시에_여러_주문이_들어와도_재고가_정상_차감된다() throws InterruptedException {
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
        Product product = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"));
        Long productId = product.getId();

        int threadCount = 10;
        Queue<Exception> exceptions = ConcurrencyTestHelper.executeConcurrently(threadCount, i ->
                orderFacade.placeOrder((long) (i + 1), OrderCommand.Place.of(
                        List.of(OrderCommand.PlaceItem.of(productId, 1))
                ))
        );

        Product found = productRepository.findById(productId).orElseThrow();
        assertThat(found.getStockQuantity()).isEqualTo(90);
        assertThat(exceptions).isEmpty();
    }

    @Test
    void 재고보다_많은_동시_주문이_들어오면_일부만_성공한다() throws InterruptedException {
        Long brandId = brandService.register(BrandCommand.Register.of("나이키", "스포츠 브랜드")).getId();
        Product product = productService.register(
                ProductCommand.Register.of(brandId, "운동화", new BigDecimal("50000"), 5, "편한 운동화"));
        Long productId = product.getId();

        int threadCount = 10;
        Queue<Exception> exceptions = ConcurrencyTestHelper.executeConcurrently(threadCount, i ->
                orderFacade.placeOrder((long) (i + 1), OrderCommand.Place.of(
                        List.of(OrderCommand.PlaceItem.of(productId, 1))
                ))
        );

        Product found = productRepository.findById(productId).orElseThrow();
        assertThat(found.getStockQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(exceptions).isNotEmpty();
        int successCount = threadCount - exceptions.size();
        assertThat(found.getStockQuantity()).isEqualTo(5 - successCount);
    }
}
