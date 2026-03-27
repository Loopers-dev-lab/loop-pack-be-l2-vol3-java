package com.loopers.infrastructure.payment;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("local")
public class PgSimulatorProcessManager {

    @Value("${pg-simulator.jar-path:docker/pg-simulator/pg-simulator.jar}")
    private String jarPath;

    @Value("${pg-simulator.java-home:}")
    private String javaHome;

    private Process process;

    @PostConstruct
    void start() {
        File jar = resolveJar();
        if (jar == null) {
            return;
        }

        try {
            String java = resolveJavaPath();
            ProcessBuilder pb = new ProcessBuilder(
                    java, "-Xms256m", "-Xmx512m",
                    "-jar", jar.getAbsolutePath(),
                    "--spring.profiles.active=local",
                    "--datasource.mysql-jpa.main.jdbc-url=jdbc:mysql://localhost:3306/paymentgateway")
                    .redirectErrorStream(true)
                    .inheritIO();

            process = pb.start();
            log.info("PG 시뮬레이터 시작됨 (PID: {}, jar: {})", process.pid(), jar.getAbsolutePath());
        } catch (IOException e) {
            log.error("PG 시뮬레이터 시작 실패", e);
        }
    }

    @PreDestroy
    void stop() {
        if (process != null && process.isAlive()) {
            log.info("PG 시뮬레이터 종료 중 (PID: {})", process.pid());
            process.destroy();
        }
    }

    private File resolveJar() {
        File jar = new File(jarPath);
        if (jar.exists()) {
            return jar;
        }
        // bootRun은 apps/commerce-api/ 에서 실행될 수 있으므로 상위 탐색
        File current = new File(System.getProperty("user.dir"));
        for (int i = 0; i < 3; i++) {
            File candidate = new File(current, jarPath);
            if (candidate.exists()) {
                return candidate;
            }
            current = current.getParentFile();
            if (current == null) break;
        }
        log.warn("PG 시뮬레이터 jar 없음: {}. PG 시뮬레이터가 실행되지 않습니다. (cwd: {})",
                jarPath, System.getProperty("user.dir"));
        return null;
    }

    private String resolveJavaPath() {
        if (javaHome != null && !javaHome.isBlank()) {
            return javaHome + "/bin/java";
        }
        return System.getProperty("java.home") + "/bin/java";
    }
}
