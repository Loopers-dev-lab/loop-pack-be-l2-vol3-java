package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = false)
      public MemberModel saveMember(MemberModel memberModel) {
        //저장하기 전에 이미 같은 loginId가 있는지 확인
        Optional<MemberModel> existing = memberRepository.findByLoginId(memberModel.getLoginId());
        if (existing.isPresent()) {
          throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }

        // 비밀번호 암호화 후 저장
        String encrypted = passwordEncoder.encode(memberModel.getPassword());
        memberModel.encryptPassword(encrypted);

        try {
          return memberRepository.save(memberModel);
        } catch (DataIntegrityViolationException e) {
          throw new CoreException(ErrorType.CONFLICT, "이미 존재하는 아이디입니다.");
        }
      }

    @Transactional(readOnly = true)
    public MemberModel getMember(String loginId) {
        MemberModel model = new MemberModel(loginId);  // 객체 먼저 생성해야 함
        return memberRepository.findByLoginId(model.getLoginId())
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "[id = " + loginId + "] 회원을 찾을 수 없습니다."));
    }
}
