package com.loopers.domain.brand;

import com.loopers.domain.common.Name;

/**
 *  브랜드 도메인 객체
 */
public class Brand {

    private final Long id;
    private Name name;
    private String description;

    private Brand(Long id, Name name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public static Brand create(Long id, String name, String description) {
        return new Brand(id, new Name(name), description);
    }

    public void update(String name, String description) {
        this.name = new Name(name);
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public Name getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
