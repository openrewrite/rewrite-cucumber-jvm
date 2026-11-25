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

import static org.openrewrite.cucumber.jvm.CucumberToJunitPlatformSuiteTest.cucumber7;
import static org.openrewrite.java.Assertions.*;
import static org.openrewrite.maven.Assertions.pomXml;

class UpgradeCucumber7xTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .recipeFromResources("org.openrewrite.cucumber.jvm.UpgradeCucumber7x")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-java-4.8.1"));
    }

    @DocumentExample
    @Test
    void addCucumberJavaThatAnObjectFactoryUsedToBring() {
        rewriteRun(
          mavenProject("app",
            srcTestJava(
              //language=java
              java(
                """
                  package com.example;

                  import cucumber.api.java.en.Given;

                  public class Steps {
                      @Given("a step")
                      public void aStep() {
                      }
                  }
                  """,
                """
                  package com.example;

                  import io.cucumber.java.en.Given;

                  public class Steps {
                      @Given("a step")
                      public void aStep() {
                      }
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
                    <properties>
                        <cucumber.version>1.2.2</cucumber.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>info.cukes</groupId>
                            <artifactId>cucumber-picocontainer</artifactId>
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
                            <version>%1$s</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-picocontainer</artifactId>
                            <version>${cucumber.version}</version>
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
    void keepCucumberJavaThatIsDeclared() {
        rewriteRun(
          mavenProject("app",
            srcTestJava(
              //language=java
              java(
                """
                  package com.example;

                  import io.cucumber.java.en.Given;

                  public class Steps {
                      @Given("a step")
                      public void aStep() {
                      }
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
                            <version>7.18.0</version>
                            <scope>test</scope>
                        </dependency>
                        <dependency>
                            <groupId>io.cucumber</groupId>
                            <artifactId>cucumber-picocontainer</artifactId>
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
                            <artifactId>cucumber-picocontainer</artifactId>
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
