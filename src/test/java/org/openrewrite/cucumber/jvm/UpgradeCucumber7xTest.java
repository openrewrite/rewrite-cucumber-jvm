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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.openrewrite.maven.Assertions.pomXml;

/**
 * {@code cucumber-jvm-deps} is not needed by any {@code cucumber-core} from 3.0.0 on, so the upgrade to 7.x
 * does not leave a declaration of it behind.
 */
class UpgradeCucumber7xTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.UpgradeCucumber7x");
    }

    @Test
    void legacyCucumberAndJvmDepsEndUpOnCucumber7WithoutJvmDeps() {
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
            spec -> spec.after(actual -> {
                assertThat(actual)
                  .doesNotContain("cucumber-jvm-deps")
                  .doesNotContain("info.cukes")
                  .containsOnlyOnce("<artifactId>cucumber-java</artifactId>")
                  .containsPattern("<groupId>io\\.cucumber</groupId>\\s+<artifactId>cucumber-java</artifactId>\\s+<version>7\\.\\d+\\.\\d+</version>");
                return actual;
            })
          )
        );
    }

    @Test
    void jvmDepsNextToCucumberJava8ReplacementEndsUpWithOneCucumberJava() {
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
                          <artifactId>cucumber-java8</artifactId>
                          <version>2.3.1</version>
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
            spec -> spec.after(actual -> {
                assertThat(actual)
                  .doesNotContain("cucumber-jvm-deps")
                  .doesNotContain("info.cukes")
                  .containsOnlyOnce("<artifactId>cucumber-java</artifactId>");
                return actual;
            })
          )
        );
    }

    @Test
    void jvmDepsAloneBecomesCucumberJava7() {
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
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """,
            spec -> spec.after(actual -> {
                assertThat(actual)
                  .doesNotContain("cucumber-jvm-deps")
                  .doesNotContain("info.cukes")
                  .containsPattern("<groupId>io\\.cucumber</groupId>\\s+<artifactId>cucumber-java</artifactId>\\s+<version>7\\.\\d+\\.\\d+</version>");
                return actual;
            })
          )
        );
    }

    @Test
    void managedLegacyCucumberAndJvmDepsLoseTheManagedJvmDeps() {
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
            spec -> spec.after(actual -> {
                assertThat(actual)
                  .doesNotContain("cucumber-jvm-deps")
                  .doesNotContain("info.cukes")
                  .containsOnlyOnce("<artifactId>cucumber-java</artifactId>");
                return actual;
            })
          )
        );
    }
}
