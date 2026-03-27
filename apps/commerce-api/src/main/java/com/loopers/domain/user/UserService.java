package com.loopers.domain.user;

import com.loopers.domain.outbox.DomainEventTypes;
import com.loopers.domain.outbox.DomainKafkaTopics;
import com.loopers.domain.outbox.TransactionalOutboxWriter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final TransactionalOutboxWriter transactionalOutboxWriter;

    public UserService(UserRepository userRepository, TransactionalOutboxWriter transactionalOutboxWriter) {
        this.userRepository = userRepository;
        this.transactionalOutboxWriter = transactionalOutboxWriter;
    }

    @Transactional
    public UserModel signUp(
        UserId userId,
        Email email,
        BirthDate birthDate,
        Password password,
        Gender gender
    ) {
        String value = userId.value();
        if (userRepository.existsByUserId(value)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자 ID입니다: " + value);
        }

        try {
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);
            UserModel saved = userRepository.save(user);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("userId", saved.getId());
            payload.put("loginId", saved.getUserId());
            transactionalOutboxWriter.record(
                    DomainKafkaTopics.USER_EVENTS,
                    String.valueOf(saved.getId()),
                    DomainEventTypes.USER_REGISTERED,
                    payload);
            return saved;
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자 ID입니다: " + value);
        }
    }

    @Transactional(readOnly = true)
    public Optional<UserModel> getMyInfo(String userId) {
        return userRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Long getPoints(String userId) {
        return userRepository.findByUserId(userId)
            .map(UserModel::getPoints)
            .orElse(null);
    }

    @Transactional
    public void updatePassword(String userId, String currentPassword, String newPassword) {
        UserModel user = userRepository.findByUserId(userId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다: " + userId));

        try {
            user.updatePassword(currentPassword, newPassword);
        } catch (IllegalArgumentException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, e.getMessage());
        }
    }
}
