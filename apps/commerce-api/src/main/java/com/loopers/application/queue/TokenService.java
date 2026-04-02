package com.loopers.application.queue;

public interface TokenService {
    void issue(Long userId);
    boolean validate(Long userId);
    void delete(Long userId);
}
