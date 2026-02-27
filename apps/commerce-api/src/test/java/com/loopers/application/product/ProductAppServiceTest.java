package com.loopers.application.product;

import com.loopers.domain.product.ProductRevisionModel;
import com.loopers.domain.product.ProductService;
import com.loopers.support.enums.ProductRevisionAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAppService 단위 테스트")
class ProductAppServiceTest {

    @Mock
    ProductService productService;

    @InjectMocks
    ProductAppService productAppService;

    @Test
    @DisplayName("상품 삭제 시 ProductService.deleteProduct가 호출된다")
    void deleteProduct_ShouldCallServiceMethod() {
        productAppService.deleteProduct("p1");

        verify(productService).deleteProduct("p1");
    }

    @Test
    @DisplayName("변경 이력 조회 시 ProductRevisionInfo 목록을 반환한다")
    void getRevisions_ShouldReturnRevisionInfoList() {
        ProductRevisionModel revision = mock(ProductRevisionModel.class);
        when(revision.getAction()).thenReturn(ProductRevisionAction.CREATE);
        when(productService.findRevisionsByProductId("p1")).thenReturn(List.of(revision));

        List<ProductRevisionInfo> result = productAppService.getRevisions("p1");

        assertThat(result).hasSize(1);
        verify(productService).findRevisionsByProductId("p1");
    }

    @Test
    @DisplayName("변경 이력 상세 조회 시 ProductRevisionInfo를 반환한다")
    void getRevisionDetail_ShouldReturnRevisionInfo() {
        ProductRevisionModel revision = mock(ProductRevisionModel.class);
        when(revision.getAction()).thenReturn(ProductRevisionAction.UPDATE);
        when(productService.findRevisionById("p1", 1L)).thenReturn(revision);

        ProductRevisionInfo result = productAppService.getRevisionDetail("p1", 1L);

        assertThat(result).isNotNull();
        assertThat(result.getAction()).isEqualTo(ProductRevisionAction.UPDATE);
        verify(productService).findRevisionById("p1", 1L);
    }
}
