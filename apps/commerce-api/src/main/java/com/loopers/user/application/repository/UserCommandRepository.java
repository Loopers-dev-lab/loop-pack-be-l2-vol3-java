package com.loopers.user.application.repository;

import com.loopers.user.domain.model.User;

public interface UserCommandRepository {

	User save(User user);

	User update(User user);
}
