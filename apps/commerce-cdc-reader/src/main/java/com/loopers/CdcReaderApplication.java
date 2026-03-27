package com.loopers;

import com.loopers.cdc.CdcReaderProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.TimeZone;

@EnableConfigurationProperties(CdcReaderProperties.class)
@SpringBootApplication
public class CdcReaderApplication {

    @PostConstruct
    public void started() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CdcReaderApplication.class, args);
    }
}
