package com.loopers.testcontainers;

import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

@Configuration
public class MockPgTestContainersConfig {

    private static final GenericContainer<?> mockToss;
    private static final GenericContainer<?> mockNice;

    static {
        Path projectRoot = Path.of(System.getProperty("project.root"));

        mockToss = createMockContainer(projectRoot, "mock-toss", 8090);
        mockNice = createMockContainer(projectRoot, "mock-nice", 8091);

        mockToss.start();
        mockNice.start();

        System.setProperty("payment.toss.base-url",
                "http://" + mockToss.getHost() + ":" + mockToss.getMappedPort(8090));
        System.setProperty("payment.nice.base-url",
                "http://" + mockNice.getHost() + ":" + mockNice.getMappedPort(8091));
    }

    private static GenericContainer<?> createMockContainer(Path projectRoot, String moduleName, int port) {
        Path mockDir = projectRoot.resolve("mocks/" + moduleName);

        return new GenericContainer<>(
                new ImageFromDockerfile(moduleName + "-test", false)
                        .withFileFromPath("Dockerfile", mockDir.resolve("Dockerfile"))
                        .withFileFromPath("app.jar", findBootJar(mockDir.resolve("build/libs"))))
                .withExposedPorts(port)
                .waitingFor(Wait.forHttp("/chaos/mode").forStatusCode(200));
    }

    private static Path findBootJar(Path libsDir) {
        try (var stream = Files.list(libsDir)) {
            return stream
                    .filter(p -> p.toString().endsWith(".jar"))
                    .filter(p -> !p.toString().contains("plain"))
                    .max(Comparator.comparingLong(p -> {
                        try { return Files.getLastModifiedTime(p).toMillis(); }
                        catch (IOException e) { return 0L; }
                    }))
                    .orElseThrow(() -> new RuntimeException("Boot jar not found in " + libsDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to find boot jar in " + libsDir, e);
        }
    }
}
