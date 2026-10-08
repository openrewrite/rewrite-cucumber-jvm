/*
 * Copyright 2026 the original author or authors.
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
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class CucumberRunWithToSuiteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "cucumber-junit-7"))
          .recipe(new CucumberRunWithToSuite());
    }

    @DocumentExample
    @Test
    void runnerWithOptions() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.retry.acceptance;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              @CucumberOptions(
                plugin = {"pretty", "html:target/cucumber-html-reports", "json:target/cucumber.json"},
                features = "src/test/resources/acceptance/",
                glue = "com.example.retry.acceptance.steps",
                tags = "@component_tests")
              public class AcceptanceTest {
              }
              """,
            """
              package com.example.retry.acceptance;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.*;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("acceptance")
              @ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, html:target/cucumber-html-reports, json:target/cucumber.json")
              @ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "@component_tests")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.retry.acceptance.steps")
              public class AcceptanceTest {
              }
              """
          )
        );
    }

    @Test
    void runnerWithoutOptionsUsesItsOwnPackage() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              public class RunCucumberTest {
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("com/example")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void allOptions() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import io.cucumber.junit.CucumberOptions.SnippetType;
              import org.junit.runner.RunWith;

              @SuppressWarnings("unused")
              @RunWith(Cucumber.class)
              @CucumberOptions(
                features = {"classpath:features/a", "classpath:/features/b/"},
                extraGlue = "com.example.hooks",
                name = "^Checkout",
                monochrome = true,
                dryRun = false,
                publish = true,
                snippets = SnippetType.CAMELCASE,
                objectFactory = CustomObjectFactory.class,
                strict = false,
                stepNotifications = true,
                useFileNameCompatibleName = true)
              public class RunCucumberTest {
              }

              class CustomObjectFactory {
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.*;

              @SuppressWarnings("unused")
              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("features/a")
              @SelectClasspathResource("features/b")
              @ConfigurationParameter(key = FILTER_NAME_PROPERTY_NAME, value = "^Checkout")
              @ConfigurationParameter(key = ANSI_COLORS_DISABLED_PROPERTY_NAME, value = "true")
              @ConfigurationParameter(key = PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME, value = "true")
              @ConfigurationParameter(key = SNIPPET_TYPE_PROPERTY_NAME, value = "camelcase")
              @ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "com.example.CustomObjectFactory")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example, com.example.hooks")
              public class RunCucumberTest {
              }

              class CustomObjectFactory {
              }
              """
          )
        );
    }

    @Test
    void featuresOutsideTheClasspath() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              @CucumberOptions(features = "src/test/features/checkout.feature:12", glue = "com.example.steps")
              public class RunCucumberTest {
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.FEATURES_PROPERTY_NAME;
              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @ConfigurationParameter(key = FEATURES_PROPERTY_NAME, value = "src/test/features/checkout.feature:12")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.steps")
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void commentOnOptionsReferringToConstants() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              @CucumberOptions(features = RunCucumberTest.FEATURES)
              public class RunCucumberTest {
                  static final String FEATURES = "classpath:features";
              }
              """,
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              // Not migrated to a JUnit Platform `@Suite`, as `features` is not a string literal
              @RunWith(Cucumber.class)
              @CucumberOptions(features = RunCucumberTest.FEATURES)
              public class RunCucumberTest {
                  static final String FEATURES = "classpath:features";
              }
              """
          )
        );
    }

    @Test
    void commentOnRunnerInTheDefaultPackage() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.junit.Cucumber;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              public class RunCucumberTest {
              }
              """,
            """
              import io.cucumber.junit.Cucumber;
              import org.junit.runner.RunWith;

              // Not migrated to a JUnit Platform `@Suite`, as it is in the default package and has no `features`
              @RunWith(Cucumber.class)
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void emptyFeaturesAndGlueFallBackToThePackage() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              @CucumberOptions(features = {}, glue = {})
              public class RunCucumberTest {
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("com/example")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void tagsArrayIsCombinedWithAnd() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "cucumber-junit-5.7.0")),
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              @CucumberOptions(tags = {"@a", "not @b"}, glue = "com.example.steps")
              public class RunCucumberTest {
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.IncludeEngines;
              import org.junit.platform.suite.api.SelectClasspathResource;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.FILTER_TAGS_PROPERTY_NAME;
              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("com/example")
              @ConfigurationParameter(key = FILTER_TAGS_PROPERTY_NAME, value = "(@a) and (not @b)")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example.steps")
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void classLevelSetupBecomesSuiteLifecycle() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import org.junit.AfterClass;
              import org.junit.BeforeClass;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              public class RunCucumberTest {
                  @BeforeClass
                  public static void startServer() {
                  }

                  @AfterClass
                  public static void stopServer() {
                  }
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.*;

              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("com/example")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
              public class RunCucumberTest {
                  @BeforeSuite
                  public static void startServer() {
                  }

                  @AfterSuite
                  public static void stopServer() {
                  }
              }
              """
          )
        );
    }

    @Test
    void jupiterClassLevelSetupBecomesSuiteLifecycle() {
        rewriteRun(
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "cucumber-junit-7", "junit-jupiter-api")),
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import org.junit.jupiter.api.AfterAll;
              import org.junit.jupiter.api.BeforeAll;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              public class RunCucumberTest {
                  @BeforeAll
                  static void startServer() {
                  }

                  @AfterAll
                  static void stopServer() {
                  }
              }
              """,
            """
              package com.example;

              import org.junit.platform.suite.api.*;

              import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

              @Suite
              @IncludeEngines("cucumber")
              @SelectClasspathResource("com/example")
              @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
              public class RunCucumberTest {
                  @BeforeSuite
                  static void startServer() {
                  }

                  @AfterSuite
                  static void stopServer() {
                  }
              }
              """
          )
        );
    }

    @Test
    void commentOnRunnerInheritingOptions() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.CucumberOptions;

              @CucumberOptions(features = "classpath:features", glue = "com.example.steps")
              public abstract class BaseRunner {
              }
              """
          ),
          //language=java
          java(
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import org.junit.runner.RunWith;

              @RunWith(Cucumber.class)
              public class RunCucumberTest extends BaseRunner {
              }
              """,
            """
              package com.example;

              import io.cucumber.junit.Cucumber;
              import org.junit.runner.RunWith;

              // Not migrated to a JUnit Platform `@Suite`, as it extends another class, which may contribute `@CucumberOptions`
              @RunWith(Cucumber.class)
              public class RunCucumberTest extends BaseRunner {
              }
              """
          )
        );
    }

    @Test
    void swapsCucumberJUnitDependency() {
        rewriteRun(
          spec -> spec.recipeFromResources("org.openrewrite.cucumber.jvm.CucumberToJunitPlatformSuite"),
          mavenProject("project",
            srcTestJava(
              //language=java
              java(
                """
                  package com.example;

                  import io.cucumber.junit.Cucumber;
                  import org.junit.runner.RunWith;

                  @RunWith(Cucumber.class)
                  public class RunCucumberTest {
                  }
                  """,
                """
                  package com.example;

                  import org.junit.platform.suite.api.ConfigurationParameter;
                  import org.junit.platform.suite.api.IncludeEngines;
                  import org.junit.platform.suite.api.SelectClasspathResource;
                  import org.junit.platform.suite.api.Suite;

                  import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

                  @Suite
                  @IncludeEngines("cucumber")
                  @SelectClasspathResource("com/example")
                  @ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "com.example")
                  public class RunCucumberTest {
                  }
                  """
              )
            ),
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit</artifactId>
                            <version>7.18.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """,
              spec -> spec.after(actual -> """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <version>7.18.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.platform</groupId>
                            <artifactId>junit-platform-suite</artifactId>
                            <version>%s</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(addedJUnitPlatformSuiteVersion(actual)))
            )
          )
        );
    }

    // `AddDependency` resolves `1.x` against Maven Central, so the version moves with each JUnit release;
    // read it back out of the migrated pom to keep asserting the whole document without pinning a release
    private static final Pattern JUNIT_PLATFORM_SUITE_VERSION =
      Pattern.compile("<artifactId>junit-platform-suite</artifactId>\\s+<version>(1\\.\\d+[^<]*)</version>");

    private static String addedJUnitPlatformSuiteVersion(String actual) {
        Matcher matcher = JUNIT_PLATFORM_SUITE_VERSION.matcher(actual);
        assertThat(matcher.find()).as("junit-platform-suite added to %s", actual).isTrue();
        return matcher.group(1);
    }
}
