dependencies {
    // add-ons
    implementation(project(":modules:kafka"))
    implementation(project(":supports:jackson"))
    implementation(project(":supports:logging"))
    implementation(project(":supports:monitoring"))

    implementation("com.github.shyiko:mysql-binlog-connector-java:0.21.0")
}
