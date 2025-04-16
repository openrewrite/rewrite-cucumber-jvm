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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class RegexToCucumberExpressionTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new RegexToCucumberExpression())
                .parser(JavaParser.fromJavaVersion().classpath("junit-jupiter-api", "cucumber-java"));
    }

    @Test
    @DocumentExample
    void regexToCucumberExpression() {
        // language=java
        rewriteRun(java(
                """
                package com.example.app;

                import io.cucumber.java.Before;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class ExpressionDefinitions {

                    private int a;

                    @Before
                    public void before() {
                        a = 0;
                    }

                    @Given("^five cukes$")
                    public void five_cukes() {
                        a = 5;
                    }

                    @Then("^I expect (\\\\d+)$")
                    public void i_expect_int(Integer c) {
                        assertEquals(c, a);
                    }

                }
                """,
                """
                package com.example.app;

                import io.cucumber.java.Before;
                import io.cucumber.java.en.Given;
                import io.cucumber.java.en.Then;

                import static org.junit.jupiter.api.Assertions.assertEquals;

                public class ExpressionDefinitions {

                    private int a;

                    @Before
                    public void before() {
                        a = 0;
                    }

                    @Given("five cukes")
                    public void five_cukes() {
                        a = 5;
                    }

                    @Then("^I expect (\\\\d+)$")
                    public void i_expect_int(Integer c) {
                        assertEquals(c, a);
                    }

                }
                """));
    }

    @Nested
    @DisplayName("should convert")
    class ShouldConvert {

        @Test
        void only_leading_anchor() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("^five cukes")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""));
        }

        @Test
        void only_trailing_anchor() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("five cukes$")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""));
        }

        @Test
        void forward_slashes() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("/five cukes/")
                        public void five_cukes() {
                        }
                    }""", """
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("five cukes")
                        public void five_cukes() {
                        }
                    }"""));
        }

    }

    @Nested
    @DisplayName("should not convert")
    class ShouldNotConvert {

        @Test
        void unrecognized_capturing_groups() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("^some (foo|bar)$")
                        public void five_cukes(String fooOrBar) {
                        }
                    }"""));
        }

        @Test
        void integer_matchers() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("^(\\\\d+) cukes$")
                        public void int_cukes(int cukes) {
                        }
                    }"""));
        }

        @Test
        void regex_question_mark_optional() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("^cukes?$")
                        public void cukes() {
                        }
                    }"""));
        }

        @Test
        void regex_one_or_more() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;

                    public class ExpressionDefinitions {
                        @Given("^cukes+$")
                        public void cukes() {
                        }
                    }"""));
        }

        @Test
        void disabled() {
            // language=java
            rewriteRun(java("""
                    package com.example.app;

                    import io.cucumber.java.en.Given;
                    import org.junit.jupiter.api.Disabled;

                    public class ExpressionDefinitions {
                        @Disabled("/for now/")
                        public void disabled() {
                        }
                        @Given("trigger getSingleSourceApplicableTest")
                        public void trigger() {
                        }
                    }"""));
        }

    }

}
