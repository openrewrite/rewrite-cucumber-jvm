plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Cucumber JVM Migration"

val rewriteVersion = "latest.release"
dependencies {
    implementation("io.cucumber:cucumber-java:7.18.0")
    implementation("io.cucumber:cucumber-java8:7.18.0")
    implementation("io.cucumber:cucumber-plugin:7.18.0")
    implementation("io.cucumber:cucumber-junit-platform-engine:7.18.0")
    implementation("org.junit.platform:junit-platform-suite-api:1.9.3")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:8.41.1"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")

    implementation("org.openrewrite.recipe:rewrite-java-dependencies:1.24.1")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:1.21.1")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
}
