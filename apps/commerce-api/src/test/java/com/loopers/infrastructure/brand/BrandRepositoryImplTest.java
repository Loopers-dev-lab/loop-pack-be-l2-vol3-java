package com.loopers.infrastructure.brand;

import com.loopers.domain.brand.BrandModel;
import com.loopers.support.enums.DisplayStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(BrandRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("BrandRepository 통합 테스트")
class BrandRepositoryImplTest {

    @Autowired
    BrandRepositoryImpl brandRepository;

    @Test
    @DisplayName("저장 시 UUID ID가 자동 생성된다")
    void save_ShouldPersistWithUuidId() {
        BrandModel brand = BrandModel.create("테스트브랜드", "설명", "서울");

        BrandModel saved = brandRepository.save(brand);

        assertThat(saved.getBrandId()).isNotNull();
        assertThat(saved.getBrandId()).hasSize(36);
    }

    @Test
    @DisplayName("ID로 조회 - 존재하는 브랜드")
    void findById_Existing_ShouldReturn() {
        BrandModel brand = BrandModel.create("테스트브랜드", "설명", "서울");
        BrandModel saved = brandRepository.save(brand);

        Optional<BrandModel> found = brandRepository.findById(saved.getBrandId());

        assertThat(found).isPresent();
        assertThat(found.get().getBrandName()).isEqualTo("테스트브랜드");
    }

    @Test
    @DisplayName("ID로 조회 - 존재하지 않는 브랜드")
    void findById_NotExisting_ShouldReturnEmpty() {
        Optional<BrandModel> found = brandRepository.findById("nonexistent-uuid");

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("delYn과 displayStatus로 필터링 조회")
    void findAllByDelYnAndDisplayStatus_ShouldFilter() {
        BrandModel active1 = BrandModel.create("활성브랜드1", "설명", "서울");
        BrandModel active2 = BrandModel.create("활성브랜드2", "설명", "부산");
        BrandModel hidden = BrandModel.create("숨김브랜드", "설명", "대전");
        hidden.hide();
        BrandModel deleted = BrandModel.create("삭제브랜드", "설명", "광주");
        deleted.softDelete();

        brandRepository.save(active1);
        brandRepository.save(active2);
        brandRepository.save(hidden);
        brandRepository.save(deleted);

        List<BrandModel> result = brandRepository.findAllByDelYnAndDisplayStatus("N", DisplayStatus.ACTIVE);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("키워드로 브랜드명 부분 검색")
    void findAllByKeyword_ShouldMatchPartialBrandName() {
        brandRepository.save(BrandModel.create("테스트브랜드A", "설명", "서울"));
        brandRepository.save(BrandModel.create("테스트브랜드B", "설명", "부산"));
        brandRepository.save(BrandModel.create("다른브랜드", "설명", "대전"));

        List<BrandModel> result = brandRepository.findAllByKeyword("테스트");

        assertThat(result).hasSize(2);
    }
}
