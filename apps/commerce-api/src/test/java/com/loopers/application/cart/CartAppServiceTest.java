package com.loopers.application.cart;

import com.loopers.domain.cart.CartService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartAppService 단위 테스트")
class CartAppServiceTest {

    @Mock
    CartService cartService;

    @Mock
    UserService userService;

    @InjectMocks
    CartAppService cartAppService;

    @Test
    @DisplayName("항목 삭제 시 인증 후 CartService.removeItem이 호출된다")
    void removeItem_ShouldCallServiceMethod() {
        UserModel user = mock(UserModel.class);
        when(user.getUserId()).thenReturn("user-1");
        when(userService.authenticate("login1", "pw1")).thenReturn(user);

        cartAppService.removeItem("login1", "pw1", "p1");

        verify(userService).authenticate("login1", "pw1");
        verify(cartService).removeItem("user-1", "p1");
    }
}
