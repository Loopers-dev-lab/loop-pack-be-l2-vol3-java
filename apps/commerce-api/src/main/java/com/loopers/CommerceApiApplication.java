package com.loopers;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import java.util.TimeZone;

@ConfigurationPropertiesScan
@SpringBootApplication
public class CommerceApiApplication {

    @PostConstruct
    public void started() {
        // JVM 기본 타임존을 Asia/Seoul로 설정하여 DB 저장, 로그 출력 시 시간대 일관성을 보장한다.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    public static void main(String[] args) {
        SpringApplication.run(CommerceApiApplication.class, args);
    }
}
