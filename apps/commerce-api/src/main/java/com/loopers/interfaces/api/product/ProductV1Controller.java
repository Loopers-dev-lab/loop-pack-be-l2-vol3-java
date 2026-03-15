package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.dto.FindProductListReqDto;
import com.loopers.application.product.dto.FindProductListResDto;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.product.dto.FindProductApiResDto;
import com.loopers.interfaces.api.product.dto.FindProductListApiResDto;
import com.loopers.support.enums.SortFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/products")
public class ProductV1Controller implements ProductV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final ProductFacade productFacade;

    @GetMapping
    @Override
    public ApiResponse<Page<FindProductListApiResDto>> findProductList(@RequestHeader(value = HEADER_LOGIN_ID, required = false) String loginId,
                                                                       @RequestHeader(value = HEADER_LOGIN_PW, required = false) String password,
                                                                       @RequestParam(required = false) Long brandId,
                                                                       @RequestParam SortFilter sortFilter, Pageable pageable) {
        FindProductListReqDto req = new FindProductListReqDto(loginId, password, brandId, sortFilter);
        Page<FindProductListResDto> productList = productFacade.findProductList(req, pageable);
        return ApiResponse.success(productList.map(FindProductListApiResDto::from));
    }

    @GetMapping("/{productId}")
    @Override
    public ApiResponse<FindProductApiResDto> findProduct(@RequestHeader(value = HEADER_LOGIN_ID, required = false) String loginId,
                                                         @RequestHeader(value = HEADER_LOGIN_PW, required = false) String password,
                                                         @PathVariable Long productId) {
        return ApiResponse.success(FindProductApiResDto.from(productFacade.findProduct(loginId, password, productId)));
    }
}
