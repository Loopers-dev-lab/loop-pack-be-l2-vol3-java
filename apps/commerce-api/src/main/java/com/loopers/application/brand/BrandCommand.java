package com.loopers.application.brand;

import com.loopers.domain.brand.ModifyBrand;
import com.loopers.domain.brand.NewBrand;

public class BrandCommand {

    public record CreateBrandCommand(String name, String logoUrl, String description) {

        public NewBrand toNewBrand() {
            return new NewBrand(name, logoUrl, description);
        }
    }

    public record UpdateBrandCommand(Long brandId, String name, String logoUrl, String description) {

        public ModifyBrand toModifyBrand() {
            return new ModifyBrand(brandId, name, logoUrl, description);
        }
    }
}
