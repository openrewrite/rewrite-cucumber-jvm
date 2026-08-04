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

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class MigrateScenarioWriteAndEmbedTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.MigrateScenarioWriteAndEmbed")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-java-5", "cucumber-java8-5"));
    }

    @DocumentExample
    @Test
    void writeToLogAndEmbedToAttach() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java.Scenario;
            import io.cucumber.java.en.Given;

            class StepDefinitions {
                @Given("a step")
                void aStep(Scenario scenario) {
                    scenario.write("a message");
                    scenario.embed(new byte[0], "image/png");
                    scenario.embed(new byte[0], "image/png", "a name");
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.java.Scenario;
            import io.cucumber.java.en.Given;

            class StepDefinitions {
                @Given("a step")
                void aStep(Scenario scenario) {
                    scenario.log("a message");
                    scenario.attach(new byte[0], "image/png", null);
                    scenario.attach(new byte[0], "image/png", "a name");
                }
            }
            """));
    }

    @Test
    void migrateCucumberJava8Scenario() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java8.Scenario;

            class StepDefinitions {
                void aStep(Scenario scenario) {
                    scenario.write("a message");
                    scenario.embed(new byte[0], "image/png");
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.java8.Scenario;

            class StepDefinitions {
                void aStep(Scenario scenario) {
                    scenario.log("a message");
                    scenario.attach(new byte[0], "image/png", null);
                }
            }
            """));
    }

    @Test
    void retainLogAndAttach() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java.Scenario;

            class StepDefinitions {
                void aStep(Scenario scenario) {
                    scenario.log("a message");
                    scenario.attach(new byte[0], "image/png", "a name");
                }
            }
            """));
    }
}
