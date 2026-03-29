package com.loopers.domain.usercard;

import com.loopers.domain.payment.CardType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class UserCardService {

    private final UserCardRepository userCardRepository;

    @Transactional
    public UserCard saveCard(Long userId, CardType cardType, String cardNo, boolean updateDefaultCard) {
        var existingDefault = userCardRepository.findDefaultByUserId(userId);
        boolean shouldBeDefault = updateDefaultCard || existingDefault.isEmpty();

        if (shouldBeDefault) {
            existingDefault.ifPresent(existing -> {
                existing.unmarkAsDefault();
                userCardRepository.save(existing);
            });
        }

        return userCardRepository.save(new UserCard(userId, cardType, cardNo, shouldBeDefault));
    }
}