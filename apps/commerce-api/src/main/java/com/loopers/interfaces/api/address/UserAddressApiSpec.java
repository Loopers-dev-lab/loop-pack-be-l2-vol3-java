package com.loopers.interfaces.api.address;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Address API", description = "배송지 관리 API")
public interface UserAddressApiSpec {

    @Operation(summary = "배송지 목록 조회", description = "내 배송지 목록을 조회합니다.")
    ApiResponse<UserAddressResponse.AddressListResponse> getAddresses(@AuthUser User user);

    @Operation(summary = "배송지 등록", description = "배송지를 등록합니다. 첫 번째 주소는 자동으로 기본주소로 설정됩니다.")
    ApiResponse<Object> registerAddress(@AuthUser User user, UserAddressRequest.RegisterAddressRequest request);

    @Operation(summary = "배송지 수정", description = "배송지 정보를 수정합니다.")
    ApiResponse<Object> updateAddress(@AuthUser User user, Long addressId, UserAddressRequest.UpdateAddressRequest request);

    @Operation(summary = "배송지 삭제", description = "배송지를 삭제합니다. 기본주소 삭제 시 다른 주소가 기본주소로 전환됩니다.")
    ApiResponse<Object> deleteAddress(@AuthUser User user, Long addressId);
}
