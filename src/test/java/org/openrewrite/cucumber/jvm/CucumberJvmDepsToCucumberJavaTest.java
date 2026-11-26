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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.gradle.Assertions.buildGradle;
import static org.openrewrite.gradle.Assertions.buildGradleKts;
import static org.openrewrite.gradle.toolingapi.Assertions.withToolingApi;
import static org.openrewrite.maven.Assertions.pomXml;

class CucumberJvmDepsToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.CucumberJvmDepsToCucumberJava");
    }

    @DocumentExample
    @Test
    void mavenTestDependency() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.6</version>
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
                          <version>%s</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void mavenDependencyManagement() {
        rewriteRun(
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
                              <artifactId>cucumber-jvm-deps</artifactId>
                              <version>1.0.6</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
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
                              <artifactId>cucumber-java</artifactId>
                              <version>%s</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void mavenBomManagedTarget() {
        rewriteRun(
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
                              <version>7.34.3</version>
                              <type>pom</type>
                              <scope>import</scope>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.6</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
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
                              <version>7.34.3</version>
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
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mavenExistingTarget() {
        rewriteRun(
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
                          <version>7.34.3</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.6</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <version>7.34.3</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mavenExistingTargetKeepsItsScopeAndVersion() {
        rewriteRun(
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
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.6</version>
                      </dependency>
                  </dependencies>
              </project>
              """,
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
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mavenExistingManagedTargetKeepsItsVersion() {
        rewriteRun(
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
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-jvm-deps</artifactId>
                              <version>1.0.6</version>
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
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void mavenExistingManagedTargetInParentKeepsItsVersion() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-jvm-deps</artifactId>
                              <version>1.0.6</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void unrelatedDependencies() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-core</artifactId>
                          <version>7.34.3</version>
                      </dependency>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <version>7.34.3</version>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void gradleGroovy() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'io.cucumber:cucumber-jvm-deps:1.0.6'
              }
              """,
            spec -> spec.after(actual -> """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'io.cucumber:cucumber-java:%s'
              }
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void gradleExistingTarget() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'io.cucumber:cucumber-java:7.18.0'
                  testImplementation 'io.cucumber:cucumber-jvm-deps:1.0.6'
              }
              """,
            """
              plugins {
                  id 'java'
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation 'io.cucumber:cucumber-java:7.18.0'
              }
              """
          )
        );
    }

    @Test
    void gradleKotlin() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradleKts(
            """
              plugins {
                  java
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation("io.cucumber:cucumber-jvm-deps:1.0.6")
              }
              """,
            spec -> spec.after(actual -> """
              plugins {
                  java
              }

              repositories {
                  mavenCentral()
              }

              dependencies {
                  testImplementation("io.cucumber:cucumber-java:%s")
              }
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void infoCukesAlone() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.5</version>
                          <scope>provided</scope>
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
                          <version>%s</version>
                          <scope>provided</scope>
                      </dependency>
                  </dependencies>
              </project>
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void infoCukesNextToCucumberJava() {
        rewriteRun(
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
                          <version>2.3.1</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.5</version>
                          <scope>provided</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <version>2.3.1</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void infoCukesNextToLegacyCucumber() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <version>1.2.5</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <version>1.0.5</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-java</artifactId>
                          <version>1.2.5</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }

    @Test
    void infoCukesVersionFromPropertyNextToLegacyCucumber() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <cucumber.version>1.2.2</cucumber.version>
                      <cucumber-jvm-deps>1.0.3</cucumber-jvm-deps>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-jvm-deps</artifactId>
                          <scope>test</scope>
                          <version>${cucumber-jvm-deps}</version>
                      </dependency>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-picocontainer</artifactId>
                          <version>${cucumber.version}</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <cucumber.version>1.2.2</cucumber.version>
                      <cucumber-jvm-deps>1.0.3</cucumber-jvm-deps>
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
              """
          )
        );
    }

    @Test
    void infoCukesDependencyManagement() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                          <dependency>
                              <groupId>info.cukes</groupId>
                              <artifactId>cucumber-jvm-deps</artifactId>
                              <version>1.0.5</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>io.cucumber</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>7.18.0</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void infoCukesDependencyManagementNextToManagedLegacyCucumber() {
        rewriteRun(
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>info.cukes</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>1.2.5</version>
                          </dependency>
                          <dependency>
                              <groupId>info.cukes</groupId>
                              <artifactId>cucumber-jvm-deps</artifactId>
                              <version>1.0.5</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """,
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencyManagement>
                      <dependencies>
                          <dependency>
                              <groupId>info.cukes</groupId>
                              <artifactId>cucumber-java</artifactId>
                              <version>1.2.5</version>
                          </dependency>
                      </dependencies>
                  </dependencyManagement>
              </project>
              """
          )
        );
    }

    @Test
    void gradleInfoCukesAlone() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation 'info.cukes:cucumber-jvm-deps:1.0.5'
              }
              """,
            spec -> spec.after(actual -> """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation 'io.cucumber:cucumber-java:%s'
              }
              """.formatted(cucumberJavaVersion(actual)))
          )
        );
    }

    @Test
    void gradleInfoCukesNextToLegacyCucumber() {
        rewriteRun(
          spec -> spec.beforeRecipe(withToolingApi()),
          buildGradle(
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation 'info.cukes:cucumber-java:1.2.5'
                  testImplementation 'info.cukes:cucumber-jvm-deps:1.0.5'
              }
              """,
            """
              plugins {
                  id 'java'
              }
              repositories {
                  mavenCentral()
              }
              dependencies {
                  testImplementation 'info.cukes:cucumber-java:1.2.5'
              }
              """
          )
        );
    }

    private static String cucumberJavaVersion(String actual) {
        Matcher matcher = Pattern.compile("cucumber-java(?::|</artifactId>\\s+<version>)(7\\.\\d+\\.\\d+)").matcher(actual);
        assertThat(matcher.find()).as("Cucumber Java 7.x dependency in %s", actual).isTrue();
        return matcher.group(1);
    }
}
