<p align="center">
  <a href="https://docs.openrewrite.org">
    <picture>
      <source media="(prefers-color-scheme: dark)" srcset="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-dark.svg">
      <source media="(prefers-color-scheme: light)" srcset="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-light.svg">
      <img alt="OpenRewrite Logo" src="https://github.com/openrewrite/rewrite/raw/main/doc/logo-oss-light.svg" width='600px'>
    </picture>
  </a>
</p>

<div align="center">
  <h1>rewrite-cucumber-jvm</h1>
</div>

<div align="center">

<!-- Keep the gap above this line, otherwise they won't render correctly! -->
[![ci](https://github.com/openrewrite/rewrite-cucumber-jvm/actions/workflows/ci.yml/badge.svg)](https://github.com/openrewrite/rewrite-cucumber-jvm/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/org.openrewrite.recipe/rewrite-cucumber-jvm.svg)](https://mvnrepository.com/artifact/org.openrewrite.recipe/rewrite-cucumber-jvm)
[![Revved up by Develocity](https://img.shields.io/badge/Revved%20up%20by-Develocity-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.openrewrite.org/scans)
[![Contributing Guide](https://img.shields.io/badge/Contributing-Guide-informational)](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md)
</div>

### What is this?

This project implements a [Rewrite module](https://github.com/openrewrite/rewrite) that performs common tasks when migrating to new version of [Cucumber-JVM](https://github.com/cucumber/cucumber-jvm).

Browse [a selection of recipes available through this module in the recipe catalog](https://docs.openrewrite.org/recipes/cucumber/jvm).

## Running migration recipes
Migration recipes can be run using the [rewrite-maven-plugin](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
or [rewrite-gradle-plugin](https://docs.openrewrite.org/reference/gradle-plugin-configuration).
These can either be added to the build file of the project to be migrated or [run without modifying the build](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-maven-project-without-modifying-the-build).


### Upgrade to Cucumber JVM 7.x
```shell
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-cucumber-jvm:RELEASE \
  -DactiveRecipes=org.openrewrite.cucumber.jvm.UpgradeCucumber7x
```

### Cucumber-Java8 migration to Cucumber-Java
```shell
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=org.openrewrite.recipe:rewrite-cucumber-jvm:RELEASE \
  -DactiveRecipes=org.openrewrite.cucumber.jvm.CucumberJava8ToJava
```

## Bugs and Feature requests

You can register bugs and feature requests in the
[GitHub Issue Tracker](https://github.com/openrewrite/rewrite-cucumber-jvm/issues).

## Contributing

If you'd like to contribute to the documentation, checkout
[CONTRIBUTING.md](https://github.com/openrewrite/.github/blob/main/CONTRIBUTING.md).
