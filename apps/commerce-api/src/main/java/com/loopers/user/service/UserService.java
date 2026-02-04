package com.loopers.user.service;

import com.loopers.user.domain.User;
import com.loopers.user.repository.UserRepository;

public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User signUp(String loginId, String password, String name, String birthDate, String email) {
        User user = new User(loginId, password, name, birthDate, email);

        if(userRepository.existsByLoginId(loginId)){
            throw new IllegalArgumentException("이미 가입된 ID 입니다.");
        }

        return userRepository.save(user);
    }
}
