package com.loopers.domain.member;

/*
    # Command : 비즈니스 로직을 수행하기 위한 입력 데이터로 도메인 서비스로 전달 되는 객체
    ex) 고객이 입력한 값들을 담은 객체

*/

public record SignupCommand(
        String loginId,
        String password,
        String name,
        String email,
        String birthDate) {
}
