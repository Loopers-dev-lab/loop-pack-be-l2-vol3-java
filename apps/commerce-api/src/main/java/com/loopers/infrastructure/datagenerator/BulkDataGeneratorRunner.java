package com.loopers.infrastructure.datagenerator;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.data-generator.enabled", havingValue = "true")
public class BulkDataGeneratorRunner implements ApplicationRunner {

    private final BulkDataGeneratorService bulkDataGeneratorService;

    @Override
    public void run(ApplicationArguments args) {
        bulkDataGeneratorService.generateAll();
    }
}
