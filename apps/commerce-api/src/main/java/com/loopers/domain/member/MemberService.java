package com.loopers.domain.member;

import com.loopers.support.crypto.PasswordEncoder;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class MemberService {

	private final MemberRepository memberRepository;

	@Transactional
	public MemberModel register(String loginId, String password, String name, LocalDate birthDate, String email) {
		memberRepository.findByLoginId(loginId)
				.ifPresent(member -> {
					throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 loginId입니다.");
				});

		MemberModel member = new MemberModel(loginId, password, name, birthDate, email);
		member.applyEncodedPassword(PasswordEncoder.encode(password));
		return memberRepository.save(member);
	}

	@Transactional(readOnly = true)
	public MemberModel getMyInfo(String loginId, String password) {
		MemberModel member = memberRepository.findByLoginId(loginId)
				.orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 회원입니다."));

		if (!PasswordEncoder.matches(password, member.getPassword())) {
			throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
		}

		return member;
	}

	@Transactional
	public void changePassword(String loginId, String currentPassword, String newPassword) {
		if (currentPassword.equals(newPassword)) {
			throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다.");
		}
		MemberModel member = getMyInfo(loginId, currentPassword);
		member.changePassword(newPassword);
		member.applyEncodedPassword(PasswordEncoder.encode(newPassword));
	}
}
