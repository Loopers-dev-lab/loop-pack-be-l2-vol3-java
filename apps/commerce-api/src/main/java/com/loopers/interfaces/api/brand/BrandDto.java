package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;

import java.util.List;

public class BrandDto {

    public record Response(Long id, String name) {
        public static Response from(Brand brand) {
            return new Response(brand.getId(), brand.getName());
        }
    }

    public record ListResponse(List<Response> brands) {
        public static ListResponse from(List<Brand> brands) {
            return new ListResponse(
                    brands.stream().map(Response::from).toList()
            );
        }
    }
}
