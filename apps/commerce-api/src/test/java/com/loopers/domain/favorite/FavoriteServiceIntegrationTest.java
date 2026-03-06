package com.loopers.domain.favorite;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.favorite.repository.FavoriteJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class FavoriteServiceIntegrationTest {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteJpaRepository favoriteJpaRepository;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @BeforeEach
    void setUp() {
        favoriteJpaRepository.deleteAll();
        productJpaRepository.deleteAll();
        brandJpaRepository.deleteAll();
        memberJpaRepository.deleteAll();
    }

    private MemberEntity saveMember(String loginId, String email) {
        memberService.addMember(new MemberCommand.SignUp(
            loginId, "Password123!", "홍길동",
            LocalDate.of(1990, 1, 15), email
        ));
        return memberJpaRepository.findByLoginId(loginId).orElseThrow();
    }

    private BrandEntity saveBrand() {
        Brand brand = Brand.create(new BrandCommand.Create("테스트브랜드", "테스트 설명"));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("좋아요 등록")
    @Nested
    class AddFavorite {

        @DisplayName("이미 좋아요한 상품에 다시 좋아요하면 예외 없이 무시한다")
        @Test
        void addFavorite_duplicateIgnored() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            favoriteService.addFavorite(new FavoriteCommand.Add(member.getId(), product.getId()));

            // act — 중복 등록 시도
            favoriteService.addFavorite(new FavoriteCommand.Add(member.getId(), product.getId()));

            // assert — 예외 없이 성공, 데이터는 1건만 존재
            assertThat(favoriteJpaRepository.countByProductId(product.getId())).isEqualTo(1);
        }

        @DisplayName("정상적으로 좋아요를 등록하면 DB에 저장된다")
        @Test
        void addFavorite_success() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            // act
            favoriteService.addFavorite(new FavoriteCommand.Add(member.getId(), product.getId()));

            // assert
            assertThat(favoriteJpaRepository.existsByMemberIdAndProductId(member.getId(), product.getId())).isTrue();
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class DeleteFavorite {

        @DisplayName("좋아요하지 않은 상품을 취소하면 NOT_FOUND 예외가 발생한다")
        @Test
        void delete_notFound() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            // act & assert
            assertThatThrownBy(() -> favoriteService.delete(
                new FavoriteCommand.Delete(member.getId(), product.getId())
            ))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @DisplayName("좋아요를 취소하면 DB에서 삭제된다")
        @Test
        void delete_success() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            favoriteService.addFavorite(new FavoriteCommand.Add(member.getId(), product.getId()));

            // act
            favoriteService.delete(new FavoriteCommand.Delete(member.getId(), product.getId()));

            // assert
            assertThat(favoriteJpaRepository.existsByMemberIdAndProductId(member.getId(), product.getId())).isFalse();
        }
    }

    @DisplayName("좋아요 존재 확인")
    @Nested
    class ExistsByMemberIdAndProductId {

        @DisplayName("좋아요하지 않은 상품이면 false를 반환한다")
        @Test
        void exists_false() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            // act
            boolean result = favoriteService.existsByMemberIdAndProductId(member.getId(), product.getId());

            // assert
            assertThat(result).isFalse();
        }

        @DisplayName("좋아요한 상품이면 true를 반환한다")
        @Test
        void exists_true() {
            // arrange
            MemberEntity member = saveMember("testuser", "test@example.com");
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);
            favoriteService.addFavorite(new FavoriteCommand.Add(member.getId(), product.getId()));

            // act
            boolean result = favoriteService.existsByMemberIdAndProductId(member.getId(), product.getId());

            // assert
            assertThat(result).isTrue();
        }
    }

    @DisplayName("좋아요 수 조회")
    @Nested
    class CountByProductId {

        @DisplayName("좋아요가 없으면 0을 반환한다")
        @Test
        void count_zero() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            // act
            long result = favoriteService.countByProductId(product.getId());

            // assert
            assertThat(result).isZero();
        }

        @DisplayName("여러 회원이 좋아요하면 좋아요 수를 반환한다")
        @Test
        void count_multiple() {
            // arrange
            BrandEntity brand = saveBrand();
            ProductEntity product = saveProduct(brand.getId(), "상품A", 10000, 100);

            MemberEntity member1 = saveMember("user1", "user1@example.com");
            MemberEntity member2 = saveMember("user2", "user2@example.com");
            MemberEntity member3 = saveMember("user3", "user3@example.com");

            favoriteService.addFavorite(new FavoriteCommand.Add(member1.getId(), product.getId()));
            favoriteService.addFavorite(new FavoriteCommand.Add(member2.getId(), product.getId()));
            favoriteService.addFavorite(new FavoriteCommand.Add(member3.getId(), product.getId()));

            // act
            long result = favoriteService.countByProductId(product.getId());

            // assert
            assertThat(result).isEqualTo(3);
        }
    }
}
