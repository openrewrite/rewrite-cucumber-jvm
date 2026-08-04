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
class CollapseCucumberOptionsTagsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CollapseCucumberOptionsTags())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-junit-5", "cucumber-testng-5"));
    }

    @DocumentExample
    @Test
    void combineTagsWithAnd() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.junit.CucumberOptions;

            @CucumberOptions(features = "src/test/resources/features", tags = {"@smoke", "not @wip"})
            public class RunCucumberTest {
            }
            """,
          """
            package com.example.app;

            import io.cucumber.junit.CucumberOptions;

            @CucumberOptions(features = "src/test/resources/features", tags = "(@smoke) and (not @wip)")
            public class RunCucumberTest {
            }
            """));
    }

    @Test
    void unwrapSingleElementArray() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.testng.CucumberOptions;

            @CucumberOptions(tags = {"@smoke"})
            public class RunCucumberTest {
            }
            """,
          """
            package com.example.app;

            import io.cucumber.testng.CucumberOptions;

            @CucumberOptions(tags = "@smoke")
            public class RunCucumberTest {
            }
            """));
    }

    @Test
    void retainSingleTagExpression() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.junit.CucumberOptions;

            @CucumberOptions(tags = "@smoke and not @wip")
            public class RunCucumberTest {
            }
            """));
    }

    @Test
    void retainTagsThatAreNotStringLiterals() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.junit.CucumberOptions;

            @CucumberOptions(tags = {RunCucumberTest.SMOKE, "not @wip"})
            public class RunCucumberTest {
                static final String SMOKE = "@smoke";
            }
            """));
    }
}
