plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Cucumber JVM Migration"

recipeDependencies {
    // The types the recipes generate code against, shipped with the recipe artifact; the major version is
    // pinned, as the recipes resolve these type tables by their `<artifactId>-<major>` resource name
    parserClasspath("io.cucumber:cucumber-java:7.+")
    parserClasspath("io.cucumber:cucumber-java8:7.+")
    // JUnit Platform 6.x requires Java 17; recipe modules still compile against Java 8
    parserClasspath("org.junit.platform:junit-platform-suite-api:1.+")

    // The types the tests parse against
    testParserClasspath("io.cucumber:cucumber-expressions:latest.release")
    testParserClasspath("io.cucumber:cucumber-junit:7.+")
    testParserClasspath("io.cucumber:cucumber-junit-platform-engine:latest.release")
    testParserClasspath("io.cucumber:cucumber-plugin:latest.release")
    testParserClasspath("io.cucumber:cucumber-testng:7.+")
    testParserClasspath("io.cucumber:datatable:latest.release")
    testParserClasspath("io.cucumber:docstring:latest.release")
    testParserClasspath("org.junit.jupiter:junit-jupiter-api:latest.release")

    // The `cucumber.api` types the upgrade recipes migrate away from, gone from every supported release
    testParserClasspath("io.cucumber:cucumber-core:4.8.1")
    testParserClasspath("io.cucumber:cucumber-java:4.8.1")
    testParserClasspath("io.cucumber:cucumber-junit:4.8.1")
    testParserClasspath("io.cucumber:cucumber-testng:4.8.1")
    // Cucumber-JVM 2.x and earlier, for the `cucumber.api` types that were already gone by 4.x
    testParserClasspath("info.cukes:cucumber-core:1.2.5")
    // The last release with `io.cucumber.core.api.TypeRegistryConfigurer`, removed in 7.0.0
    testParserClasspath("io.cucumber:cucumber-core:6.11.0")
    // The last release with `Scenario.write`/`embed` and `@CucumberOptions(tags = {...})`, both gone in 6.0.0
    testParserClasspath("io.cucumber:cucumber-java:5.7.0")
    testParserClasspath("io.cucumber:cucumber-java8:5.7.0")
    testParserClasspath("io.cucumber:cucumber-junit:5.7.0")
    testParserClasspath("io.cucumber:cucumber-testng:5.7.0")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
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
