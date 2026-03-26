plugins {
    `java-library`
}

dependencies {
    api(project(":modules:jpa"))
    api("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-autoconfigure")
    implementation("org.springframework:spring-tx")
}
