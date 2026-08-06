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
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class UpgradeCucumber2xTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.UpgradeCucumber2x");
    }

    @DocumentExample
    @Test
    void changeRunnerAndGlueArtifacts() {
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
                          <artifactId>cucumber-junit</artifactId>
                          <version>1.2.5</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-spring</artifactId>
                          <version>1.2.5</version>
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
                          <artifactId>cucumber-junit</artifactId>
                          <version>2.4.0</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-spring</artifactId>
                          <version>2.4.0</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          ));
    }

    @Test
    void leaveArtifactsWithoutA2xReleaseAlone() {
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
                          <artifactId>gherkin</artifactId>
                          <version>2.12.2</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>info.cukes</groupId>
                          <artifactId>cucumber-pro</artifactId>
                          <version>1.0.16</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          ));
    }
}
