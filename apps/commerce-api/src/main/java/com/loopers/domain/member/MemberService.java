package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@RequiredArgsConstructor
@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public Member register(String loginId, String plainPassword, String name,
                           String birthDate, String email) {
        LoginId loginIdVo = new LoginId(loginId);

        if (memberRepository.existsByLoginId(loginIdVo)) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 ID입니다.");
        }

        BirthDate birthDateVo = BirthDate.from(birthDate);
        Password password = Password.create(plainPassword, birthDateVo.value(), passwordEncoder);
        Email emailVo = new Email(email);

        Member member = new Member(loginIdVo, password, name, birthDateVo, emailVo);
        return memberRepository.save(member);
    }

    public Optional<Member> findByLoginId(String loginId) {
        return memberRepository.findByLoginId(new LoginId(loginId));
    }

    public void changePassword(Member member, String currentPlain, String newPlain) {
        if (!member.getPassword().matches(currentPlain, passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (member.getPassword().matches(newPlain, passwordEncoder)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "새 비밀번호는 현재 비밀번호와 달라야 합니다.");
        }

        Password newPassword = Password.create(
            newPlain, member.getBirthDate().value(), passwordEncoder);
        member.changePassword(newPassword);
    }
}
