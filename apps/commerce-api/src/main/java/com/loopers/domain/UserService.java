package com.loopers.domain;

import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional
    public UserModel save(UserModel userModel) {
        return userRepository.save(userModel);
    }

    public Boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
