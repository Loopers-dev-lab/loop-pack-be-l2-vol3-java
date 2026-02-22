package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.Brand;

import java.util.List;

public class AdminBrandDto {

    public record CreateRequest(String name) {}

    public record UpdateRequest(String name) {}

    public record Response(Long id, String name, boolean deleted) {
        public static Response from(Brand brand) {
            return new Response(brand.getId(), brand.getName(), brand.isDeleted());
        }
    }

    public record ListResponse(List<Response> brands) {
        public static ListResponse from(List<Brand> brands) {
            return new ListResponse(
                    brands.stream()
                            .map(Response::from)
                            .toList()
            );
        }
    }
}
