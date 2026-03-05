package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandInfo;
import com.loopers.application.brand.BrandRegisterCommand;
import com.loopers.application.brand.BrandUpdateCommand;
import org.springframework.data.domain.Page;

import java.time.ZonedDateTime;
import java.util.List;

public class BrandAdminV1Dto {

    // 브랜드 등록 요청 객체
    public record RegisterRequest(
            String name
    ){
        public BrandRegisterCommand toCommand(){
            return new BrandRegisterCommand(name);
        }
    }

    // 브랜드 정보 수정 요청 객체
    public record UpdateRequest(
            String name
    ){
        public BrandUpdateCommand toCommand(long id){
            return new BrandUpdateCommand(id, name);
        }
    }

    public record BrandResponse(
            long id,
            String name,
            ZonedDateTime createdAt,
            ZonedDateTime updatedAt
    ){
        public static BrandResponse from(BrandInfo info){
            return new BrandResponse(
                    info.id(),
                    info.name(),
                    info.createdAt(),
                    info.updatedAt()
            );
        }
    }

    public record BrandListResponse(
            List<BrandResponse> brands,
            int page,
            int size,
            long totalElements,
            int totalPages
    ){
        public static BrandListResponse from(Page<BrandInfo> info){
            return new BrandListResponse(
                    info.getContent().stream().map(BrandResponse::from).toList(),
                    info.getNumber(),
                    info.getSize(),
                    info.getTotalElements(),
                    info.getTotalPages()
            );
        }
    }

}
