dependencies {
    // add-ons
    implementation(project(":modules:jpa"))
    implementation(project(":modules:redis"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    // batch
    implementation("org.springframework.boot:spring-boot-starter-batch")
    testImplementation("org.springframework.batch:spring-batch-test")

    // querydsl
    annotationProcessor("com.querydsl:querydsl-apt::jakarta")
    annotationProcessor("jakarta.persistence:jakarta.persistence-api")
    annotationProcessor("jakarta.annotation:jakarta.annotation-api")

    // test-fixtures
    testImplementation(testFixtures(project(":modules:jpa")))
    testImplementation(testFixtures(project(":modules:redis")))
}

// 평소 ./gradlew test 에서는 @Tag("benchmark") 테스트를 제외한다.
// 측정용 실행은 -PrunBenchmark=true 또는 별도 task ":apps:commerce-batch:benchmarkTest" 사용.
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("benchmark")
    }
}

tasks.register<Test>("benchmarkTest") {
    description = "랭킹 배치 선형성/스파이크 측정 (오래 걸림)"
    group = "verification"
    useJUnitPlatform {
        includeTags("benchmark")
    }
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    // 측정 결과가 stdout 으로 흘러나오도록 standard output 강제 노출
    testLogging {
        showStandardStreams = true
    }
}
