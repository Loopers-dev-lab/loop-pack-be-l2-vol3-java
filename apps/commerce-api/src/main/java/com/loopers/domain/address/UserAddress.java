package com.loopers.domain.address;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.vo.Address;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserAddressErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_addresses")
public class UserAddress extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "receiver_name", nullable = false)
    private String receiverName;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Embedded
    @AttributeOverride(name = "zipCode", column = @Column(name = "zip_code", nullable = false))
    @AttributeOverride(name = "addressLine1", column = @Column(name = "address_line1", nullable = false))
    @AttributeOverride(name = "addressLine2", column = @Column(name = "address_line2"))
    private Address address;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected UserAddress() {}

    private UserAddress(Long userId, String receiverName, String phone,
                        String zipCode, String addressLine1, String addressLine2) {
        this.userId = userId;
        this.receiverName = receiverName;
        this.phone = phone;
        this.address = new Address(zipCode, addressLine1, addressLine2);
        this.isDefault = false;
    }

    public static UserAddress create(Long userId, String receiverName, String phone,
                                      String zipCode, String addressLine1, String addressLine2) {
        return new UserAddress(userId, receiverName, phone, zipCode, addressLine1, addressLine2);
    }

    public void update(String receiverName, String phone,
                       String zipCode, String addressLine1, String addressLine2) {
        this.receiverName = receiverName;
        this.phone = phone;
        this.address = new Address(zipCode, addressLine1, addressLine2);
    }

    public void setAsDefault() {
        this.isDefault = true;
    }

    public void unsetDefault() {
        this.isDefault = false;
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(UserAddressErrorType.NOT_OWNER);
        }
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
}
