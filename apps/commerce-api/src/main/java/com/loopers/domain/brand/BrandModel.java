package com.loopers.domain.brand;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.DisplayStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 브랜드 JPA 엔티티.
 * 브랜드명, 설명, 주소, 노출 상태를 관리하며 소프트 삭제를 지원한다.
 * {@link BaseStringIdEntity}를 상속하여 UUID PK와 del_yn/deletedAt 이중 삭제 관리를 사용한다.
 */
@Entity
@Table(name = "brands")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BrandModel extends BaseStringIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "brand_id")
    private Long brandId;

    @Column(name = "brand_name", nullable = false)
    private String brandName;

    @Column
    private String description;

    @Column
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_status", nullable = false, length = 20)
    private DisplayStatus displayStatus;

    @Column(name = "attach_file")
    private String attachFile;

    private BrandModel(String brandName, String description, String address) {
        validateBrandName(brandName);
        this.brandName = brandName;
        this.description = description;
        this.address = address;
        this.displayStatus = DisplayStatus.ACTIVE;
    }

    /**
     * 브랜드 엔티티를 생성한다. displayStatus는 ACTIVE, delYn은 "N"으로 초기화된다.
     *
     * @param brandName   브랜드명 (필수)
     * @param description 브랜드 설명
     * @param address     브랜드 주소
     * @return 생성된 BrandModel 인스턴스
     * @throws CoreException brandName이 null 또는 blank인 경우 (BAD_REQUEST)
     */
    public static BrandModel create(String brandName, String description, String address) {
        return new BrandModel(brandName, description, address);
    }

    /**
     * 브랜드를 고객에게 비노출 상태(HIDDEN)로 전환한다.
     */
    public void hide() {
        this.displayStatus = DisplayStatus.HIDDEN;
    }

    /**
     * 브랜드를 고객에게 노출 상태(ACTIVE)로 전환한다.
     */
    public void activate() {
        this.displayStatus = DisplayStatus.ACTIVE;
    }

    /**
     * 브랜드 정보(이름, 설명, 주소)를 수정한다.
     *
     * @param brandName   새 브랜드명 (필수)
     * @param description 새 설명
     * @param address     새 주소
     * @throws CoreException brandName이 null 또는 blank인 경우 (BAD_REQUEST)
     */
    public void updateInfo(String brandName, String description, String address) {
        validateBrandName(brandName);
        this.brandName = brandName;
        this.description = description;
        this.address = address;
    }

    /**
     * 고객 노출 여부를 판별한다. displayStatus가 ACTIVE이고 삭제되지 않은 경우에만 true를 반환한다.
     *
     * @return 고객에게 노출 가능하면 true
     */
    public boolean isVisibleForCustomer() {
        return displayStatus == DisplayStatus.ACTIVE && !isDeleted();
    }

    /**
     * JPA @PrePersist/@PreUpdate 시 호출되는 유효성 검증 훅.
     */
    @Override
    protected void guard() {
        validateBrandName(this.brandName);
    }

    private static void validateBrandName(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드 이름은 필수입니다.");
        }
    }
}
