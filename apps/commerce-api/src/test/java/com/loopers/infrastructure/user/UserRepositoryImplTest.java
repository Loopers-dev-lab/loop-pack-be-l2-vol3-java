package com.loopers.infrastructure.user;

import com.loopers.domain.user.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class UserRepositoryImplTest {

    @Autowired
    UserRepositoryImpl userRepository;

    @Test
    void 존재하는_loginId로_조회하면_User를_반환() {
        // given
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("loopers123")).thenReturn("encrypted");

        LoginId loginId = LoginId.from("loopers");
        Password password = Password.of("loopers123", encoder);
        Name name = Name.from("루퍼스");
        BirthDate birthDate = BirthDate.from(LocalDate.of(1996, 11, 22));
        Email email = Email.from("test@loopers.im");

        User user = User.create(loginId, password, name, birthDate, email);
        userRepository.save(user);

        // when
        Optional<User> findUser = userRepository.findByLoginId(loginId);

        // then
        assertThat(findUser).isPresent();
    }

    @Test
    void 존재하지_않는_loginId로_조회하면_빈_Optional_반환() {
        // given-when
        Optional<User> findUser = userRepository.findByLoginId(LoginId.from("loop123"));

        // then
        assertThat(findUser).isEmpty();
    }
}
