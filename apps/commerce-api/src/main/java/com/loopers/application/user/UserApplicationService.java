package com.loopers.application.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.Phone;
import com.loopers.domain.user.vo.UserId;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class UserApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User register(RegisterCommand command) {
        UserId userId = new UserId(command.userId());
        Password rawPassword = new Password(command.rawPassword());
        Name name = new Name(command.name());
        Email email = new Email(command.email());
        BirthDate birthDate = BirthDate.of(command.birthDate());
        Phone phone = new Phone(command.phone());

        if (userRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }

        User user = new User(userId, rawPassword, name, email, birthDate, phone);
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(user.password().value()));
        User userWithEncodedPassword = new User(
                user.id(),
                encodedPassword,
                user.name(),
                user.email(),
                user.birthDate(),
                user.phone()
        );

        try {
            return userRepository.save(userWithEncodedPassword);
        } catch (DataIntegrityViolationException e) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }
    }

    @Transactional(readOnly = true)
    public boolean checkDuplicateLoginId(String loginId) {
        UserId userId = new UserId(loginId);
        return userRepository.existsByUserId(userId);
    }

    @Transactional
    public void changePassword(ChangePasswordCommand command) {
        User user = userRepository.findByUserId(command.userId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        if (passwordEncoder.matches(command.newRawPassword(), user.password().value())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }

        Password newRawPassword = new Password(command.newRawPassword());
        new User(user.id(), newRawPassword, user.name(), user.email(), user.birthDate(), user.phone());
        Password encodedPassword = Password.ofEncoded(passwordEncoder.encode(newRawPassword.value()));
        User updatedUser = new User(
                user.id(),
                encodedPassword,
                user.name(),
                user.email(),
                user.birthDate(),
                user.phone()
        );
        userRepository.save(updatedUser);
    }
}
