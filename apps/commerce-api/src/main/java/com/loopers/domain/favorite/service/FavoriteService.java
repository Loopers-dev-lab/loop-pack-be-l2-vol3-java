package com.loopers.domain.favorite.service;

import com.loopers.domain.favorite.model.Favorite;
import com.loopers.domain.favorite.model.FavoriteCommand;
import com.loopers.domain.favorite.repository.FavoriteRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;

    public boolean addFavorite(FavoriteCommand.Add command) {
        if (favoriteRepository.existsByMemberIdAndProductId(command.memberId(), command.productId())) {
            return false;
        }
        try {
            Favorite favorite = Favorite.create(command.memberId(), command.productId());
            favoriteRepository.save(favorite);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 동시 요청으로 중복 등록 시도 — 이미 등록된 것이므로 무시
            return false;
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

    public Set<Long> getFavoriteProductIds(Long memberId, List<Long> productIds) {
        if (memberId == null || productIds.isEmpty()) {
            return Set.of();
        }
        return favoriteRepository.findByMemberIdAndProductIds(memberId, productIds)
            .stream()
            .map(Favorite::getProductId)
            .collect(Collectors.toSet());
    }
}
