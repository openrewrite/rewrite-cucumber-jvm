/*
 * Copyright 2024 the original author or authors.
 * <p>
 * Licensed under the Moderne Source Available License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://docs.moderne.io/licensing/moderne-source-available-license
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.cucumber.jvm;

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class UpgradeCucumber6xTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.UpgradeCucumber6x");
    }

    @DocumentExample
    @Test
    void dropTimeoutAlongsideThePackageRename() {
        // language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-java-4")),
          java(
            """
              package com.example.app;

              import cucumber.api.java.en.Given;

              class StepDefinitions {
                  @Given(value = "a step", timeout = 1000)
                  void aStep() {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.en.Given;

              class StepDefinitions {
                  @Given("a step")
                  void aStep() {
                  }
              }
              """));
    }

    @Test
    void collapseTagsAndMigrateScenario() {
        // language=java
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-java-5", "cucumber-junit-5")),
          java(
            """
              package com.example.app;

              import io.cucumber.java.Scenario;
              import io.cucumber.java.en.Given;
              import io.cucumber.junit.CucumberOptions;

              @CucumberOptions(tags = {"@smoke", "not @wip"})
              public class RunCucumberTest {
                  @Given("a step")
                  void aStep(Scenario scenario) {
                      scenario.write("a message");
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.Scenario;
              import io.cucumber.java.en.Given;
              import io.cucumber.junit.CucumberOptions;

              @CucumberOptions(tags = "(@smoke) and (not @wip)")
              public class RunCucumberTest {
                  @Given("a step")
                  void aStep(Scenario scenario) {
                      scenario.log("a message");
                  }
              }
              """));
    }

    @Test
    void splitTheCucumberOptionsProperty() {
        rewriteRun(
          properties(
            """
              cucumber.options=--glue com.example.app --tags @smoke --tags "not @wip"
              """,
            """
              cucumber.filter.tags=(@smoke) and (not @wip)
              cucumber.glue=com.example.app
              """,
            spec -> spec.path("src/test/resources/cucumber.properties")));
    }
}
