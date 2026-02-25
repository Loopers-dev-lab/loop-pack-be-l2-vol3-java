package com.loopers.domain.address;

import com.loopers.domain.common.vo.Address;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;
import java.time.ZonedDateTime;

/**
 * UserAddress Aggregate Root (순수 POJO)
 * JPA 어노테이션 없음
 */
public class UserAddress {

    private Long id;
    private Long userId;
    private String receiverName;
    private String phone;
    private Address address;
    private boolean isDefault;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected UserAddress() {}

    private UserAddress(Long userId, String receiverName, String phone,
                        String zipCode, String addressLine1, String addressLine2) {
        this.userId = userId;
        this.receiverName = receiverName;
        this.phone = phone;
        this.address = new Address(zipCode, addressLine1, addressLine2);
        this.isDefault = false;
    }

    /**
     * 새로운 배송지 생성 (비즈니스 로직)
     */
    public static UserAddress create(Long userId, String receiverName, String phone,
                                      String zipCode, String addressLine1, String addressLine2) {
        return new UserAddress(userId, receiverName, phone, zipCode, addressLine1, addressLine2);
    }

    /**
     * 영속화된 데이터로부터 도메인 객체 재구성
     */
    public static UserAddress reconstitute(Long id, Long userId, String receiverName, String phone,
                                            Address address, boolean isDefault,
                                            ZonedDateTime createdAt, ZonedDateTime updatedAt, ZonedDateTime deletedAt) {
        UserAddress userAddress = new UserAddress();
        userAddress.id = id;
        userAddress.userId = userId;
        userAddress.receiverName = receiverName;
        userAddress.phone = phone;
        userAddress.address = address;
        userAddress.isDefault = isDefault;
        userAddress.createdAt = createdAt;
        userAddress.updatedAt = updatedAt;
        userAddress.deletedAt = deletedAt;
        return userAddress;
    }

    /**
     * 배송지 정보 부분 수정 (null이면 기존값 유지, 빈값이면 검증 에러)
     */
    public void update(String receiverName, String phone,
                       String zipCode, String addressLine1, String addressLine2) {
        if (receiverName != null) {
            if (receiverName.isBlank()) {
                throw new CoreException(UserAddressErrorType.INVALID_RECEIVER_NAME);
            }
            this.receiverName = receiverName;
        }
        if (phone != null) {
            if (phone.isBlank()) {
                throw new CoreException(UserAddressErrorType.INVALID_PHONE);
            }
            this.phone = phone;
        }
        if (zipCode != null || addressLine1 != null || addressLine2 != null) {
            String newZipCode = zipCode != null ? zipCode : this.address.getZipCode();
            String newLine1 = addressLine1 != null ? addressLine1 : this.address.getAddressLine1();
            String newLine2 = addressLine2 != null ? addressLine2 : this.address.getAddressLine2();
            if (newZipCode.isBlank() || newLine1.isBlank()) {
                throw new CoreException(UserAddressErrorType.INVALID_ADDRESS);
            }
            this.address = new Address(newZipCode, newLine1, newLine2);
        }
    }

    /**
     * 기본 배송지로 설정
     */
    public void setAsDefault() {
        this.isDefault = true;
    }

    /**
     * 기본 배송지 해제
     */
    public void unsetDefault() {
        this.isDefault = false;
    }

    /**
     * 삭제 (소프트 삭제)
     */
    public void delete() {
        if (this.deletedAt == null) {
            this.deletedAt = ZonedDateTime.now();
        }
    }

    /**
     * 복원
     */
    public void restore() {
        if (this.deletedAt != null) {
            this.deletedAt = null;
        }
    }

    /**
     * 소유권 검증
     */
    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(UserAddressErrorType.NOT_OWNER);
        }
    }

    public Long getId() {
        return this.id;
    }

    public Long getUserId() {
        return this.userId;
    }

    public String getReceiverName() {
        return this.receiverName;
    }

    public String getPhone() {
        return this.phone;
    }

    public Address getAddress() {
        return this.address;
    }

    public String getZipCode() {
        return this.address.getZipCode();
    }

    public String getAddressLine1() {
        return this.address.getAddressLine1();
    }

    public String getAddressLine2() {
        return this.address.getAddressLine2();
    }

    public boolean isDefault() {
        return this.isDefault;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
