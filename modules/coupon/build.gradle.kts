plugins {
    `java-library`
}

dependencies {
    api(project(":modules:jpa"))
    implementation(project(":supports:error"))
}
