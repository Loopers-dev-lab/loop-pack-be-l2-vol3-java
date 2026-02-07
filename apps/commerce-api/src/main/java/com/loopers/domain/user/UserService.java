package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.transaction.Transactional;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserModel createUser(String loginId, String rawPassword, LocalDate birthDate, String name, String email) {
        if (userRepository.existsByLoginId(loginId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용 중인 아이디입니다.");
        }
        if (userRepository.existsByEmail(email)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 이메일입니다.");
        }
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);

        String encodedPassword = passwordEncoder.encode(rawPassword);
        UserModel user = UserModel.create(loginId, encodedPassword, birthDate, name, email);
        return userRepository.save(user);
    }

    public UserModel authenticate(String loginId, String rawPassword) {
        UserModel user = userRepository.findByLoginId(loginId)
            .orElseThrow(() -> new CoreException(ErrorType.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다."));
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인 정보가 올바르지 않습니다.");
        }
        return user;
    }

    public Boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    public UserModel findById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        UserModel user = findById(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "기존 비밀번호가 일치하지 않습니다.");
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.");
        }

        validatePasswordNotContainsBirthDate(newPassword, user.getBirthDate());

        String newEncodedPassword = passwordEncoder.encode(newPassword);
        user.changePassword(newEncodedPassword);
    }

    private void validatePasswordNotContainsBirthDate(String password, LocalDate birthDate) {
        String birthStr = birthDate.toString().replace("-", "");
        if (password.contains(birthStr)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }
}
