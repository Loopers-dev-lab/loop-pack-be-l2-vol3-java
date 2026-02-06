package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * MemberService 도메인 서비스 테스트
 *
 * 수정사항:
 * - IllegalArgumentException → CoreException으로 변경
 * - ErrorType 검증 추가
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberService 도메인 서비스 테스트")
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private MemberService memberService;

    // ========================================
    // 1. 회원가입 테스트
    // ========================================

    @Test
    @DisplayName("유효한 입력으로 회원가입 성공")
    void register_WithValidInput_ShouldSuccess() {
        // Given: 유효한 회원 정보
        String loginId = "testuser123";
        String loginPw = "Test1234!@#";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 1);
        String email = "test@example.com";

        // Mock 설정
        when(memberRepository.existsByLoginId(loginId)).thenReturn(false);
        when(passwordEncoder.encode(loginPw)).thenReturn("{bcrypt}encoded_password");
        when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When: 회원가입
        MemberModel result = memberService.register(loginId, loginPw, name, birthDate, email);

        // Then: 회원 생성 및 저장
        assertThat(result).isNotNull();
        assertThat(result.getLoginId()).isEqualTo(loginId);
        assertThat(result.getName()).isEqualTo(name);

        // 검증: Repository 메서드 호출 확인
        verify(memberRepository, times(1)).existsByLoginId(loginId);
        verify(passwordEncoder, times(1)).encode(loginPw);
        verify(memberRepository, times(1)).save(any(MemberModel.class));
    }

    @Test
    @DisplayName("로그인 ID 중복 시 CoreException 발생 (DUPLICATE_LOGIN_ID)")
    void register_WithDuplicateLoginId_ShouldThrowCoreException() {
        // Given: 이미 존재하는 로그인 ID
        String duplicateLoginId = "testuser123";

        // Mock 설정: 중복 ID 존재
        when(memberRepository.existsByLoginId(duplicateLoginId)).thenReturn(true);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.register(
                duplicateLoginId,
                "Test1234!@#",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID);
                });

        // 검증: save는 호출되지 않음
        verify(memberRepository, never()).save(any());
    }

    // ========================================
    // 2. 회원 조회 테스트
    // ========================================

    @Test
    @DisplayName("로그인 ID로 회원 조회 성공")
    void findByLoginId_WithExistingId_ShouldReturnMember() {
        // Given: 존재하는 회원
        String loginId = "testuser123";
        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));

        // When: 회원 조회
        MemberModel result = memberService.findByLoginId(loginId);

        // Then: 회원 반환
        assertThat(result).isNotNull();
        assertThat(result.getLoginId()).isEqualTo(loginId);

        verify(memberRepository, times(1)).findByLoginId(loginId);
    }

    @Test
    @DisplayName("존재하지 않는 회원 조회 시 CoreException 발생 (NOT_FOUND)")
    void findByLoginId_WithNonExistingId_ShouldThrowCoreException() {
        // Given: 존재하지 않는 로그인 ID
        String nonExistingId = "nonexistent";

        when(memberRepository.findByLoginId(nonExistingId)).thenReturn(Optional.empty());

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.findByLoginId(nonExistingId))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                });

        verify(memberRepository, times(1)).findByLoginId(nonExistingId);
    }

    // ========================================
    // 3. 비밀번호 변경 테스트
    // ========================================

    @Test
    @DisplayName("유효한 조건으로 비밀번호 변경 성공")
    void changePassword_WithValidConditions_ShouldSuccess() {
        // Given: 기존 회원
        String loginId = "testuser123";
        String currentPassword = "Test1234!@#";
        String newPassword = "NewPass5678$";

        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));
        when(passwordEncoder.matches(currentPassword, existingMember.getLoginPw())).thenReturn(true);
        when(passwordEncoder.matches(newPassword, existingMember.getLoginPw())).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("{bcrypt}new_encoded_password");

        // When: 비밀번호 변경
        memberService.changePassword(loginId, currentPassword, newPassword);

        // Then: 정상 처리
        verify(memberRepository, times(1)).findByLoginId(loginId);
        verify(passwordEncoder, times(2)).matches(anyString(), anyString());  // currentPassword + newPassword 확인
        verify(passwordEncoder, times(1)).encode(newPassword);
    }

    @Test
    @DisplayName("기존 비밀번호 불일치 시 CoreException 발생 (PASSWORD_MISMATCH)")
    void changePassword_WithWrongCurrentPassword_ShouldThrowCoreException() {
        // Given: 기존 회원
        String loginId = "testuser123";
        String wrongCurrentPassword = "WrongPass123!";
        String newPassword = "NewPass5678$";

        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));
        when(passwordEncoder.matches(wrongCurrentPassword, existingMember.getLoginPw())).thenReturn(false);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.changePassword(
                loginId, wrongCurrentPassword, newPassword
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
                });

        // 검증: encode는 호출되지 않음
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @DisplayName("새 비밀번호가 기존과 동일하면 CoreException 발생 (SAME_PASSWORD)")
    void changePassword_WithSamePassword_ShouldThrowCoreException() {
        // Given: 기존 회원
        String loginId = "testuser123";
        String currentPassword = "Test1234!@#";
        String samePassword = "Test1234!@#";

        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));
        when(passwordEncoder.matches(currentPassword, existingMember.getLoginPw())).thenReturn(true);
        when(passwordEncoder.matches(samePassword, existingMember.getLoginPw())).thenReturn(true);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.changePassword(
                loginId, currentPassword, samePassword
        ))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.SAME_PASSWORD);
                });
    }

    // ========================================
    // 4. 인증 테스트
    // ========================================

    @Test
    @DisplayName("올바른 로그인 ID와 비밀번호로 인증 성공")
    void authenticate_WithCorrectCredentials_ShouldSuccess() {
        // Given: 존재하는 회원
        String loginId = "testuser123";
        String password = "Test1234!@#";

        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));
        when(passwordEncoder.matches(password, existingMember.getLoginPw())).thenReturn(true);

        // When: 인증
        MemberModel result = memberService.authenticate(loginId, password);

        // Then: 회원 반환
        assertThat(result).isNotNull();
        assertThat(result.getLoginId()).isEqualTo(loginId);

        verify(memberRepository, times(1)).findByLoginId(loginId);
        verify(passwordEncoder, times(1)).matches(password, existingMember.getLoginPw());
    }

    @Test
    @DisplayName("존재하지 않는 로그인 ID로 인증 실패 (UNAUTHORIZED)")
    void authenticate_WithNonExistingLoginId_ShouldThrowCoreException() {
        // Given: 존재하지 않는 로그인 ID
        String nonExistingId = "nonexistent";

        when(memberRepository.findByLoginId(nonExistingId)).thenReturn(Optional.empty());

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.authenticate(nonExistingId, "anyPassword"))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });

        // 검증: 비밀번호 검증은 호출되지 않음
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 불일치로 인증 실패 (UNAUTHORIZED)")
    void authenticate_WithWrongPassword_ShouldThrowCoreException() {
        // Given: 존재하는 회원, 잘못된 비밀번호
        String loginId = "testuser123";
        String wrongPassword = "WrongPass123!";

        MemberModel existingMember = createMockMember(loginId);

        when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));
        when(passwordEncoder.matches(wrongPassword, existingMember.getLoginPw())).thenReturn(false);

        // When & Then: CoreException 발생
        assertThatThrownBy(() -> memberService.authenticate(loginId, wrongPassword))
                .isInstanceOf(CoreException.class)
                .satisfies(ex -> {
                    CoreException coreEx = (CoreException) ex;
                    assertThat(coreEx.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                });
    }

    // ========================================
    // Helper 메서드
    // ========================================

    /**
     * 테스트용 Mock MemberModel 생성
     */
    private MemberModel createMockMember(String loginId) {
        return MemberModel.createWithEncodedPassword(
                loginId,
                "{bcrypt}encoded_password",
                "홍길동",
                LocalDate.of(1990, 1, 1),
                "test@example.com"
        );
    }
}
