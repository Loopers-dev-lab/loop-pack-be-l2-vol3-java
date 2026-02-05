package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public Member signup(SignupCommand command) {
        MemberId memberId = new MemberId(command.memberId());
        BirthDate birthDate = new BirthDate(command.birthDate());

        if (memberRepository.existsByMemberIdValue(memberId.getValue())) {
            throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 회원 ID입니다.");
        }

        Password.of(command.password(), birthDate);
        String encodedPassword = passwordEncoder.encode(command.password());

        Member member = new Member(
                memberId,
                Password.ofEncoded(encodedPassword),
                new Name(command.name()),
                new Email(command.email()),
                birthDate
        );

        return memberRepository.save(member);
    }

    public void changePassword(Member member, String currentPassword, String newPassword) {
        member.updatePassword(currentPassword, newPassword, passwordEncoder);
    }

    public record SignupCommand(
            String memberId,
            String password,
            String name,
            String email,
            String birthDate
    ) {}
}
