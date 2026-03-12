package com.loopers.application.product;

import com.loopers.application.order.OrderQueryApplicationService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProductAdminFacade {

    private final ProductApplicationService productApplicationService;
    private final OrderQueryApplicationService orderQueryApplicationService;

    public void delete(UUID productId) {
        if (orderQueryApplicationService.existsOrderItemByProductId(productId)) {
            throw new CoreException(ErrorType.CONFLICT, "주문 이력이 있는 상품은 삭제할 수 없습니다.");
        }
        productApplicationService.deleteSoft(productId);
    }
}
