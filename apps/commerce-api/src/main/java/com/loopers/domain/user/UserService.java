package com.loopers.domain.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.UserId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserService {

    private static final String ERROR_PASSWORD_NOT_ENCODED = "비밀번호가 암호화되지 않았습니다";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterCommand command) {
        UserId userId = new UserId(command.getUserId());
        Password password = new Password(command.getRawPassword());
        Name name = new Name(command.getName());
        Email email = new Email(command.getEmail());
        BirthDate birthDate = BirthDate.of(command.getBirthDate());

        if (userRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }

        User user = new User(userId, password, name, email, birthDate);
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(user.password().value()));
        if (!encodedPassword.isEncoded()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, ERROR_PASSWORD_NOT_ENCODED);
        }
        User userWithEncodedPassword = new User(user.id(), encodedPassword, user.name(), user.email(), user.birthDate());
        return userRepository.save(userWithEncodedPassword);
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findByUserId(command.getUserId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (passwordEncoder.matches(command.getNewRawPassword(), user.password().value())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        Password newPassword = new Password(command.getNewRawPassword());
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(newPassword.value()));
        if (!encodedPassword.isEncoded()) {
            throw new CoreException(ErrorType.INTERNAL_ERROR, ERROR_PASSWORD_NOT_ENCODED);
        }
        User updatedUser = new User(user.id(), encodedPassword, user.name(), user.email(), command.getBirthDate());
        userRepository.save(updatedUser);
    }
}
