package com.loopers.infrastructure.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.LoginId;
import com.loopers.domain.user.vo.UserName;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

/**
 * UserMapper
 * Domain POJO ↔ JPA Entity 변환
 */
@Component
public class UserMapper {

    /**
     * Domain → JPA Entity
     */
    public UserEntity toEntity(User domain) {
        UserEntity entity = new UserEntity();
        entity.setId(domain.getId());

        // VO → 개별 필드 분해
        entity.setLoginId(domain.getLoginId().getValue());
        entity.setPassword(domain.getPassword());
        entity.setName(domain.getName().getValue());
        entity.setBirthDate(domain.getBirthDate().getValue());
        entity.setEmail(domain.getEmail().getValue());

        ZonedDateTime now = ZonedDateTime.now();
        entity.setCreatedAt(domain.getCreatedAt() != null ? domain.getCreatedAt() : now);
        entity.setUpdatedAt(now);
        entity.setDeletedAt(domain.getDeletedAt());

        return entity;
    }

    /**
     * JPA Entity → Domain
     */
    public User toDomain(UserEntity entity) {
        // 개별 필드 → VO 재조합
        LoginId loginId = new LoginId(entity.getLoginId());
        UserName name = new UserName(entity.getName());
        BirthDate birthDate = new BirthDate(entity.getBirthDate().toString());
        Email email = new Email(entity.getEmail());

        return User.reconstitute(
                entity.getId(),
                loginId,
                entity.getPassword(),
                name,
                birthDate,
                email,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getDeletedAt()
        );
    }
}
