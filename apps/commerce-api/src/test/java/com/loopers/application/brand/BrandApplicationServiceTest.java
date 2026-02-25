package com.loopers.application.brand;

import com.loopers.application.brand.command.CreateBrandCommand;
import com.loopers.application.brand.command.UpdateBrandCommand;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.InOrder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class BrandApplicationServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @InjectMocks
    private BrandApplicationService brandApplicationService;

    @Nested
    @DisplayName("브랜드 등록")
    class CreateBrandTest {

        @Test
        @DisplayName("CreateBrandCommand 생성 성공")
        void createCommandCreation() {
            String name = "퍼피박스";
            String description = "강아지용품 브랜드";
            String imageUrl = "https://example.com/brand.png";

            CreateBrandCommand command = new CreateBrandCommand(name, description, imageUrl);

            assertThat(command.name()).isEqualTo(name);
            assertThat(command.description()).isEqualTo(description);
            assertThat(command.imageUrl()).isEqualTo(imageUrl);
        }

        @Test
        @DisplayName("CreateBrandCommand - description null 허용")
        void createCommandWithNullDescription() {
            String name = "퍼피박스";
            String description = null;
            String imageUrl = "https://example.com/brand.png";

            CreateBrandCommand command = new CreateBrandCommand(name, description, imageUrl);

            assertThat(command.description()).isNull();
        }

        @Test
        @DisplayName("CreateBrandCommand - imageUrl null 허용")
        void createCommandWithNullImageUrl() {
            String name = "퍼피박스";
            String description = "설명";
            String imageUrl = null;

            CreateBrandCommand command = new CreateBrandCommand(name, description, imageUrl);

            assertThat(command.imageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("브랜드 수정")
    class UpdateBrandTest {

        @Test
        @DisplayName("UpdateBrandCommand 생성 - description만 변경")
        void updateCommandDescriptionOnly() {
            String description = "새로운 설명";
            String imageUrl = null;

            UpdateBrandCommand command = new UpdateBrandCommand(description, imageUrl);

            assertThat(command.description()).isEqualTo(description);
            assertThat(command.imageUrl()).isNull();
        }

        @Test
        @DisplayName("UpdateBrandCommand 생성 - imageUrl만 변경")
        void updateCommandImageUrlOnly() {
            String description = null;
            String imageUrl = "https://new.com/brand.png";

            UpdateBrandCommand command = new UpdateBrandCommand(description, imageUrl);

            assertThat(command.description()).isNull();
            assertThat(command.imageUrl()).isEqualTo(imageUrl);
        }

        @Test
        @DisplayName("UpdateBrandCommand 생성 - 둘 다 변경")
        void updateCommandBoth() {
            String description = "새로운 설명";
            String imageUrl = "https://new.com/brand.png";

            UpdateBrandCommand command = new UpdateBrandCommand(description, imageUrl);

            assertThat(command.description()).isEqualTo(description);
            assertThat(command.imageUrl()).isEqualTo(imageUrl);
        }
    }

    @Nested
    @DisplayName("브랜드 삭제")
    class DeleteTest {

        @Test
        @DisplayName("브랜드 삭제 시 관련 상품 soft-delete와 함께 삭제한다")
        void deleteAlsoDeletesRelatedProducts() {
            Brand target = new Brand(1L, new BrandName("퍼피박스"), "설명", "https://example.com/brand.png");

            when(brandRepository.findById(1L)).thenReturn(Optional.of(target));
            doNothing().when(brandRepository).deleteRelatedProducts(1L);
            doNothing().when(brandRepository).delete(target);

            brandApplicationService.delete(1L);

            InOrder inOrder = inOrder(brandRepository);
            inOrder.verify(brandRepository).deleteRelatedProducts(1L);
            inOrder.verify(brandRepository).delete(target);
            verifyNoMoreInteractions(brandRepository);
        }

        @Test
        @DisplayName("삭제 대상이 없으면 404 반환")
        void deleteWhenNotFound() {
            when(brandRepository.findById(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> brandApplicationService.delete(99L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));

            verify(brandRepository).findById(99L);
            verify(brandRepository, never()).deleteRelatedProducts(anyLong());
            verify(brandRepository, never()).delete(any(Brand.class));
        }
    }
}
