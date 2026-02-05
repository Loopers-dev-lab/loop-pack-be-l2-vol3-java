package com.loopers.domain.member;

import com.loopers.support.crypto.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemberServiceUnitTest {

	@Mock
	private MemberRepository memberRepository;

	@InjectMocks
	private MemberService memberService;

	@DisplayName("[Dummy] 의존성이 사용되지 않는 경우,")
	@Nested
	class DummyTest {

		@DisplayName("현재 비밀번호와 동일한 비밀번호로 변경하면, Repository에 접근하지 않고 예외가 발생한다.")
		@Test
		void failWithSamePasswordWithoutRepositoryAccess() {
			// given
			MemberRepository dummyRepository = mock(MemberRepository.class);
			MemberService service = new MemberService(dummyRepository);

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					service.changePassword("testuser", "password1!@", "password1!@")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
			verifyNoInteractions(dummyRepository);
		}
	}

	@DisplayName("[Stub] 고정된 응답으로 흐름을 제어할 때,")
	@Nested
	class StubTest {

		@DisplayName("findByLoginId가 빈 값을 반환하도록 stub하면, 회원가입이 정상 처리된다.")
		@Test
		void registerSuccessWithStub() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			String name = "홍길동";
			LocalDate birthDate = LocalDate.of(2000, 6, 5);
			String email = "test@example.com";

			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
			when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

			// when
			MemberModel result = memberService.register(loginId, password, name, birthDate, email);

			// then
			assertAll(
					() -> assertThat(result.getLoginId()).isEqualTo(loginId),
					() -> assertThat(result.getName()).isEqualTo(name),
					() -> assertThat(result.getPassword()).isEqualTo(PasswordEncoder.encode(password)),
					() -> assertThat(result.getPassword()).isNotEqualTo(password)
			);
		}

		@DisplayName("findByLoginId가 회원을 반환하도록 stub하면, 내 정보가 조회된다.")
		@Test
		void getMyInfoSuccessWithStub() {
			// given
			String loginId = "testuser";
			String password = "password1!@";
			MemberModel member = new MemberModel(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			member.applyEncodedPassword(PasswordEncoder.encode(password));

			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

			// when
			MemberModel result = memberService.getMyInfo(loginId, password);

			// then
			assertAll(
					() -> assertThat(result.getLoginId()).isEqualTo(loginId),
					() -> assertThat(result.getName()).isEqualTo("홍길동")
			);
		}

		@DisplayName("findByLoginId가 빈 값을 반환하도록 stub하면, NOT_FOUND 예외가 발생한다.")
		@Test
		void failWithNotFoundLoginId() {
			// given
			when(memberRepository.findByLoginId("nonexistent")).thenReturn(Optional.empty());

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					memberService.getMyInfo("nonexistent", "password1!@")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
		}

		@DisplayName("findByLoginId가 다른 비밀번호를 가진 회원을 반환하도록 stub하면, BAD_REQUEST 예외가 발생한다.")
		@Test
		void failWithWrongPassword() {
			// given
			String loginId = "testuser";
			MemberModel member = new MemberModel(loginId, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			member.applyEncodedPassword(PasswordEncoder.encode("password1!@"));

			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					memberService.getMyInfo(loginId, "wrongpass1!@")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
		}
	}

	@DisplayName("[Mock] 메서드 호출 여부를 검증할 때,")
	@Nested
	class MockTest {

		@DisplayName("회원가입 성공 시, save가 1회 호출된다.")
		@Test
		void verifySaveCalledOnRegister() {
			// given
			String loginId = "testuser";
			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
			when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));

			// when
			memberService.register(loginId, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// then
			verify(memberRepository, times(1)).findByLoginId(loginId);
			verify(memberRepository, times(1)).save(any(MemberModel.class));
		}

		@DisplayName("중복 loginId로 가입 시, save가 호출되지 않는다.")
		@Test
		void verifySaveNotCalledOnDuplicateLoginId() {
			// given
			String loginId = "testuser";
			MemberModel existingMember = new MemberModel(loginId, "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existingMember));

			// when
			assertThrows(CoreException.class, () ->
					memberService.register(loginId, "other1234!@", "김철수", LocalDate.of(1995, 3, 10), "other@example.com")
			);

			// then
			verify(memberRepository, times(1)).findByLoginId(loginId);
			verify(memberRepository, never()).save(any(MemberModel.class));
		}

		@DisplayName("비밀번호 변경 성공 시, findByLoginId가 1회 호출된다.")
		@Test
		void verifyFindByLoginIdCalledOnChangePassword() {
			// given
			String loginId = "testuser";
			String currentPassword = "password1!@";
			MemberModel member = new MemberModel(loginId, currentPassword, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			member.applyEncodedPassword(PasswordEncoder.encode(currentPassword));
			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));

			// when
			memberService.changePassword(loginId, currentPassword, "newpass1!@#");

			// then
			verify(memberRepository, times(1)).findByLoginId(loginId);
		}
	}

	@DisplayName("[Spy] 실제 객체의 동작을 감시할 때,")
	@Nested
	class SpyTest {

		@DisplayName("changePassword 호출 시, 내부적으로 getMyInfo가 호출된다.")
		@Test
		void verifyGetMyInfoCalledInChangePassword() {
			// given
			String loginId = "testuser";
			String currentPassword = "password1!@";
			String newPassword = "newpass1!@#";
			MemberModel member = new MemberModel(loginId, currentPassword, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			member.applyEncodedPassword(PasswordEncoder.encode(currentPassword));

			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));
			MemberService spyService = spy(new MemberService(memberRepository));

			// when
			spyService.changePassword(loginId, currentPassword, newPassword);

			// then
			verify(spyService).getMyInfo(loginId, currentPassword);
			assertThat(member.getPassword()).isEqualTo(PasswordEncoder.encode(newPassword));
		}

		@DisplayName("register 호출 시, 실제 암호화 로직이 동작하며 save가 호출된다.")
		@Test
		void spyServiceCallsRealRegisterLogic() {
			// given
			String loginId = "testuser";
			String password = "password1!@";

			when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
			when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
			MemberService spyService = spy(new MemberService(memberRepository));

			// when
			MemberModel result = spyService.register(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// then
			verify(spyService).register(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			assertThat(result.getPassword()).isEqualTo(PasswordEncoder.encode(password));
		}
	}

	@DisplayName("[Fake] InMemoryMemberRepository로 테스트할 때,")
	@Nested
	class FakeTest {

		static class InMemoryMemberRepository implements MemberRepository {
			private final Map<String, MemberModel> store = new HashMap<>();

			@Override
			public MemberModel save(MemberModel member) {
				store.put(member.getLoginId(), member);
				return member;
			}

			@Override
			public Optional<MemberModel> findByLoginId(String loginId) {
				return Optional.ofNullable(store.get(loginId));
			}
		}

		@DisplayName("회원가입 후 내 정보 조회가 정상 동작한다.")
		@Test
		void registerAndGetMyInfo() {
			// given
			InMemoryMemberRepository fakeRepository = new InMemoryMemberRepository();
			MemberService service = new MemberService(fakeRepository);

			String loginId = "testuser";
			String password = "password1!@";

			// when
			service.register(loginId, password, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");
			MemberModel result = service.getMyInfo(loginId, password);

			// then
			assertAll(
					() -> assertThat(result.getLoginId()).isEqualTo(loginId),
					() -> assertThat(result.getName()).isEqualTo("홍길동"),
					() -> assertThat(result.getPassword()).isEqualTo(PasswordEncoder.encode(password))
			);
		}

		@DisplayName("중복 loginId로 가입 시 CONFLICT 예외가 발생한다.")
		@Test
		void failWithDuplicateLoginId() {
			// given
			InMemoryMemberRepository fakeRepository = new InMemoryMemberRepository();
			MemberService service = new MemberService(fakeRepository);
			service.register("testuser", "password1!@", "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			CoreException result = assertThrows(CoreException.class, () ->
					service.register("testuser", "other1234!@", "김철수", LocalDate.of(1995, 3, 10), "other@example.com")
			);

			// then
			assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
		}

		@DisplayName("비밀번호 변경 후 새 비밀번호로 조회가 가능하다.")
		@Test
		void changePasswordAndVerify() {
			// given
			InMemoryMemberRepository fakeRepository = new InMemoryMemberRepository();
			MemberService service = new MemberService(fakeRepository);
			String loginId = "testuser";
			String currentPassword = "password1!@";
			String newPassword = "newpass1!@#";
			service.register(loginId, currentPassword, "홍길동", LocalDate.of(2000, 6, 5), "test@example.com");

			// when
			service.changePassword(loginId, currentPassword, newPassword);

			// then
			MemberModel result = service.getMyInfo(loginId, newPassword);
			assertThat(result.getLoginId()).isEqualTo(loginId);
		}
	}
}
