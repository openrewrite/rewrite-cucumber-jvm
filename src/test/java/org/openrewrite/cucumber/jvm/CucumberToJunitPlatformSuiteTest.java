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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.maven.MavenExecutionContextView;
import org.openrewrite.maven.tree.MavenRepository;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.SourceSpec;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class CucumberToJunitPlatformSuiteTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.CucumberToJunitPlatformSuite");
    }

    @Nested
    class ExplicitVersion {

        @DocumentExample
        @Test
        void movesTheWholeFamilyToTheEngineVersion() {
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
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>7.18.0</version>
                                <scope>test</scope>
                            </dependency>
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
                                <artifactId>cucumber-java</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }

        @Test
        void sharedVersionProperty() {
            rewriteRun(
              mavenProject("app",
                //language=xml
                pomXml(
                  """
                    <project>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                        <properties>
                            <cucumber.version>4.8.1</cucumber.version>
                        </properties>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>${cucumber.version}</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <version>${cucumber.version}</version>
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
                        <properties>
                            <cucumber.version>%1$s</cucumber.version>
                        </properties>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>${cucumber.version}</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }

        @Test
        void cucumber4Runner() {
            rewriteRun(
              spec -> spec.parser(JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "cucumber-junit-4.8.1")),
              mavenProject("app",
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
                                <artifactId>cucumber-java</artifactId>
                                <version>4.8.1</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <version>4.8.1</version>
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
                                <artifactId>cucumber-java</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>org.junit.platform</groupId>
                                <artifactId>junit-platform-suite</artifactId>
                                <version>%2$s</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual), CucumberRunWithToSuiteTest.addedJUnitPlatformSuiteVersion(actual)))
                )
              )
            );
        }

        @Test
        void cucumber1() {
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
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>1.2.6</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <version>1.2.6</version>
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
                                <artifactId>cucumber-java</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }
    }

    @Nested
    class ManagedByRemoteParent {

        @TempDir
        Path corporateRepository;

        @Test
        void parentManagesOnlyCucumberJUnit() {
            rewriteRun(
              spec -> remoteParent(spec,
                //language=xml
                """
                  <dependency>
                      <groupId>io.cucumber</groupId>
                      <artifactId>cucumber-junit</artifactId>
                      <version>7.18.0</version>
                  </dependency>
                  """),
              mavenProject("app",
                //language=xml
                pomXml(
                  """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>7.18.0</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <scope>test</scope>
                                <version>%1$s</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }

        @Test
        void parentManagesTheFamily() {
            rewriteRun(
              spec -> remoteParent(spec,
                //language=xml
                """
                  <dependency>
                      <groupId>io.cucumber</groupId>
                      <artifactId>cucumber-java</artifactId>
                      <version>7.18.0</version>
                  </dependency>
                  <dependency>
                      <groupId>io.cucumber</groupId>
                      <artifactId>cucumber-junit</artifactId>
                      <version>7.18.0</version>
                  </dependency>
                  """),
              mavenProject("app",
                //language=xml
                pomXml(
                  """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <scope>test</scope>
                                <version>%1$s</version>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <scope>test</scope>
                                <version>%1$s</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }

        @Test
        void parentManagesCucumber4() {
            rewriteRun(
              spec -> remoteParent(spec,
                //language=xml
                """
                  <dependency>
                      <groupId>io.cucumber</groupId>
                      <artifactId>cucumber-junit</artifactId>
                      <version>4.8.1</version>
                  </dependency>
                  """),
              mavenProject("app",
                //language=xml
                pomXml(
                  """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>4.8.1</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit</artifactId>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """,
                  spec -> spec.after(actual -> """
                    <project>
                        <parent>
                            <groupId>com.example</groupId>
                            <artifactId>corporate-parent</artifactId>
                            <version>1.0.0</version>
                        </parent>
                        <artifactId>app</artifactId>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java</artifactId>
                                <version>%1$s</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-junit-platform-engine</artifactId>
                                <scope>test</scope>
                                <version>%1$s</version>
                            </dependency>
                        </dependencies>
                    </project>
                    """.formatted(cucumber7(actual)))
                )
              )
            );
        }

        // A parent that is not among the sources, as in a corporate parent resolved from a repository, which the
        // recipe can neither see the sources of nor change
        private void remoteParent(RecipeSpec spec, String managedDependencies) {
            Path pom = corporateRepository.resolve("com/example/corporate-parent/1.0.0/corporate-parent-1.0.0.pom");
            try {
                Files.createDirectories(pom.getParent());
                //language=xml
                Files.writeString(pom, """
                  <project>
                      <groupId>com.example</groupId>
                      <artifactId>corporate-parent</artifactId>
                      <version>1.0.0</version>
                      <packaging>pom</packaging>
                      <dependencyManagement>
                          <dependencies>
                              %s
                          </dependencies>
                      </dependencyManagement>
                  </project>
                  """.formatted(managedDependencies));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            MavenRepository repository = MavenRepository.builder()
              .id("corporate")
              .uri(corporateRepository.toUri().toString())
              .knownToExist(true)
              .build();
            spec.executionContext(MavenExecutionContextView.view(defaultExecutionContext(new SourceSpec[0]))
              .setRepositories(List.of(repository)));
        }
    }

    @Test
    void managedByCucumberBom() {
        rewriteRun(
          mavenProject("app",
            //language=xml
            pomXml(
              """
                <project>
                    <groupId>com.example</groupId>
                    <artifactId>app</artifactId>
                    <version>1.0.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-bom</artifactId>
                                <version>7.18.0</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit</artifactId>
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
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-bom</artifactId>
                                <version>%s</version>
                                <type>pom</type>
                                <scope>import</scope>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-java</artifactId>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(cucumber7(actual)))
            )
          )
        );
    }

    @Test
    void upgradeFromCucumber4MigratesTheRunnerInTheSameRun() {
        rewriteRun(
          spec -> spec
            .recipeFromResources("org.openrewrite.cucumber.jvm.UpgradeCucumber7x")
            .parser(JavaParser.fromJavaVersion()
              .classpathFromResources(new InMemoryExecutionContext(), "junit-4", "cucumber-junit-4.8.1")),
          mavenProject("app",
            srcTestJava(
              //language=java
              java(
                """
                  package com.example;

                  import cucumber.api.junit.Cucumber;
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
                            <artifactId>cucumber-java</artifactId>
                            <version>4.8.1</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit</artifactId>
                            <version>4.8.1</version>
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
                            <artifactId>cucumber-java</artifactId>
                            <version>%1$s</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-junit-platform-engine</artifactId>
                            <version>%1$s</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>org.junit.platform</groupId>
                            <artifactId>junit-platform-suite</artifactId>
                            <version>%2$s</version>
                            <scope>test</scope>
                        </dependency>
                    </dependencies>
                </project>
                """.formatted(cucumber7(actual), CucumberRunWithToSuiteTest.addedJUnitPlatformSuiteVersion(actual)))
            )
          )
        );
    }

    // The family moves to whichever 7.x is the latest on Maven Central; read it back out of the migrated pom to keep
    // asserting the whole document without pinning a release
    private static final Pattern CUCUMBER_7_VERSION = Pattern.compile(">(7\\.\\d+\\.\\d+)<");

    static String cucumber7(String actual) {
        Matcher matcher = CUCUMBER_7_VERSION.matcher(actual);
        assertThat(matcher.find()).as("Cucumber 7.x version in %s", actual).isTrue();
        String version = matcher.group(1);
        assertThat(version).as("moved off the original 7.18.0").isNotEqualTo("7.18.0");
        return version;
    }
}
