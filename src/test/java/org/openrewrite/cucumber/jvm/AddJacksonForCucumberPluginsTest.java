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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpecs;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/63")
class AddJacksonForCucumberPluginsTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddJacksonForCucumberPlugins())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(),
            "cucumber-junit-7", "cucumber-testng-7", "cucumber-junit-platform-engine-7", "junit-platform-suite-api-1"));
    }

    //language=xml
    private static final String POM = """
      <project>
          <groupId>com.example</groupId>
          <artifactId>app</artifactId>
          <version>1.0.0</version>
          <dependencies>
              <dependency>
                  <groupId>io.cucumber</groupId>
                  <artifactId>cucumber-java</artifactId>
                  <version>7.18.0</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    //language=xml
    private static final String POM_WITH_JACKSON = """
      <project>
          <groupId>com.example</groupId>
          <artifactId>app</artifactId>
          <version>1.0.0</version>
          <dependencies>
              <dependency>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-databind</artifactId>
                  <version>%1$s</version>
                  <scope>test</scope>
              </dependency>
              <dependency>
                  <groupId>com.fasterxml.jackson.datatype</groupId>
                  <artifactId>jackson-datatype-jdk8</artifactId>
                  <version>%1$s</version>
                  <scope>test</scope>
              </dependency>
              <dependency>
                  <groupId>io.cucumber</groupId>
                  <artifactId>cucumber-java</artifactId>
                  <version>7.18.0</version>
                  <scope>test</scope>
              </dependency>
          </dependencies>
      </project>
      """;

    private static SourceSpecs pomGainingJackson() {
        return pomXml(POM, spec -> spec.after(actual -> POM_WITH_JACKSON.formatted(jackson2(actual))));
    }

    @DocumentExample
    @Test
    void htmlPluginInJUnitPlatformProperties() {
        rewriteRun(
          mavenProject("app",
            pomGainingJackson(),
            srcTestResources(
              properties(
                """
                  cucumber.plugin=pretty, html:target/cucumber-reports/cucumber.html
                  """,
                spec -> spec.path("junit-platform.properties")
              )
            )
          )
        );
    }

    @Test
    void jsonPluginInCucumberOptions() {
        rewriteRun(
          mavenProject("app",
            pomGainingJackson(),
            srcTestJava(
              //language=java
              java(
                """
                  import io.cucumber.junit.CucumberOptions;

                  @CucumberOptions(plugin = {"pretty", "json:target/cucumber.json"})
                  public class RunCucumberTest {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void publishInTestNGCucumberOptions() {
        rewriteRun(
          mavenProject("app",
            pomGainingJackson(),
            srcTestJava(
              //language=java
              java(
                """
                  import io.cucumber.testng.AbstractTestNGCucumberTests;
                  import io.cucumber.testng.CucumberOptions;

                  @CucumberOptions(publish = true)
                  public class RunCucumberTest extends AbstractTestNGCucumberTests {
                  }
                  """
              )
            )
          )
        );
    }

    @Test
    void messagePluginInConfigurationParameter() {
        rewriteRun(
          mavenProject("app",
            pomGainingJackson(),
            srcTestJava(
              //language=java
              java(
                """
                  import org.junit.platform.suite.api.ConfigurationParameter;
                  import org.junit.platform.suite.api.IncludeEngines;
                  import org.junit.platform.suite.api.SelectClasspathResource;
                  import org.junit.platform.suite.api.Suite;

                  import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

                  @Suite
                  @IncludeEngines("cucumber")
                  @SelectClasspathResource("features")
                  @ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty, message:target/cucumber.ndjson")
                  public class RunCucumberTest {
                  }
                  """
              )
            )
          )
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
      "cucumber.plugin=pretty, junit:target/cucumber.xml, rerun:target/rerun.txt",
      "cucumber.publish.enabled=false",
      "cucumber.publish.quiet=true"
    })
    void noPluginNeedingJackson(String property) {
        rewriteRun(
          mavenProject("app",
            pomXml(POM),
            srcTestResources(
              properties(property, spec -> spec.path("cucumber.properties"))
            )
          )
        );
    }

    @Test
    void cucumberPluginKeyOutsideOfCucumberProperties() {
        rewriteRun(
          mavenProject("app",
            pomXml(POM),
            srcMainResources(
              properties("cucumber.plugin=html:target/cucumber.html", spec -> spec.path("application.properties"))
            )
          )
        );
    }

    @Test
    void jackson2AlreadyPresent() {
        rewriteRun(
          mavenProject("app",
            pomXml(POM_WITH_JACKSON.formatted("2.17.2")),
            srcTestResources(
              properties("cucumber.plugin=html:target/cucumber.html", spec -> spec.path("junit-platform.properties"))
            )
          )
        );
    }

    @Test
    void jackson3AlreadyPresent() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>tools.jackson.core</groupId>
                            <artifactId>jackson-databind</artifactId>
                            <version>3.0.0</version>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-java</artifactId>
                            <version>7.18.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            ),
            srcTestResources(
              properties("cucumber.plugin=html:target/cucumber.html", spec -> spec.path("junit-platform.properties"))
            )
          )
        );
    }

    @Test
    void onlyTheModuleConfiguringThePlugin() {
        rewriteRun(
          mavenProject("app",
            pomGainingJackson(),
            srcTestResources(
              properties("cucumber.plugin=timeline:target/timeline", spec -> spec.path("junit-platform.properties"))
            )
          ),
          mavenProject("other",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>other</artifactId>
                    <version>1.0.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-java</artifactId>
                            <version>7.18.0</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """
            )
          )
        );
    }

    private static final Pattern JACKSON_2_VERSION = Pattern.compile(">(2\\.\\d+\\.\\d+)<");

    private static String jackson2(String actual) {
        Matcher matcher = JACKSON_2_VERSION.matcher(actual);
        assertThat(matcher.find()).as("Jackson 2.x version in %s", actual).isTrue();
        return matcher.group(1);
    }
}
