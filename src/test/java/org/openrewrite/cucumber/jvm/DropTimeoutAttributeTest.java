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
class DropTimeoutAttributeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.DropTimeoutAttribute")
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-java-4"));
    }

    @DocumentExample
    @Test
    void dropTimeoutFromStepDefinition() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java.en.Given;

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
    void dropTimeoutFromHooks() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java.After;
            import io.cucumber.java.Before;

            class StepDefinitions {
                @Before(timeout = 1000, order = 10)
                void before() {
                }

                @After(timeout = 1000)
                void after() {
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.java.After;
            import io.cucumber.java.Before;

            class StepDefinitions {
                @Before(order = 10)
                void before() {
                }

                @After
                void after() {
                }
            }
            """));
    }

    @Test
    void dropTimeoutFromNonEnglishStepDefinition() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.java.nl.Gegeven;

            class StepDefinitions {
                @Gegeven(value = "een stap", timeout = 1000)
                void eenStap() {
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.java.nl.Gegeven;

            class StepDefinitions {
                @Gegeven("een stap")
                void eenStap() {
                }
            }
            """));
    }

    @Test
    void dropTimeoutBeforeThePackageRename() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import cucumber.api.java.en.When;

            class StepDefinitions {
                @When(value = "a step", timeout = 1000)
                void aStep() {
                }
            }
            """,
          """
            package com.example.app;

            import cucumber.api.java.en.When;

            class StepDefinitions {
                @When("a step")
                void aStep() {
                }
            }
            """));
    }

    @Test
    void retainStepDefinitionsWithoutTimeout() {
        // language=java
        rewriteRun(java(
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
}
