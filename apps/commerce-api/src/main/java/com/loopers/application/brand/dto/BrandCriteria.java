package com.loopers.application.brand.dto;

import com.loopers.domain.brand.dto.BrandCommand;

public class BrandCriteria {

    public record Register(String name) {
        public BrandCommand.Register toCommand() {
            return new BrandCommand.Register(name);
        }
    }

    public record Update(String name) {
        public BrandCommand.Update toCommand() {
            return new BrandCommand.Update(name);
        }
    }
}
