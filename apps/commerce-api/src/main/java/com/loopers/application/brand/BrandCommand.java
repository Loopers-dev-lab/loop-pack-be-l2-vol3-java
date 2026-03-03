package com.loopers.application.brand;

public record BrandCommand() {

    public record Create(String name, String description) {
        public static Create of(String name, String description) {
            return new Create(name, description);
        }
    }

    public record UpdateInfo(String name, String description) {
        public static UpdateInfo of(String name, String description) {
            return new UpdateInfo(name, description);
        }
    }
}
