package com.loopers.user.infrastructure.mapper;


import com.loopers.user.domain.model.User;
import com.loopers.user.infrastructure.entity.UserEntity;
import org.springframework.stereotype.Component;


@Component
public class UserEntityMapper {

	public UserEntity toEntity(User user) {
		return UserEntity.of(
			user.getId(),
			user.getLoginId(),
			user.getPassword().value(),
			user.getName(),
			user.getBirthday(),
			user.getEmail()
		);
	}


	public User toDomain(UserEntity entity) {
		return User.reconstruct(
			entity.getId(),
			entity.getLoginId(),
			entity.getPassword(),
			entity.getName(),
			entity.getBirthday(),
			entity.getEmail()
		);
	}

}
