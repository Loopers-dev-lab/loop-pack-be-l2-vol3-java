package com.loopers.infrastructure.like;

import com.loopers.domain.like.LikeId;
import com.loopers.domain.like.LikeModel;
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
@Import(LikeRepositoryImpl.class)
@ActiveProfiles("test")
@DisplayName("LikeRepository 통합 테스트")
class LikeRepositoryImplTest {

    @Autowired
    LikeRepositoryImpl likeRepository;

    @Test
    @DisplayName("좋아요 저장")
    void save_ShouldPersist() {
        LikeModel like = LikeModel.create(1L, 1L);

        LikeModel saved = likeRepository.save(like);

        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getProductId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("복합 PK로 조회 - 존재하는 좋아요")
    void findById_Existing_ShouldReturn() {
        likeRepository.save(LikeModel.create(1L, 1L));

        Optional<LikeModel> found = likeRepository.findById(new LikeId(1L, 1L));

        assertThat(found).isPresent();
    }

    @Test
    @DisplayName("복합 PK로 조회 - 존재하지 않는 좋아요")
    void findById_NotExisting_ShouldReturnEmpty() {
        Optional<LikeModel> found = likeRepository.findById(new LikeId(1L, 1L));

        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("좋아요 삭제 (물리 삭제)")
    void delete_ShouldRemove() {
        LikeModel like = likeRepository.save(LikeModel.create(1L, 1L));

        likeRepository.delete(like);

        Optional<LikeModel> found = likeRepository.findById(new LikeId(1L, 1L));
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("사용자별 좋아요 목록 조회")
    void findAllByUserId_ShouldReturnUserLikes() {
        likeRepository.save(LikeModel.create(1L, 1L));
        likeRepository.save(LikeModel.create(1L, 2L));
        likeRepository.save(LikeModel.create(2L, 1L));

        List<LikeModel> result = likeRepository.findAllByUserId(1L);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("상품별 좋아요 카운트 조회")
    void countByProductId_ShouldReturnCorrectCount() {
        likeRepository.save(LikeModel.create(1L, 1L));
        likeRepository.save(LikeModel.create(2L, 1L));
        likeRepository.save(LikeModel.create(3L, 2L));

        long count = likeRepository.countByProductId(1L);

        assertThat(count).isEqualTo(2);
    }
}
