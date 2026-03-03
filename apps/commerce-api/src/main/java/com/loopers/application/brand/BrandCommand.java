package com.loopers.application.brand;

public record BrandCommand() {

    public record Register(String name, String description) {
        public static Register of(String name, String description) {
            return new Register(name, description);
        }
    }

    public record UpdateInfo(String name, String description) {
        public static UpdateInfo of(String name, String description) {
            return new UpdateInfo(name, description);
        }
    }
}
