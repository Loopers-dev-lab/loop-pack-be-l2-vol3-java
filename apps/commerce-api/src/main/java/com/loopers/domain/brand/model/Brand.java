package com.loopers.domain.brand.model;

import com.loopers.domain.brand.vo.BrandName;
import lombok.Getter;

@Getter
public class Brand {
    private Long id;
    private BrandName name;
    private String description;

    private Brand(BrandName name, String description) {
        this.name = name;
        this.description = description;
    }

    public static Brand create(BrandCommand.Create command) {
        return new Brand(
            new BrandName(command.name()),
            command.description()
        );
    }

    public static Brand reconstruct(Long id, String name, String description) {
        Brand brand = new Brand(new BrandName(name), description);
        brand.id = id;
        return brand;
    }

    public void update(BrandCommand.Update command) {
        this.name = new BrandName(command.name());
        this.description = command.description();
    }
}
