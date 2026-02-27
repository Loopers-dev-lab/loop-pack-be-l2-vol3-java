package com.loopers.interfaces.api.favorite;

import com.loopers.application.favorite.FavoriteFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members/me/favorites")
public class FavoriteV1Controller implements FavoriteV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final FavoriteFacade favoriteFacade;

    @PostMapping("/{productId}")
    @Override
    public ApiResponse<Void> addFavorite(@RequestHeader(HEADER_LOGIN_ID) String loginId, @RequestHeader(HEADER_LOGIN_PW) String password, @PathVariable Long productId) {
        favoriteFacade.addFavorite(loginId, password, productId);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{productId}")
    @Override
    public ApiResponse<Void> deleteFavorite(@RequestHeader(HEADER_LOGIN_ID) String loginId, @RequestHeader(HEADER_LOGIN_PW) String password, @PathVariable Long productId) {
        favoriteFacade.deleteFavorite(loginId, password, productId);
        return ApiResponse.success(null);
    }
}
