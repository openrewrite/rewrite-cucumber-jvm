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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class DropStrictOptionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.DropStrictOption")
                .parser(JavaParser.fromJavaVersion().classpath("cucumber-junit", "cucumber-testng"));
    }

    @DocumentExample
    @Test
    void dropStrictFromJUnitCucumberOptions() {
        // language=java
        rewriteRun(java(
                """
                package com.example.app;

                import io.cucumber.junit.CucumberOptions;

                @CucumberOptions(strict = true, features = "src/test/resources/features")
                public class RunCucumberTest {
                }
                """,
                """
                package com.example.app;

                import io.cucumber.junit.CucumberOptions;

                @CucumberOptions(features = "src/test/resources/features")
                public class RunCucumberTest {
                }
                """));
    }

    @Test
    void dropStrictFromTestNgCucumberOptions() {
        // language=java
        rewriteRun(java(
                """
                package com.example.app;

                import io.cucumber.testng.CucumberOptions;

                @CucumberOptions(features = "src/test/resources/features", strict = false)
                public class RunCucumberTest {
                }
                """,
                """
                package com.example.app;

                import io.cucumber.testng.CucumberOptions;

                @CucumberOptions(features = "src/test/resources/features")
                public class RunCucumberTest {
                }
                """));
    }

    @Test
    void retainOtherOptions() {
        // language=java
        rewriteRun(java(
                """
                package com.example.app;

                import io.cucumber.junit.CucumberOptions;

                @CucumberOptions(features = "src/test/resources/features", plugin = "pretty")
                public class RunCucumberTest {
                }
                """));
    }
}
