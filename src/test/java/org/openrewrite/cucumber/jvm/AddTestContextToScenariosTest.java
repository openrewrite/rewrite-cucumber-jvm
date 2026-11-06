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
import org.openrewrite.Issue;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/63")
class AddTestContextToScenariosTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new AddTestContextToScenarios())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-testng-7")
            // The type tables hold no TestNG
            //language=java
            .dependsOn(
              """
                package org.testng;
                public interface ITestContext {}
                """,
              """
                package org.testng.annotations;
                public @interface DataProvider {
                    boolean parallel() default false;
                }
                """
            ));
    }

    @DocumentExample
    @Test
    void parallelScenarios() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends AbstractTestNGCucumberTests {

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios() {
                      return super.scenarios();
                  }
              }
              """,
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;
              import org.testng.ITestContext;
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends AbstractTestNGCucumberTests {

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios(ITestContext context) {
                      return super.scenarios(context);
                  }
              }
              """
          )
        );
    }

    @Test
    void contextAlreadyNamedInBody() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends AbstractTestNGCucumberTests {

                  private final String context = "smoke";

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios() {
                      System.out.println(context);
                      return super.scenarios();
                  }
              }
              """,
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;
              import org.testng.ITestContext;
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends AbstractTestNGCucumberTests {

                  private final String context = "smoke";

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios(ITestContext testContext) {
                      System.out.println(context);
                      return super.scenarios(testContext);
                  }
              }
              """
          )
        );
    }

    @Test
    void throughIntermediateBaseClass() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;

              public abstract class BaseCucumberTest extends AbstractTestNGCucumberTests {
              }
              """
          ),
          //language=java
          java(
            """
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends BaseCucumberTest {

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios() {
                      return super.scenarios();
                  }
              }
              """,
            """
              import org.testng.ITestContext;
              import org.testng.annotations.DataProvider;

              public class RunCucumberTest extends BaseCucumberTest {

                  @Override
                  @DataProvider(parallel = true)
                  public Object[][] scenarios(ITestContext context) {
                      return super.scenarios(context);
                  }
              }
              """
          )
        );
    }

    @Test
    void unrelatedScenariosMethod() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.testng.AbstractTestNGCucumberTests;

              public class Scenarios {

                  AbstractTestNGCucumberTests tests;

                  public Object[][] scenarios() {
                      return new Object[0][];
                  }
              }
              """
          )
        );
    }
}
