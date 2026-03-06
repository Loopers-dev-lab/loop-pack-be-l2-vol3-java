package com.loopers.domain.favorite.service;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.repository.FavoriteRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public void addFavorite(FavoriteCommand.Add command) {
        if (favoriteRepository.existsByMemberIdAndProductId(command.memberId(), command.productId())) {
            return;
        }
        try {
            Favorite favorite = Favorite.create(command.memberId(), command.productId());
            favoriteRepository.save(favorite);
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 중복 등록 시도 — 이미 등록된 것이므로 무시
        }
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
