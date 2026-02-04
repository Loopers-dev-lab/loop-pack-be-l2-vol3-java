package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UserServiceTest {
    private InMemoryUserRepository userRepository;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        userService = new UserService(userRepository);
    }

    @DisplayName("내 정보 조회 시 이름의 마지막 글자는 마스킹(*)되어 반환된다")
    @Test
    void getMyInfo_masks_last_character_of_name() {
        // given
        User user = UserFixture.builder()
                               .name("테스터")
                               .build();

        userRepository.save(user);

        // when
        UserInfo myInfo = userService.getMyInfo(user.getLoginId());

        // then
        assertThat(myInfo.name()).isEqualTo("테스*");
        assertThat(myInfo.loginId()).isEqualTo(user.getLoginId());
        assertThat(myInfo.email()).isEqualTo(user.getEmail());
        assertThat(myInfo.birthDate()).isEqualTo(user.getBirthDate());
    }

}
