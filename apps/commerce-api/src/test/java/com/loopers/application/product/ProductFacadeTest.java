package com.loopers.application.product;

import com.loopers.application.product.dto.FindProductListReqDto;
import com.loopers.application.product.dto.FindProductListResDto;
import com.loopers.application.product.dto.FindProductResDto;
import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.service.BrandService;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.ProductItem;
import com.loopers.domain.product.service.ProductService;
import com.loopers.support.enums.SortFilter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductFacadeTest {

    @InjectMocks
    private ProductFacade productFacade;

    @Mock
    private ProductService productService;

    @Mock
    private BrandService brandService;

    @Mock
    private FavoriteService favoriteService;

    @Mock
    private MemberService memberService;

    private static Member createTestMember() {
        return Member.reconstruct(1L, "testuser", "encodedPw", "홍길동", LocalDate.of(1990, 1, 1), "test@test.com");
    }

    private static ProductItem createTestProductItem(boolean isFavorite) {
        return new ProductItem(1L, "상품A", 1L, "나이키", 10000, 100, "DISPLAYING", 5L, isFavorite);
    }

    @DisplayName("상품 목록 조회")
    @Nested
    class FindProductList {

        @DisplayName("존재하지 않는 brandId로 조회하면 CoreException이 발생한다")
        @Test
        void throwsException_whenBrandNotFound() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            when(brandService.findBrand(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 브랜드입니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.findProductList(new FindProductListReqDto("testuser", "password", 999L, SortFilter.LATEST), pageable))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("비로그인 사용자의 상품 목록 조회 시 좋아요 여부는 모두 false이다")
        @Test
        void findProductList_withoutLogin() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            ProductItem item = createTestProductItem(false);
            Page<ProductItem> itemPage = new PageImpl<>(List.of(item), pageable, 1);

            when(productService.findProductList(isNull(), any(SortFilter.class), eq(pageable)))
                    .thenReturn(itemPage);
            when(favoriteService.getFavoriteProductIds(isNull(), anyList()))
                    .thenReturn(Set.of());

            // act
            Page<FindProductListResDto> result = productFacade.findProductList(new FindProductListReqDto(null, null, null, SortFilter.LATEST), pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(1),
                () -> assertThat(result.getContent().get(0).isFavorite()).isFalse()
            );
            verify(memberService, never()).findMember(any(), any());
        }

        @DisplayName("로그인 사용자의 상품 목록 조회 시 좋아요 여부를 포함한다")
        @Test
        void findProductList_withLogin_includesFavorite() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            Member member = createTestMember();
            ProductItem item = createTestProductItem(false);
            Page<ProductItem> itemPage = new PageImpl<>(List.of(item), pageable, 1);

            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(productService.findProductList(isNull(), any(SortFilter.class), eq(pageable)))
                    .thenReturn(itemPage);
            when(favoriteService.getFavoriteProductIds(eq(1L), anyList()))
                    .thenReturn(Set.of(1L));

            // act
            Page<FindProductListResDto> result = productFacade.findProductList(new FindProductListReqDto("testuser", "password", null, SortFilter.LATEST), pageable);

            // assert
            assertAll(
                () -> assertThat(result.getTotalElements()).isEqualTo(1),
                () -> assertThat(result.getContent().get(0).isFavorite()).isTrue()
            );
        }

        @DisplayName("brandId가 있으면 brandService로 브랜드 존재를 검증한다")
        @Test
        void validatesBrand_whenBrandIdPresent() {
            // arrange
            Pageable pageable = PageRequest.of(0, 10);
            ProductItem item = createTestProductItem(false);
            Page<ProductItem> itemPage = new PageImpl<>(List.of(item), pageable, 1);

            when(brandService.findBrand(1L)).thenReturn(Brand.reconstruct(1L, "나이키", "스포츠"));
            when(productService.findProductList(eq(1L), any(SortFilter.class), eq(pageable)))
                    .thenReturn(itemPage);
            when(favoriteService.getFavoriteProductIds(isNull(), anyList()))
                    .thenReturn(Set.of());

            // act
            productFacade.findProductList(new FindProductListReqDto(null, null, 1L, SortFilter.LATEST), pageable);

            // assert
            verify(brandService).findBrand(1L);
        }
    }

    @DisplayName("상품 상세 조회")
    @Nested
    class FindProduct {

        @DisplayName("존재하지 않는 상품 조회 시 CoreException(NOT_FOUND)이 발생한다")
        @Test
        void throwsException_whenProductNotFound() {
            // arrange
            when(productService.findProductDetail(999L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 상품입니다."));

            // act & assert
            assertThatThrownBy(() -> productFacade.findProduct(null, null, 999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("비로그인 사용자의 상품 상세 조회 시 좋아요 여부는 false이다")
        @Test
        void returnsProduct_withoutLogin() {
            // arrange
            ProductItem item = createTestProductItem(false);
            when(productService.findProductDetail(1L)).thenReturn(item);

            // act
            FindProductResDto result = productFacade.findProduct(null, null, 1L);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(1L),
                () -> assertThat(result.favoriteCnt()).isEqualTo(5L),
                () -> assertThat(result.isFavorite()).isFalse()
            );
            verify(favoriteService, never()).existsByMemberIdAndProductId(any(), any());
        }

        @DisplayName("로그인 사용자는 좋아요 여부를 포함한 상품 상세를 반환한다")
        @Test
        void returnsProduct_withFavoriteInfo_whenLoggedIn() {
            // arrange
            Member member = createTestMember();
            ProductItem item = createTestProductItem(false);

            when(productService.findProductDetail(1L)).thenReturn(item);
            when(memberService.findMember("testuser", "password")).thenReturn(member);
            when(favoriteService.existsByMemberIdAndProductId(1L, 1L)).thenReturn(true);

            // act
            FindProductResDto result = productFacade.findProduct("testuser", "password", 1L);

            // assert
            assertAll(
                () -> assertThat(result.id()).isEqualTo(1L),
                () -> assertThat(result.brandName()).isEqualTo("나이키"),
                () -> assertThat(result.favoriteCnt()).isEqualTo(5L),
                () -> assertThat(result.isFavorite()).isTrue()
            );
        }
    }
}
