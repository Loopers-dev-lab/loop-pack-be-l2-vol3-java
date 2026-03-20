package com.loopers.domain.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProductImageServiceTest {

    private ProductImageService productImageService;
    private FakeProductImageRepository fakeProductImageRepository;

    @BeforeEach
    void setUp() {
        fakeProductImageRepository = new FakeProductImageRepository();
        productImageService = new ProductImageService(fakeProductImageRepository);
    }

    @DisplayName("이미지를 추가할 때, ")
    @Nested
    class AddImage {

        @DisplayName("정상적으로 저장된다.")
        @Test
        void addImage_success() {
            // act
            ProductImageModel result = productImageService.addImage(
                    1L, "https://cdn.example.com/img.jpg", ImageType.MAIN, 0);

            // assert
            assertThat(result.getId()).isNotNull();
            assertThat(result.getProductId()).isEqualTo(1L);
            assertThat(result.getImageUrl()).isEqualTo("https://cdn.example.com/img.jpg");
        }
    }

    @DisplayName("상품별 이미지를 조회할 때, ")
    @Nested
    class GetImagesByProductId {

        @DisplayName("해당 상품의 이미지만 반환된다.")
        @Test
        void getImagesByProductId_success() {
            // arrange
            productImageService.addImage(1L, "https://cdn.example.com/1.jpg", ImageType.MAIN, 0);
            productImageService.addImage(1L, "https://cdn.example.com/2.jpg", ImageType.DETAIL, 0);
            productImageService.addImage(2L, "https://cdn.example.com/3.jpg", ImageType.MAIN, 0);

            // act
            List<ProductImageModel> result = productImageService.getImagesByProductId(1L);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("타입별로 필터링할 수 있다.")
        @Test
        void getImagesByProductIdAndType_success() {
            // arrange
            productImageService.addImage(1L, "https://cdn.example.com/main.jpg", ImageType.MAIN, 0);
            productImageService.addImage(1L, "https://cdn.example.com/detail1.jpg", ImageType.DETAIL, 0);
            productImageService.addImage(1L, "https://cdn.example.com/detail2.jpg", ImageType.DETAIL, 1);

            // act
            List<ProductImageModel> mainImages =
                    productImageService.getImagesByProductIdAndType(1L, ImageType.MAIN);
            List<ProductImageModel> detailImages =
                    productImageService.getImagesByProductIdAndType(1L, ImageType.DETAIL);

            // assert
            assertThat(mainImages).hasSize(1);
            assertThat(detailImages).hasSize(2);
        }

        @DisplayName("sortOrder 순으로 정렬된다.")
        @Test
        void getImagesByProductId_sortedBySortOrder() {
            // arrange
            productImageService.addImage(1L, "https://cdn.example.com/2.jpg", ImageType.MAIN, 2);
            productImageService.addImage(1L, "https://cdn.example.com/0.jpg", ImageType.MAIN, 0);
            productImageService.addImage(1L, "https://cdn.example.com/1.jpg", ImageType.MAIN, 1);

            // act
            List<ProductImageModel> result = productImageService.getImagesByProductId(1L);

            // assert
            assertThat(result.get(0).getImageUrl()).contains("0.jpg");
            assertThat(result.get(1).getImageUrl()).contains("1.jpg");
            assertThat(result.get(2).getImageUrl()).contains("2.jpg");
        }
    }

    @DisplayName("상품 이미지를 전체 삭제할 때, ")
    @Nested
    class DeleteAllByProductId {

        @DisplayName("해당 상품의 이미지만 삭제된다.")
        @Test
        void deleteAllByProductId_success() {
            // arrange
            productImageService.addImage(1L, "https://cdn.example.com/1.jpg", ImageType.MAIN, 0);
            productImageService.addImage(1L, "https://cdn.example.com/2.jpg", ImageType.DETAIL, 0);
            productImageService.addImage(2L, "https://cdn.example.com/3.jpg", ImageType.MAIN, 0);

            // act
            productImageService.deleteAllByProductId(1L);

            // assert
            assertThat(productImageService.getImagesByProductId(1L)).isEmpty();
            assertThat(productImageService.getImagesByProductId(2L)).hasSize(1);
        }
    }
}
