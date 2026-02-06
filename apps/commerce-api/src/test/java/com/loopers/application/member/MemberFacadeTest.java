package com.loopers.application.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MemberFacade 테스트
 *
 * TDD Red Phase: 실패하는 테스트를 먼저 작성
 *
 * Facade는 여러 서비스를 조합하여 하나의 비즈니스 플로우를 완성
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberFacade Application 테스트")
class MemberFacadeTest {

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberFacade memberFacade;

    // ========================================
    // 1. 회원가입 Facade 테스트
    // ========================================

    @Test
    @DisplayName("회원가입 Facade - 성공")
    void register_WithValidInput_ShouldReturnMemberInfo() {
        // Given: 유효한 입력
        String loginId = "testuser123";
        String loginPw = "Test1234!@#";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "test@example.com";

        // Mock: Service 동작 정의
        MemberModel mockMember = createMockMember(loginId, name, birthDate, email);
        when(memberService.register(loginId, loginPw, name, birthDate, email))
                .thenReturn(mockMember);

        // When: 회원가입
        MemberInfo result = memberFacade.register(loginId, loginPw, name, birthDate, email);

        // Then: MemberInfo 반환
        assertThat(result).isNotNull();
        assertThat(result.getLoginId()).isEqualTo(loginId);
        assertThat(result.getName()).isEqualTo(name);
        assertThat(result.getBirthDate()).isEqualTo(birthDate);
        assertThat(result.getEmail()).isEqualTo(email);

        // 검증: Service 호출 확인
        verify(memberService, times(1)).register(loginId, loginPw, name, birthDate, email);
    }

    @Test
    @DisplayName("회원가입 Facade - 중복 ID 예외 전파")
    void register_WithDuplicateLoginId_ShouldPropagateException() {
        // Given: 중복된 로그인 ID
        String duplicateLoginId = "testuser123";

        // Mock: Service에서 예외 발생
        when(memberService.register(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new IllegalArgumentException("이미 사용 중인 로그인 ID입니다."));

        // When & Then: 예외 전파
        assertThatThrownBy(() -> memberFacade.register(
                duplicateLoginId,
                "Test1234!@#",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용 중인 로그인 ID입니다");
    }

    // ========================================
    // 2. 내 정보 조회 Facade 테스트
    // ========================================

    @Test
    @DisplayName("내 정보 조회 Facade - 인증 후 마스킹된 정보 반환")
    void getMyInfo_WithValidCredentials_ShouldReturnMaskedInfo() {
        // Given: 유효한 인증 정보
        String loginId = "testuser123";
        String loginPw = "Test1234!@#";

        MemberModel mockMember = createMockMember(loginId, "홍길동",
                LocalDate.of(1990, 1, 1), "test@example.com");

        // Mock: 인증 성공
        when(memberService.authenticate(loginId, loginPw))
                .thenReturn(mockMember);

        // When: 내 정보 조회
        MemberInfo result = memberFacade.getMyInfo(loginId, loginPw);

        // Then: 마스킹된 정보 반환
        assertThat(result).isNotNull();
        assertThat(result.getLoginId()).isEqualTo(loginId);
        assertThat(result.getMaskedName()).isEqualTo("홍길*");  // 마스킹됨
        assertThat(result.getName()).isEqualTo("홍길동");  // 원본도 포함

        // 검증: 인증 메서드 호출
        verify(memberService, times(1)).authenticate(loginId, loginPw);
    }

    @Test
    @DisplayName("내 정보 조회 Facade - 인증 실패 시 예외")
    void getMyInfo_WithInvalidCredentials_ShouldThrowException() {
        // Given: 잘못된 인증 정보
        String loginId = "testuser123";
        String wrongPassword = "WrongPass123!";

        // Mock: 인증 실패
        when(memberService.authenticate(loginId, wrongPassword))
                .thenThrow(new IllegalArgumentException("로그인 ID 또는 비밀번호가 일치하지 않습니다."));

        // When & Then: 예외 발생
        assertThatThrownBy(() -> memberFacade.getMyInfo(loginId, wrongPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다");
    }

    // ========================================
    // 3. 비밀번호 변경 Facade 테스트
    // ========================================

    @Test
    @DisplayName("비밀번호 변경 Facade - 인증 후 변경 성공")
    void changePassword_WithValidInput_ShouldSuccess() {
        // Given: 유효한 입력
        String loginId = "testuser123";
        String currentLoginPw = "Test1234!@#";
        String currentPassword = "Test1234!@#";
        String newPassword = "NewPass5678$";

        MemberModel mockMember = createMockMember(loginId, "홍길동",
                LocalDate.of(1990, 1, 1), "test@example.com");

        // Mock: 인증 성공
        when(memberService.authenticate(loginId, currentLoginPw))
                .thenReturn(mockMember);

        // Mock: 비밀번호 변경 성공 (void 메서드)
        doNothing().when(memberService).changePassword(loginId, currentPassword, newPassword);

        // When: 비밀번호 변경
        memberFacade.changePassword(loginId, currentLoginPw, currentPassword, newPassword);

        // Then: 정상 처리
        // 검증: 인증 및 변경 메서드 호출
        verify(memberService, times(1)).authenticate(loginId, currentLoginPw);
        verify(memberService, times(1)).changePassword(loginId, currentPassword, newPassword);
    }

    @Test
    @DisplayName("비밀번호 변경 Facade - 인증 실패 시 예외")
    void changePassword_WithAuthenticationFailure_ShouldThrowException() {
        // Given: 잘못된 인증 정보
        String loginId = "testuser123";
        String wrongLoginPw = "WrongPass123!";

        // Mock: 인증 실패
        when(memberService.authenticate(loginId, wrongLoginPw))
                .thenThrow(new IllegalArgumentException("로그인 ID 또는 비밀번호가 일치하지 않습니다."));

        // When & Then: 예외 발생
        assertThatThrownBy(() -> memberFacade.changePassword(
                loginId, wrongLoginPw, "Test1234!@#", "NewPass5678$"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다");

        // 검증: 비밀번호 변경은 호출되지 않음
        verify(memberService, never()).changePassword(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 변경 Facade - 현재 비밀번호 불일치 시 예외")
    void changePassword_WithWrongCurrentPassword_ShouldThrowException() {
        // Given: 인증은 성공하지만 현재 비밀번호 불일치
        String loginId = "testuser123";
        String loginPw = "Test1234!@#";
        String wrongCurrentPassword = "WrongCurrent123!";
        String newPassword = "NewPass5678$";

        MemberModel mockMember = createMockMember(loginId, "홍길동",
                LocalDate.of(1990, 1, 1), "test@example.com");

        // Mock: 인증 성공
        when(memberService.authenticate(loginId, loginPw))
                .thenReturn(mockMember);

        // Mock: 비밀번호 변경 실패
        doThrow(new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다."))
                .when(memberService).changePassword(loginId, wrongCurrentPassword, newPassword);

        // When & Then: 예외 발생
        assertThatThrownBy(() -> memberFacade.changePassword(
                loginId, loginPw, wrongCurrentPassword, newPassword
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");
    }

    // ========================================
    // Helper 메서드
    // ========================================

    /**
     * 테스트용 Mock MemberModel 생성
     */
    private MemberModel createMockMember(String loginId, String name,
                                         LocalDate birthDate, String email) {
        return MemberModel.createWithEncodedPassword(
                loginId,
                "{bcrypt}encoded_password",
                name,
                birthDate,
                email
        );
    }
}
