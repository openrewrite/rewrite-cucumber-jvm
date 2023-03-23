# Cucumber JVM migration

Cucumber-JVM migration contains [OpenRewrite](https://docs.openrewrite.org/) recipes for migrating applications using Cucumber-JVM.

## Running migration recipes
Migration recipes can be run using the [rewrite-maven-plugin](https://docs.openrewrite.org/reference/rewrite-maven-plugin)
or [rewrite-gradle-plugin](https://docs.openrewrite.org/reference/gradle-plugin-configuration).
These can either be added to the build file of the project to be migrated or [run without modifying the build](https://docs.openrewrite.org/running-recipes/running-rewrite-on-a-maven-project-without-modifying-the-build).


### Upgrade to Cucumber JVM 7.x
```shell
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.cucumber:cucumber-jvm-migration:LATEST \
  -DactiveRecipes=io.cucumber.migration.UpgradeCucumber7x
```

### Cucumber-Java8 migration to Cucumber-Java
```shell
mvn -U org.openrewrite.maven:rewrite-maven-plugin:run \
  -Drewrite.recipeArtifactCoordinates=io.cucumber:cucumber-jvm-migration:LATEST \
  -DactiveRecipes=io.cucumber.migration.CucumberJava8ToJava
```


## Questions, Problems, Help needed?

Please ask on

* [Stack Overflow](https://stackoverflow.com/questions/tagged/cucumber-jvm).
* [CucumberBDD Slack](https://cucumberbdd-slack-invite.herokuapp.com/) <sup>[direct link](https://cucumberbdd.slack.com/)</sup>

## Bugs and Feature requests

You can register bugs and feature requests in the
[GitHub Issue Tracker](https://github.com/cucumber/cucumber-jvm-migration/issues).

Please bear in mind that this project is almost entirely developed by
volunteers. If you do not provide the implementation yourself (or pay someone
to do it for you), the bug might never get fixed. If it is a serious bug, other
people than you might care enough to provide a fix.

## Contributing

If you'd like to contribute to the documentation, checkout
[cucumber/docs.cucumber.io](https://github.com/cucumber/docs.cucumber.io)
otherwise see our
[CONTRIBUTING.md](https://github.com/cucumber/cucumber-jvm/blob/main/CONTRIBUTING.md).
