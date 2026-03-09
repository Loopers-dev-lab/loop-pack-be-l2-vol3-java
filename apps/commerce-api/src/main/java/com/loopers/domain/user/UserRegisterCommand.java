package com.loopers.domain.user;

/**
 * 사용자 회원가입 커맨드.
 * <p>
 * interfaces → domain 경계에서 사용되는 회원가입 요청 객체이다.
 * UserService가 단순 도메인으로 Facade를 거치지 않으므로 도메인 패키지에 위치한다.
 * </p>
 *
 * @param loginId     로그인 ID
 * @param rawPassword 평문 비밀번호
 * @param userName    사용자 이름
 * @param birthday    생년월일
 * @param email       이메일
 * @param address     주소
 */
public record UserRegisterCommand(String loginId, String rawPassword,
                                   String userName, String birthday,
                                   String email, String address) {
}
