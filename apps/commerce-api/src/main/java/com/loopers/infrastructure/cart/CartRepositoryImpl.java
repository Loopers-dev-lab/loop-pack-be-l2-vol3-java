package com.loopers.infrastructure.cart;

import com.loopers.domain.cart.Cart;
import com.loopers.domain.cart.CartRepository;
import com.loopers.infrastructure.support.ConstraintViolationHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CartRepositoryImpl implements CartRepository {

    private final CartJpaRepository cartJpaRepository;

    @Override
    public Cart save(Cart cart) {
        try {
            return cartJpaRepository.saveAndFlush(cart);
        } catch (DataIntegrityViolationException e) {
            if (cart.getId() == null && ConstraintViolationHelper.isUniqueViolation(e, "uk_carts_user_id")) {
                throw new OptimisticLockingFailureException("동시에 장바구니가 생성되었습니다.", e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Cart> findByUserId(Long userId) {
        return cartJpaRepository.findByUserId(userId);
    }
}
