package io.cucumber.migration;

import org.junit.jupiter.api.Test;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.version;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/264")
class DropSummaryPrinterTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DropSummaryPrinter())
                .parser(JavaParser.fromJavaVersion().classpath("cucumber-plugin"));
    }

    @Test
    void replaceSummaryPrinterWithPlugin() {
        rewriteRun(
            version(
                // language=java
                java(
                    """
                            package com.example.app;

                            import io.cucumber.plugin.SummaryPrinter;

                            public class CucumberJava8Definitions implements SummaryPrinter {
                            }""", """
                            package com.example.app;

                            import io.cucumber.plugin.Plugin;

                            public class CucumberJava8Definitions implements Plugin {
                            }
                            """),
                17));
    }

    @Test
    void dontDuplicatePlugin() {
        rewriteRun(
            version(
                // language=java
                java(
                    """
                            package com.example.app;

                            import io.cucumber.plugin.Plugin;
                            import io.cucumber.plugin.SummaryPrinter;

                            public class CucumberJava8Definitions implements Plugin, SummaryPrinter {
                            }
                            """,
                    """
                            package com.example.app;

                            import io.cucumber.plugin.Plugin;

                            public class CucumberJava8Definitions implements Plugin {
                            }
                            """),
                17));
    }
}
