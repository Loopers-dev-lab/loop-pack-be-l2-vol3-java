package com.loopers.interfaces.api.user;

import com.loopers.application.like.LikeQueryUseCase;
import com.loopers.application.user.PasswordUpdateUseCase;
import com.loopers.application.user.RegisterUseCase;
import com.loopers.application.user.UserQueryUseCase;
import com.loopers.domain.model.user.UserId;
import com.loopers.interfaces.api.like.dto.LikeResponse;
import com.loopers.interfaces.api.user.dto.PasswordUpdateRequest;
import com.loopers.interfaces.api.user.dto.UserInfoResponse;
import com.loopers.interfaces.api.user.dto.UserRegisterRequest;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final RegisterUseCase registerUseCase;
    private final UserQueryUseCase userQueryUseCase;
    private final PasswordUpdateUseCase passwordUpdateUseCase;
    private final LikeQueryUseCase likeQueryUseCase;

    public UserController(
            RegisterUseCase registerUseCase,
            UserQueryUseCase userQueryUseCase,
            PasswordUpdateUseCase passwordUpdateUseCase,
            LikeQueryUseCase likeQueryUseCase
    ) {
        this.registerUseCase = registerUseCase;
        this.userQueryUseCase = userQueryUseCase;
        this.passwordUpdateUseCase = passwordUpdateUseCase;
        this.likeQueryUseCase = likeQueryUseCase;
    }

    @PostMapping
    public ResponseEntity<Void> register(@RequestBody UserRegisterRequest request) {
        registerUseCase.register(request.toCommand());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMyInfo(HttpServletRequest request) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");

        var userInfo = userQueryUseCase.getUserInfo(userId);
        return ResponseEntity.ok(UserInfoResponse.from(userInfo));
    }

    @GetMapping("/{userId}/likes")
    public ResponseEntity<List<LikeResponse>> getMyLikes(
            @PathVariable String userId,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Boolean saleYn,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        UserId authenticatedUserId = (UserId) request.getAttribute("authenticatedUserId");
        if (!authenticatedUserId.getValue().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        List<LikeResponse> likes = likeQueryUseCase.getMyLikes(authenticatedUserId, sort, saleYn, status).stream()
                .map(LikeResponse::from)
                .toList();
        return ResponseEntity.ok(likes);
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            HttpServletRequest request,
            @RequestBody PasswordUpdateRequest passwordUpdateRequest
    ) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");

        passwordUpdateUseCase.updatePassword(
                userId,
                passwordUpdateRequest.currentPassword(),
                passwordUpdateRequest.newPassword()
        );
        return ResponseEntity.ok().build();
    }
}
