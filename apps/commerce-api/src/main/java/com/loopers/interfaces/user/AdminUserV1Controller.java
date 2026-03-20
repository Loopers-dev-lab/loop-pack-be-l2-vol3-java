package com.loopers.interfaces.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.user.dto.AdminUserV1Dto;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/users")
public class AdminUserV1Controller {

    private final UserService userService;

    @GetMapping
    public ApiResponse<AdminUserV1Dto.ListResponse> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        Page<UserModel> userPage = userService.getUsers(PageRequest.of(page, size));
        return ApiResponse.success(
                new AdminUserV1Dto.ListResponse(
                        userPage.getNumber(),
                        userPage.getSize(),
                        userPage.getTotalElements(),
                        userPage.getTotalPages(),
                        userPage.getContent().stream()
                                .map(AdminUserV1Dto.ListResponse.ListItem::from)
                                .toList()));
    }

    @PostMapping("/{userId}/point")
    public ApiResponse<AdminUserV1Dto.AddPointResponse> addPoint(
        @PathVariable Long userId,
        @Valid @RequestBody AdminUserV1Dto.AddPointRequest request
    ) {
        userService.addPoint(userId, request.amount());
        return ApiResponse.success(
                new AdminUserV1Dto.AddPointResponse(1, request.amount(),
                        "유저 " + userId + "에게 " + request.amount() + " 포인트를 지급했습니다."));
    }

    @PostMapping("/point")
    public ApiResponse<AdminUserV1Dto.AddPointResponse> addPointToAll(
        @Valid @RequestBody AdminUserV1Dto.AddPointAllRequest request
    ) {
        List<UserModel> users = userService.getAllUsers();
        for (UserModel user : users) {
            userService.addPoint(user.getId(), request.amount());
        }
        return ApiResponse.success(
                new AdminUserV1Dto.AddPointResponse(users.size(), request.amount(),
                        "전체 " + users.size() + "명에게 " + request.amount() + " 포인트를 지급했습니다."));
    }
}
