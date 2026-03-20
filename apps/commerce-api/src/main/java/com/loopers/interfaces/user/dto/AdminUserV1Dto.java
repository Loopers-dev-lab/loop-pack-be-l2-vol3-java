package com.loopers.interfaces.user.dto;

import com.loopers.domain.user.UserModel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.ZonedDateTime;
import java.util.List;

public class AdminUserV1Dto {

    public record ListResponse(
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<ListItem> items
    ) {
        public record ListItem(
            Long id,
            String loginId,
            String name,
            String email,
            long point,
            ZonedDateTime createdAt
        ) {
            public static ListItem from(UserModel model) {
                return new ListItem(
                        model.getId(),
                        model.getLoginId(),
                        model.getName(),
                        model.getEmail(),
                        model.getPoint(),
                        model.getCreatedAt());
            }
        }
    }

    public record AddPointRequest(
        @NotNull(message = "금액은 필수입니다.")
        @Min(value = 1, message = "금액은 1 이상이어야 합니다.")
        Long amount
    ) {}

    public record AddPointAllRequest(
        @NotNull(message = "금액은 필수입니다.")
        @Min(value = 1, message = "금액은 1 이상이어야 합니다.")
        Long amount
    ) {}

    public record AddPointResponse(
        int updatedCount,
        long amountPerUser,
        String message
    ) {}
}
