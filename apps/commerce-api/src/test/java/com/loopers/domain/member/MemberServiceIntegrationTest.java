package com.loopers.domain.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MemberServiceIntegrationTest {

  @Autowired
  private MemberService memberService;

  @Autowired
  private DatabaseCleanUp databaseCleanUp;

  @AfterEach
  void tearDown() {
    databaseCleanUp.truncateAllTables();
  }

  @DisplayName("회원가입을 성공한다")
  @Nested
  class SaveMember {

    @DisplayName("회원가입에 필요한 정보가 들어오면 디비에 저장하고 저장한 아이디를 조회한다")
    @Test
    void returnsMemberInfo_whenValidMemberInfoIsProvided() {
      // arrange
      MemberModel memberModel = new MemberModel("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

      // act
      memberService.saveMember(memberModel);
      MemberModel result = memberService.getMember(memberModel.getLoginId());

      // assert
      assertAll(
          () -> assertThat(result).isNotNull(),
          () -> assertThat(result.getLoginId()).isEqualTo(memberModel.getLoginId()),
          () -> assertThat(result.getName()).isEqualTo(memberModel.getName()),
          () -> assertThat(result.getBirthDate()).isEqualTo(memberModel.getBirthDate()),
          () -> assertThat(result.getEmail()).isEqualTo(memberModel.getEmail())
      );
    }

    @DisplayName("중복 ID로 가입 시도하면 예외가 발생한다")
    @Test
    void throwsException_whenExistIdIsTryToSaveMember() {
      // arrange - 먼저 한 명 가입시키기
      memberService.saveMember(new MemberModel("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));

      // act - 같은 ID로 또 가입 시도
      CoreException exception = assertThrows(CoreException.class, () -> {
        memberService.saveMember(new MemberModel("testuser", "Test1234!", "홍길동", "19900101", "test@example.com"));
      });

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
    }

  }

  @DisplayName("회원을 조회할 때, ")
  @Nested
  class GetMember {

      @DisplayName("존재하는 ID를 주면, 해당 유저 정보를 반환한다.")
      @Test
      void returnsExampleInfo_whenValidIdIsProvided() {
          // arrange  // 정보저장
          MemberModel memberModel = new MemberModel("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");
          memberService.saveMember(memberModel);

          // act
          MemberModel result = memberService.getMember(memberModel.getLoginId());

          // assert
          assertAll(
                  () -> assertThat(result).isNotNull(),
                  () -> assertThat(result.getLoginId()).isEqualTo(memberModel.getLoginId()),
                  () -> assertThat(result.getName()).isEqualTo(memberModel.getName()),
                  () -> assertThat(result.getBirthDate()).isEqualTo(memberModel.getBirthDate()),
                  () -> assertThat(result.getEmail()).isEqualTo(memberModel.getEmail())
          );
      }

      @DisplayName("존재하지 않는 ID로 조회하면 예외가 발생한다")
      @Test
      void throwsException_whenMemberNotFound() {
        // arrange
        String loginId = "testuser"; // Assuming this ID does not exist

      // act
      CoreException exception = assertThrows(CoreException.class, () -> {
        memberService.getMember(loginId);
      });

      // assert
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
    }
  }
}
