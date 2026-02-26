package com.loopers.application.brand;

public record BrandCommand() {

    public record Create(String name, String description) {
        public static Create of(String name, String description) {
            return new Create(name, description);
        }
    }

    public record Update(String name, String description) {
        public static Update of(String name, String description) {
            return new Update(name, description);
        }
    }
}
