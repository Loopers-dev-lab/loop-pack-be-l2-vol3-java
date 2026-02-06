package com.loopers.domain.user;

import com.loopers.application.user.UserInfo;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class UserServiceIntegrationTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("내 정보 조회 시 이름 마지막 글자가 마스킹된다.")
    void getMyInfo_returnsUserInfoWithMaskedName() {
        // arrange
        String loginId = "testUser123";
        User user = UserFixture.builder()
                               .loginId(loginId)
                               .name("박자바")
                               .build();
        userJpaRepository.save(user);

        // act
        UserInfo result = userService.getMyInfo(loginId);

        // assert
        assertAll(
            () -> assertThat(result.loginId()).isEqualTo(loginId),
            () -> assertThat(result.name()).isEqualTo("박자*")
        );
    }
}
