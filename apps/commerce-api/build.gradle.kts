dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":modules:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // web
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // security (PasswordEncoder 사용)
    implementation("org.springframework.security:spring-security-crypto")

    // resilience4j (PG 결제 연동 보호: CircuitBreaker, Retry)
    implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Apache HttpClient 5 (RestClient의 HTTP 엔진 — 커넥션 풀, 세밀한 타임아웃 지원)
    implementation("org.apache.httpcomponents.client5:httpclient5")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:${project.properties["springDocOpenApiVersion"]}")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
    testImplementation(testFixtures(project(":modules:kafka")))

    // awaitility (비동기 이벤트 처리 완료 대기 — @Async + @TransactionalEventListener 테스트)
    testImplementation("org.awaitility:awaitility:4.2.2")

    // wiremock (PG 시뮬레이터 대역: 실패율, 지연, 타임아웃 등 장애 시나리오 재현)
    // standalone: Jetty HTTP 서버를 내장하여 별도 의존성 없이 동작 (classpath shadowing으로 충돌 방지)
    testImplementation("org.wiremock:wiremock-standalone:3.9.2")
}
