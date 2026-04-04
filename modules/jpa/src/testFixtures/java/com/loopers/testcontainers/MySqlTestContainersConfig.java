package com.loopers.testcontainers;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public final class MySqlTestContainersConfig {

    @Container
    @SuppressWarnings("resource")
    public static final MySQLContainer<?> MY_SQL_CONTAINER = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("loopers")
            .withUsername("test")
            .withPassword("test")
            .withExposedPorts(3306)
            .withCommand(
                    "--character-set-server=utf8mb4",
                    "--collation-server=utf8mb4_general_ci",
                    "--skip-character-set-client-handshake"
            );

    static {
        MY_SQL_CONTAINER.start();
    }

    @DynamicPropertySource
    public static void overrideDatasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("datasource.mysql-jpa.main.jdbc-url", MY_SQL_CONTAINER::getJdbcUrl);
        registry.add("datasource.mysql-jpa.main.username", MY_SQL_CONTAINER::getUsername);
        registry.add("datasource.mysql-jpa.main.password", MY_SQL_CONTAINER::getPassword);
    }

    private MySqlTestContainersConfig() {
    }
}
