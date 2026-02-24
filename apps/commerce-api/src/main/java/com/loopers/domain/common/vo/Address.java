package com.loopers.domain.common.vo;

import java.util.Objects;

/**
 * Address Value Object (순수 POJO)
 * JPA 어노테이션 없음
 */
public class Address {

    private String zipCode;
    private String addressLine1;
    private String addressLine2;

    protected Address() {}

    public Address(String zipCode, String addressLine1, String addressLine2) {
        if (zipCode == null || zipCode.isBlank()) {
            throw new IllegalArgumentException("우편번호는 필수입니다.");
        }
        if (addressLine1 == null || addressLine1.isBlank()) {
            throw new IllegalArgumentException("기본 주소는 필수입니다.");
        }
        this.zipCode = zipCode;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
    }

    public String getZipCode() {
        return this.zipCode;
    }

    public String getAddressLine1() {
        return this.addressLine1;
    }

    public String getAddressLine2() {
        return this.addressLine2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address = (Address) o;
        return Objects.equals(zipCode, address.zipCode)
                && Objects.equals(addressLine1, address.addressLine1)
                && Objects.equals(addressLine2, address.addressLine2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(zipCode, addressLine1, addressLine2);
    }

    @Override
    public String toString() {
        return "Address{zipCode='" + zipCode + "', addressLine1='" + addressLine1 + "', addressLine2='" + addressLine2 + "'}";
    }
}
