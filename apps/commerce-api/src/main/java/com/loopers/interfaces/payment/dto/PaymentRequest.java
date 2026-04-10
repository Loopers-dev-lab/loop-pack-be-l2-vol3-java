package com.loopers.interfaces.payment.dto;

import com.loopers.application.order.dto.OrderCriteria;
import com.loopers.domain.payment.CardType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class PaymentRequest {

    public record Create(
            @Valid
            List<@Valid OrderItemRequest> items,
            Long couponId,
            @NotNull(message = "카드 종류는 필수 입력값입니다.")
            CardType cardType,
            @NotBlank(message = "카드 번호는 필수 입력값입니다.")
            String cardNo
    ) {
        public OrderCriteria.Create toOrderCriteria() {
            return new OrderCriteria.Create(
                    items != null ? items.stream()
                            .map(i -> new OrderCriteria.Create.CreateItem(
                                    i.productId(), i.quantity(), i.expectedPrice()))
                            .toList() : null,
                    couponId,
                    cardType,
                    cardNo);
        }

        public record OrderItemRequest(
                @NotNull(message = "상품 ID는 필수 입력값입니다.")
                Long productId,
                @NotNull(message = "수량은 필수 입력값입니다.")
                @Min(value = 1, message = "수량은 1 이상이어야 합니다.")
                Integer quantity,
                @NotNull(message = "예상 가격은 필수 입력값입니다.")
                @Min(value = 0, message = "예상 가격은 0 이상이어야 합니다.")
                Integer expectedPrice
        ) {}
    }
}
