plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Cucumber JVM Migration"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation("io.cucumber:cucumber-java:latest.release")
    implementation("io.cucumber:cucumber-java8:latest.release")
    implementation("io.cucumber:cucumber-plugin:latest.release")
    implementation("io.cucumber:cucumber-junit-platform-engine:latest.release")
    // JUnit Platform 6.x requires Java 17; recipe modules still compile against Java 8
    implementation("org.junit.platform:junit-platform-suite-api:1.+")

    compileOnly("org.projectlombok:lombok:latest.release")
    annotationProcessor("org.projectlombok:lombok:latest.release")

    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite:rewrite-gradle")
    implementation("org.openrewrite:rewrite-maven")

    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")
    implementation("org.openrewrite.recipe:rewrite-static-analysis:$rewriteVersion")

    // Only needed to resolve `@CucumberOptions` in tests; TestNG 7.12 requires Java 11
    testImplementation("io.cucumber:cucumber-junit:latest.release")
    testImplementation("io.cucumber:cucumber-testng:latest.release")

    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-test")
}
