package com.loopers.domain.favorite.service;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.repository.FavoriteRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public void addFavorite(FavoriteCommand.Add command) {
        if (favoriteRepository.existsByMemberIdAndProductId(command.memberId(), command.productId())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 좋아요한 상품입니다.");
        }
        Favorite favorite = Favorite.create(command.memberId(), command.productId());
        favoriteRepository.save(favorite);
    }

    public void delete(FavoriteCommand.Delete command) {
        Favorite favorite = favoriteRepository.findByMemberIdAndProductId(command.memberId(), command.productId())
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "좋아요 등록되지 않은 상품입니다."));
        favoriteRepository.delete(favorite);
    }

    public boolean existsByMemberIdAndProductId(Long memberId, Long productId) {
        return favoriteRepository.existsByMemberIdAndProductId(memberId, productId);
    }

    public long countByProductId(Long productId) {
        return favoriteRepository.countByProductId(productId);
    }
}
