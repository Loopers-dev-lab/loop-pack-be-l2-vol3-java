package com.loopers.domain.brand;

import static org.mockito.BDDMockito.given;

import com.loopers.support.error.CoreException;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @InjectMocks
    private BrandService brandService;

    @Mock
    private BrandRepository brandRepository;

    @Test
    @DisplayName("브랜드 정보를 수정할 때 해당 브랜드가 존재하지않으면 예외를 던진다")
    void fail_modify_not_found() {
        Long id = 10L;
        String name = "나이키";
        String description = "나이키설명";

        given(brandRepository.findById(id)).willReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> brandService.update(id, name, description))
            .isInstanceOf(CoreException.class)
            .hasMessage("브랜드를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("id로 브랜드를 조회할 때 브랜드가 존재하지않으면 예외를 던진다")
    void fail_getById_not_found_brand() {
        Long id = 10L;

        given(brandRepository.findById(id)).willReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> brandService.getById(id))
            .isInstanceOf(CoreException.class)
            .hasMessage("브랜드를 찾을 수 없습니다");
    }
}
