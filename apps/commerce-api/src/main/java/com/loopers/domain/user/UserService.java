package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserModel signUp(
        String userId,
        Email email,
        BirthDate birthDate,
        Password password,
        Gender gender
    ) {
        if (userRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자 ID입니다: " + userId);
        }

        try {
            UserModel user = UserModel.create(userId, email, birthDate, password, gender);
            return userRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 사용자 ID입니다: " + userId);
        }
    }

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(String userId) {
        return userRepository.findByUserId(userId)
            .map(UserInfo::from)
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
