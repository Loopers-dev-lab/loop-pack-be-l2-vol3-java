package com.loopers.interfaces.api.like;

import com.loopers.application.like.LikeUseCase;
import com.loopers.application.like.UnlikeUseCase;
import com.loopers.domain.model.user.UserId;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class LikeController {

    private final LikeUseCase likeUseCase;
    private final UnlikeUseCase unlikeUseCase;

    public LikeController(LikeUseCase likeUseCase, UnlikeUseCase unlikeUseCase) {
        this.likeUseCase = likeUseCase;
        this.unlikeUseCase = unlikeUseCase;
    }

    @PostMapping("/{productId}/likes")
    public ResponseEntity<Void> like(HttpServletRequest request, @PathVariable Long productId) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        likeUseCase.like(userId, productId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{productId}/likes")
    public ResponseEntity<Void> unlike(HttpServletRequest request, @PathVariable Long productId) {
        UserId userId = (UserId) request.getAttribute("authenticatedUserId");
        unlikeUseCase.unlike(userId, productId);
        return ResponseEntity.ok().build();
    }
}
