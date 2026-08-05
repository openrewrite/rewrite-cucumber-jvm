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

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
class MigrateRuntimeOptionsBuilderTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        // Both methods are gone from every 7.x release, so the types come from the last release that had them
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.MigrateRuntimeOptionsBuilder")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-core-6.11.0"));
    }

    @DocumentExample
    @Test
    void migrateDefaultsInBuilderChain() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.core.options.CommandlineOptionsParser;
            import io.cucumber.core.options.RuntimeOptions;

            class CucumberRunner {
                RuntimeOptions parse(String[] argv) {
                    return new CommandlineOptionsParser(System.out)
                            .parse(argv)
                            .addDefaultGlueIfAbsent()
                            .addDefaultFeaturePathIfAbsent()
                            .addDefaultFormatterIfAbsent()
                            .addDefaultSummaryPrinterIfAbsent()
                            .enablePublishPlugin()
                            .build();
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.core.options.CommandlineOptionsParser;
            import io.cucumber.core.options.RuntimeOptions;

            class CucumberRunner {
                RuntimeOptions parse(String[] argv) {
                    return new CommandlineOptionsParser(System.out)
                            .parse(argv)
                            .addDefaultGlueIfAbsent()
                            .addDefaultFeaturePathIfAbsent()
                            .addDefaultSummaryPrinterIfNotDisabled()
                            .enablePublishPlugin()
                            .build();
                }
            }
            """));
    }

    @Test
    void migrateDefaultsCalledAsStatements() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.core.options.RuntimeOptions;
            import io.cucumber.core.options.RuntimeOptionsBuilder;

            class CucumberRunner {
                RuntimeOptions build() {
                    RuntimeOptionsBuilder builder = new RuntimeOptionsBuilder();
                    builder.addDefaultFormatterIfAbsent();
                    builder.addDefaultSummaryPrinterIfAbsent();
                    return builder.build();
                }
            }
            """,
          """
            package com.example.app;

            import io.cucumber.core.options.RuntimeOptions;
            import io.cucumber.core.options.RuntimeOptionsBuilder;

            class CucumberRunner {
                RuntimeOptions build() {
                    RuntimeOptionsBuilder builder = new RuntimeOptionsBuilder();
                    builder.addDefaultSummaryPrinterIfNotDisabled();
                    return builder.build();
                }
            }
            """));
    }

    @Test
    void retainUnrelatedBuilderCalls() {
        // language=java
        rewriteRun(java(
          """
            package com.example.app;

            import io.cucumber.core.options.RuntimeOptions;
            import io.cucumber.core.options.RuntimeOptionsBuilder;

            class CucumberRunner {
                RuntimeOptions build() {
                    return new RuntimeOptionsBuilder()
                            .addDefaultGlueIfAbsent()
                            .addDefaultFeaturePathIfAbsent()
                            .build();
                }
            }
            """));
    }
}
