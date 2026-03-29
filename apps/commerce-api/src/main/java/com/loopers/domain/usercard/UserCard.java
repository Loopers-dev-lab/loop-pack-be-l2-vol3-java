package com.loopers.domain.usercard;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CardType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_card")
public class UserCard extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 25)
    private String cardNo;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected UserCard() {}

    public UserCard(Long userId, CardType cardType, String cardNo, boolean isDefault) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 비어있을 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.");
        }
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.isDefault = isDefault;
    }

    public Long getUserId() {
        return userId;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getCardNo() {
        return cardNo;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public void markAsDefault() {
        this.isDefault = true;
    }

    public void unmarkAsDefault() {
        this.isDefault = false;
    }
}
