package com.loopers.domain.favorite;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.repository.FavoriteRepository;
import com.loopers.domain.favorite.service.FavoriteService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavoriteServiceTest {

    @InjectMocks
    private FavoriteService favoriteService;

    @Mock
    private FavoriteRepository favoriteRepository;

    @DisplayName("좋아요 등록")
    @Nested
    class AddFavorite {

        @DisplayName("정상적으로 좋아요를 등록한다")
        @Test
        void addsFavorite_whenNotExists() {
            // arrange
            FavoriteCommand.Add command = new FavoriteCommand.Add(1L, 1L);
            when(favoriteRepository.existsByMemberIdAndProductId(1L, 1L)).thenReturn(false);

            // act
            favoriteService.addFavorite(command);

            // assert
            verify(favoriteRepository).save(any(Favorite.class));
        }

        @DisplayName("이미 좋아요한 상품이면 예외 없이 무시한다")
        @Test
        void ignoresSilently_whenAlreadyExists() {
            // arrange
            FavoriteCommand.Add command = new FavoriteCommand.Add(1L, 1L);
            when(favoriteRepository.existsByMemberIdAndProductId(1L, 1L)).thenReturn(true);

            // act & assert
            assertThatCode(() -> favoriteService.addFavorite(command))
                .doesNotThrowAnyException();
            verify(favoriteRepository, never()).save(any(Favorite.class));
        }

        @DisplayName("동시 요청으로 DataIntegrityViolationException 발생 시 예외 없이 무시한다")
        @Test
        void ignoresSilently_whenConcurrentDuplicate() {
            // arrange
            FavoriteCommand.Add command = new FavoriteCommand.Add(1L, 1L);
            when(favoriteRepository.existsByMemberIdAndProductId(1L, 1L)).thenReturn(false);
            doThrow(new DataIntegrityViolationException("Duplicate entry"))
                .when(favoriteRepository).save(any(Favorite.class));

            // act & assert
            assertThatCode(() -> favoriteService.addFavorite(command))
                .doesNotThrowAnyException();
        }
    }

    @DisplayName("좋아요 취소")
    @Nested
    class Delete {

        @DisplayName("정상적으로 좋아요를 취소한다")
        @Test
        void deletesFavorite_whenExists() {
            // arrange
            FavoriteCommand.Delete command = new FavoriteCommand.Delete(1L, 1L);
            Favorite favorite = Favorite.reconstruct(1L, 1L, 1L);
            when(favoriteRepository.findByMemberIdAndProductId(1L, 1L)).thenReturn(Optional.of(favorite));

            // act
            favoriteService.delete(command);

            // assert
            verify(favoriteRepository).delete(favorite);
        }

        @DisplayName("등록되지 않은 좋아요를 취소하면 예외가 발생한다")
        @Test
        void throwsException_whenNotExists() {
            // arrange
            FavoriteCommand.Delete command = new FavoriteCommand.Delete(1L, 1L);
            when(favoriteRepository.findByMemberIdAndProductId(1L, 1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> favoriteService.delete(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> {
                    CoreException ce = (CoreException) e;
                    assertThat(ce.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });
        }
    }
}
