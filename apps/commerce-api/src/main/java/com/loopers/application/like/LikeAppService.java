package com.loopers.application.like;

import com.loopers.domain.like.LikeService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 좋아요 도메인 Application Service.
 * 사용자 인증과 좋아요 도메인 서비스를 조합하고 Model → Info 변환을 담당한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LikeAppService {

    private final UserService userService;
    private final LikeService likeService;

    /**
     * 상품에 좋아요를 등록한다. 이미 좋아요한 경우 무시한다 (멱등).
     *
     * @param loginId   로그인 ID
     * @param loginPw   비밀번호
     * @param productId 상품 ID
     */
    @Transactional
    public void addLike(String loginId, String loginPw, String productId) {
        UserModel user = userService.authenticate(loginId, loginPw);
        likeService.addLike(user.getUserId(), productId);
    }

    /**
     * 상품 좋아요를 취소한다. 좋아요가 없으면 무시한다 (멱등).
     *
     * @param loginId   로그인 ID
     * @param loginPw   비밀번호
     * @param productId 상품 ID
     */
    @Transactional
    public void removeLike(String loginId, String loginPw, String productId) {
        UserModel user = userService.authenticate(loginId, loginPw);
        likeService.removeLike(user.getUserId(), productId);
    }

    /**
     * 특정 사용자의 좋아요 목록을 조회한다.
     *
     * @param loginId 로그인 ID
     * @param loginPw 비밀번호
     * @return 좋아요 정보 DTO 목록
     */
    public List<LikeInfo> getMyLikes(String loginId, String loginPw) {
        UserModel user = userService.authenticate(loginId, loginPw);
        return likeService.getMyLikes(user.getUserId()).stream()
                .map(LikeInfo::from)
                .toList();
    }
}
