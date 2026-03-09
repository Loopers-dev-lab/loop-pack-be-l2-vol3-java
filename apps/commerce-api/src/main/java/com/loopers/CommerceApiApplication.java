package com.loopers;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.TimeZone;

/**
 * Commerce API 메인 애플리케이션 클래스.
 * REST API 서버를 기동하며, 타임존을 Asia/Seoul로 설정하고 스케줄링을 활성화한다.
 */
@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
public class CommerceApiApplication {

    /**
     * 애플리케이션 기동 후 타임존을 Asia/Seoul로 설정한다.
     */
    @PostConstruct
    public void started() {
        // set timezone
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

    /**
     * 애플리케이션 진입점.
     *
     * @param args 커맨드라인 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(CommerceApiApplication.class, args);
    }
}
