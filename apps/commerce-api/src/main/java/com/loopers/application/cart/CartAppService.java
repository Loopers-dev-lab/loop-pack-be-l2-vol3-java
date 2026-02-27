package com.loopers.application.cart;

import com.loopers.domain.cart.CartService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 도메인 Application Service.
 *
 * <p>단일 도메인 서비스(CartService)만 호출하는 얇은 메서드를 담당한다.
 * 인증 후 CartService에 위임한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartAppService {

    private final CartService cartService;
    private final UserService userService;

    /**
     * 장바구니에서 상품을 삭제한다.
     *
     * @param loginId   로그인 ID
     * @param loginPw   로그인 비밀번호
     * @param productId 삭제할 상품 ID
     */
    @Transactional
    public void removeItem(String loginId, String loginPw, String productId) {
        UserModel user = userService.authenticate(loginId, loginPw);
        cartService.removeItem(user.getUserId(), productId);
    }
}
