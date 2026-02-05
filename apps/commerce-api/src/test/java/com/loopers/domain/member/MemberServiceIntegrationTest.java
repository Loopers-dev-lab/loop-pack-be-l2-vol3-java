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
      assertAll(() -> assertThat(result).isNotNull(), () -> assertThat(result.getLoginId()).isEqualTo(memberModel.getLoginId()), () -> assertThat(result.getName()).isEqualTo(memberModel.getName()), () -> assertThat(result.getBirthDate()).isEqualTo(memberModel.getBirthDate()), () -> assertThat(result.getEmail()).isEqualTo(memberModel.getEmail()));
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
      assertAll(() -> assertThat(result).isNotNull(), () -> assertThat(result.getLoginId()).isEqualTo(memberModel.getLoginId()), () -> assertThat(result.getName()).isEqualTo(memberModel.getName()), () -> assertThat(result.getBirthDate()).isEqualTo(memberModel.getBirthDate()), () -> assertThat(result.getEmail()).isEqualTo(memberModel.getEmail()));
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


  @DisplayName("비밀번호 변경을 할 때, ")
  @Nested
  class ChangePassword {

    @DisplayName("기존 비밀번호와 새 비밀번호가 유효하면, 비밀번호가 변경된다.")
    @Test
    void changesPassword_whenOldAndNewPasswordsAreValid() {
      // arrange
      String loginId = "testuser";
      String prevPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@test.co.kr";

      // act
      // 기존 등록된 디비 설정
      MemberModel member = new MemberModel(loginId, prevPassword, name, birthDate, email);
      memberService.saveMember(member);

      // 클라에서 입력한 아이디와 기존 비밀번호, 새로운 비밀번호
      MemberModel insertedMember = new MemberModel(loginId, prevPassword);
      String newPassword = "NewPass123!";

      // act
      memberService.changePassword(insertedMember, newPassword);

      // assert
      MemberModel updatedMember = memberService.getMember("testuser");
      // 비밀번호가 변경되었는지 확인 (암호화된 비밀번호 비교)
      assertThat(updatedMember.getPassword()).isNotEqualTo(insertedMember.getPassword());
    }

    @DisplayName("기존 비밀번호가 일치하지 않으면, 예외가 발생한다.")
    @Test
    void throwsException_whenOldPasswordDoesNotMatch() {
      // arrange
      String loginId = "testuser";
      String prevPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@test.co.kr";

      // act
      // 기존 등록된 디비 설정
      MemberModel member = new MemberModel(loginId, prevPassword, name, birthDate, email);
      memberService.saveMember(member);

      // 클라에서 입력한 아이디와 기존 비밀번호, 새로운 비밀번호
      String wrongPrevPassword = "WrongPass!";
      MemberModel insertedMember = new MemberModel(loginId, wrongPrevPassword);
      String newPassword = "NewPass123!";

      // act
      CoreException exception = assertThrows(CoreException.class, () -> {
        memberService.changePassword(insertedMember, newPassword);
      });

      // assert - ErrorType.UNAUTHORIZED
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
    }

    @DisplayName("새 비밀번호가 기존 비밀번호와 같으면, 예외가 발생한다.")
    @Test
    void throwsException_whenNewPasswordIsSameAsOld() {
      // arrange
      String loginId = "testuser";
      String prevPassword = "Test1234!";
      String name = "홍길동";
      String birthDate = "19900101";
      String email = "test@test.co.kr";

      MemberModel member = new MemberModel(loginId, prevPassword, name, birthDate, email);
      memberService.saveMember(member);

      // 클라에서 입력한 아이디와 기존 비밀번호, 새로운 비밀번호
      MemberModel insertedMember = new MemberModel(loginId, prevPassword);
      String newPassword = "Test1234!";

      // act
      CoreException exception = assertThrows(CoreException.class, () -> {
        memberService.changePassword(insertedMember, newPassword);
      });

      // assert - ErrorType.UNAUTHORIZED 또는 BAD_REQUEST
      assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
  }
}
