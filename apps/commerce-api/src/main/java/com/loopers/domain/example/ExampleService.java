package com.loopers.domain.example;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ExampleService {

    private final ExampleRepository exampleRepository;

    @Transactional(readOnly = true)
    public ExampleModel getExample(Long id) {
        return exampleRepository.find(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, ErrorMessage.Example.EXAMPLE_NOT_FOUND));
    }
}
