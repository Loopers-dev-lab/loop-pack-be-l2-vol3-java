package com.loopers.application.example;

import com.loopers.domain.example.ExampleModel;
import com.loopers.domain.example.ExampleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ExampleFacade {
    private final ExampleService exampleService;

    public ExampleInfo getExample(Long id) {
        ExampleModel example = exampleService.getExample(id);
        System.out.println("Test 입니다");
        System.out.println("Test 입니다");
        return ExampleInfo.from(example);
    }
}
