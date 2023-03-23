package io.cucumber.migration;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class CucumberAnnotationToSuiteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberAnnotationToSuite())
                .parser(JavaParser.fromJavaVersion().classpath("cucumber-junit-platform-engine",
                    "junit-platform-suite-api"));
    }

    @Test
    void shouldReplaceCucumberAnnotationWithSuiteWithSelectedClasspathResource() {
        // language=java
        rewriteRun(
            java(
                """
                        package com.example.app;

                        import io.cucumber.junit.platform.engine.Cucumber;

                        @Cucumber
                        public class CucumberJava8Definitions {
                        }
                        """,
                """
                        package com.example.app;

                        import org.junit.platform.suite.api.SelectClasspathResource;
                        import org.junit.platform.suite.api.Suite;

                        @Suite
                        @SelectClasspathResource("com/example/app")
                        public class CucumberJava8Definitions {
                        }
                        """));
    }
}
