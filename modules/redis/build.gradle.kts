plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-data-redis")

    testFixturesImplementation("com.redis:testcontainers-redis")
    testFixturesImplementation("org.testcontainers:junit-jupiter")
    testFixturesImplementation("org.springframework:spring-test")
}
