plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Cucumber JVM Migration"

recipeDependencies {
    // The types the recipes generate code against, shipped with the recipe artifact
    parserClasspath("io.cucumber:cucumber-java:latest.release")
    parserClasspath("io.cucumber:cucumber-java8:latest.release")
    // JUnit Platform 6.x requires Java 17; recipe modules still compile against Java 8
    parserClasspath("org.junit.platform:junit-platform-suite-api:1.+")

    // The types the tests parse against
    testParserClasspath("io.cucumber:cucumber-junit:latest.release")
    testParserClasspath("io.cucumber:cucumber-junit-platform-engine:latest.release")
    testParserClasspath("io.cucumber:cucumber-plugin:latest.release")
    testParserClasspath("io.cucumber:cucumber-testng:latest.release")
    testParserClasspath("org.junit.jupiter:junit-jupiter-api:latest.release")

    // Older releases, to parse the sources that the upgrade recipes take as input
    testParserClasspath("io.cucumber:cucumber-java:4.8.1") // @Given(timeout = ...), dropped in 5.0.0
    testParserClasspath("io.cucumber:cucumber-java:5.7.0") // Scenario.write/embed, dropped in 6.0.0
    testParserClasspath("io.cucumber:cucumber-java8:5.7.0") // Scenario.write/embed, dropped in 6.0.0
    testParserClasspath("io.cucumber:cucumber-junit:5.7.0") // @CucumberOptions(tags = {...}), collapsed in 6.0.0
    testParserClasspath("io.cucumber:cucumber-testng:5.7.0") // @CucumberOptions(tags = {...}), collapsed in 6.0.0
}

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

    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-test")
}
