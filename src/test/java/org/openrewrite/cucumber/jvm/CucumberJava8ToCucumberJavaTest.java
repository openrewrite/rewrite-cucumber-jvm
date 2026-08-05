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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.Issue;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;
import static org.openrewrite.java.Assertions.mavenProject;
import static org.openrewrite.java.Assertions.srcTestJava;
import static org.openrewrite.maven.Assertions.pomXml;

@Issue("https://github.com/openrewrite/rewrite-testing-frameworks/issues/259")
class CucumberJava8ToCucumberJavaTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(Environment.builder()
          .scanRuntimeClasspath("org.openrewrite.cucumber.jvm")
          .build().activateRecipes("org.openrewrite.cucumber.jvm.CucumberJava8ToJava"));
        spec.parser(JavaParser.fromJavaVersion()
          .logCompilationWarningsAndErrors(true)
          .classpathFromResources(new InMemoryExecutionContext(),
            "junit-jupiter-api", "cucumber-java-7", "cucumber-java8-7", "datatable"));
    }

    @DocumentExample
    @SuppressWarnings("CodeBlock2Expr")
    @Test
    void cucumberJava8HooksAndSteps() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.java8.En;
              import io.cucumber.java8.Scenario;
              import io.cucumber.java8.Status;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class CucumberJava8Definitions implements En {

                  private int a;

                  public CucumberJava8Definitions() {
                      Before(() -> {
                          a = 0;
                      });
                      When("I add {int}", (Integer b) -> {
                          a += b;
                      });
                      Then("I expect {int}", (Integer c) -> assertEquals(c, a));

                      After((Scenario scn) -> {
                          if (scn.getStatus() == Status.FAILED) {
                              scn.log("failed");
                          }
                      });

                  }

              }""", """
              package com.example.app;

              import io.cucumber.java.After;
              import io.cucumber.java.Before;
              import io.cucumber.java.Scenario;
              import io.cucumber.java.Status;
              import io.cucumber.java.en.Then;
              import io.cucumber.java.en.When;

              import static org.junit.jupiter.api.Assertions.assertEquals;

              public class CucumberJava8Definitions {

                  private int a;

                  @Before
                  public void before() {
                      a = 0;
                  }

                  @After
                  public void after(Scenario scn) {
                      if (scn.getStatus() == Status.FAILED) {
                          scn.log("failed");
                      }
                  }

                  @When("I add {int}")
                  public void i_add_int(Integer b) {
                      a += b;
                  }

                  @Then("I expect {int}")
                  public void i_expect_int(Integer c) {
                      assertEquals(c, a);
                  }

              }
              """));
    }

    @Nested
    class StepMigration {
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void cucumberJava8SampleToJavaSample() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  import static org.junit.jupiter.api.Assertions.assertEquals;

                  public class CalculatorStepDefinitions implements En {
                      private RpnCalculator calc;

                      public CalculatorStepDefinitions() {
                          Given("a calculator I just turned on", () -> {
                              calc = new RpnCalculator();
                          });

                          When("I add {int} and {int}", (Integer arg1, Integer arg2) -> {
                              calc.push(arg1);
                              calc.push(arg2);
                              calc.push("+");
                          });

                          Then("the result is {double}", (Double expected) -> assertEquals(expected, calc.value()));
                      }

                      static class RpnCalculator {
                          void push(Object string) {
                          }

                          public Double value() {
                              return Double.NaN;
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;
                  import io.cucumber.java.en.Then;
                  import io.cucumber.java.en.When;

                  import static org.junit.jupiter.api.Assertions.assertEquals;

                  public class CalculatorStepDefinitions {
                      private RpnCalculator calc;

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          calc = new RpnCalculator();
                      }

                      @When("I add {int} and {int}")
                      public void i_add_int_and_int(Integer arg1, Integer arg2) {
                          calc.push(arg1);
                          calc.push(arg2);
                          calc.push("+");
                      }

                      @Then("the result is {double}")
                      public void the_result_is_double(Double expected) {
                          assertEquals(expected, calc.value());
                      }

                      static class RpnCalculator {
                          void push(Object string) {
                          }

                          public Double value() {
                              return Double.NaN;
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainEmptyConstructorsOfOtherClasses() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          Given("a calculator I just turned on", () -> {
                          });
                      }

                      static class Money {
                          private int amount;

                          Money(int amount) {
                              this.amount = amount;
                          }

                          Money() {
                          }
                      }
                  }

                  class Helper {
                      Helper() {
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                      }

                      static class Money {
                          private int amount;

                          Money(int amount) {
                              this.amount = amount;
                          }

                          Money() {
                          }
                      }
                  }

                  class Helper {
                      Helper() {
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainConstructorInjectedDependencies() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions(RpnCalculator calc, Log log) {
                          Given("a calculator I just turned on", () -> {
                              calc.push(0);
                          });

                          Then("the result is {double}", (Double expected) -> {
                              log.record(expected);
                          });
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }

                      static class Log {
                          void record(Object o) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;
                  import io.cucumber.java.en.Then;

                  public class CalculatorStepDefinitions {

                      private final RpnCalculator calc;
                      private final Log log;

                      public CalculatorStepDefinitions(RpnCalculator calc, Log log) {
                          this.calc = calc;
                          this.log = log;
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          calc.push(0);
                      }

                      @Then("the result is {double}")
                      public void the_result_is_double(Double expected) {
                          log.record(expected);
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }

                      static class Log {
                          void record(Object o) {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainInterfaceWhereLambdaGlueRemains() {
            // The `DataTableType` registration is inherited from `En`, so dropping the interface would take it with it
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          Given("a calculator I just turned on", () -> {
                          });

                          DataTableType(BLANK, (String cell) -> new Money(cell));
                      }

                      static final String BLANK = "[blank]";

                      static class Money {
                          Money(String amount) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;
                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {

                          /*~~(TODO Migrate manually)~~>*/DataTableType(BLANK, (String cell) -> new Money(cell));
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                      }

                      static final String BLANK = "[blank]";

                      static class Money {
                          Money(String amount) {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainLocalVariablesCapturedByLambdas() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions() {
                          RpnCalculator calc = new RpnCalculator();
                          Given("a calculator I just turned on", () -> {
                              calc.push(0);
                          });
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private RpnCalculator calc;

                      public CalculatorStepDefinitions() {
                          calc = new RpnCalculator();
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          calc.push(0);
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainConstructorInjectedDependenciesWithMoreThanOneConstructor() {
            // The field is not final, as only the one constructor assigns it
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions() {
                          this(new RpnCalculator());
                      }

                      public CalculatorStepDefinitions(RpnCalculator calc) {
                          Given("a calculator I just turned on", () -> {
                              calc.push(0);
                          });
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private RpnCalculator calc;

                      public CalculatorStepDefinitions() {
                          this(new RpnCalculator());
                      }

                      public CalculatorStepDefinitions(RpnCalculator calc) {
                          this.calc = calc;
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          calc.push(0);
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void flagCapturedLocalVariablesThatCannotBecomeFields() {
            // Two variables declared in one statement have no one type to declare a field with
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions() {
                          int a = 1, b = 2;
                          Given("a calculator I just turned on", () -> {
                              System.out.println(a + b);
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      /*~~(TODO Migrate manually)~~>*/public CalculatorStepDefinitions() {
                          int a = 1, b = 2;
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          System.out.println(a + b);
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void flagCapturedLocalVariablesDeclaredInANestedScope() {
            // A field assigned where the declaration stood would only be assigned when that scope is entered
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions(boolean flag) {
                          if (flag) {
                              RpnCalculator calc = new RpnCalculator();
                              Given("a calculator I just turned on", () -> {
                                  calc.push(0);
                              });
                          }
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private final boolean flag;

                      /*~~(TODO Migrate manually)~~>*/public CalculatorStepDefinitions(boolean flag) {
                          if (flag) {
                              RpnCalculator calc = new RpnCalculator();
                          }
                          this.flag = flag;
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          calc.push(0);
                      }

                      static class RpnCalculator {
                          void push(Object o) {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void leaveLocalVariablesSharingANameWithAnInvokedMethodAlone() {
            // The migrated body calls `service.list()`; the local `list` is not what it closed over
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  import java.util.ArrayList;
                  import java.util.List;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions(Service service) {
                          List<String> list = new ArrayList<>();
                          list.add("not what the lambda closed over");
                          Given("a calculator I just turned on", () -> {
                              service.list();
                          });
                      }

                      static class Service {
                          void list() {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  import java.util.ArrayList;
                  import java.util.List;

                  public class CalculatorStepDefinitions {

                      private final Service service;

                      public CalculatorStepDefinitions(Service service) {
                          List<String> list = new ArrayList<>();
                          list.add("not what the lambda closed over");
                          this.service = service;
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          service.list();
                      }

                      static class Service {
                          void list() {
                          }
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainArrayLocalVariablesCapturedByLambdas() {
            // The dimensions declared after the variable name belong to the type the field is declared with
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      public CalculatorStepDefinitions() {
                          int values[] = new int[2];
                          Given("a calculator I just turned on", () -> {
                              System.out.println(values[0]);
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private int[] values;

                      public CalculatorStepDefinitions() {
                          values = new int[2];
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          System.out.println(values[0]);
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void leaveLocalVariablesFromAnotherScopeAlone() {
            // The local shares a name with the field the migrated method uses, but is not what the lambda closed over
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      private int total;

                      public CalculatorStepDefinitions() {
                          {
                              int total = 3;
                              System.out.println(total);
                          }
                          Given("a calculator I just turned on", () -> {
                              System.out.println(total);
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private int total;

                      public CalculatorStepDefinitions() {
                          int total = 3;
                          System.out.println(total);
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          System.out.println(total);
                      }
                  }
                  """));
        }

        @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void leaveLocalVariablesShadowedByTheFieldTheLambdaUsedAlone() {
            // The migrated body reads the field as `this.total`, so the local of that name is not what it closed over
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {

                      private int total;

                      public CalculatorStepDefinitions() {
                          int total = 3;
                          System.out.println(total);
                          Given("a calculator I just turned on", () -> {
                              System.out.println(this.total);
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      private int total;

                      public CalculatorStepDefinitions() {
                          int total = 3;
                          System.out.println(total);
                      }

                      @Given("a calculator I just turned on")
                      public void a_calculator_i_just_turned_on() {
                          System.out.println(this.total);
                      }
                  }
                  """));
        }

        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void methodInvocationsOutsideConstructor() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      private int cakes = 0;

                      public CalculatorStepDefinitions() {
                          delegated();
                      }

                      private void delegated() {
                          Given("{int} cakes", (Integer i) -> {
                              cakes = i;
                          });
                      }
                  }""",
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {
                      private int cakes = 0;

                      public CalculatorStepDefinitions() {
                          delegated();
                      }

                      @Given("{int} cakes")
                      public void int_cakes(Integer i) {
                          cakes = i;
                      }

                      private void delegated() {
                      }
                  }"""));
        }

        @Test
        void retainWhitespaceAndCommentsInLambdaBody() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          Given("{int} plus {int}", (Integer a, Integer b) -> {

                              // Lambda body comment
                              System.out.println(a + b);
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      @Given("{int} plus {int}")
                      public void int_plus_int(Integer a, Integer b) {

                          // Lambda body comment
                          System.out.println(a + b);
                      }
                  }
                  """));
        }

        @Test
        void retainThrowsException() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          Given("a thrown exception", () -> {
                              throw new Exception();
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.en.Given;

                  public class CalculatorStepDefinitions {

                      @Given("a thrown exception")
                      public void a_thrown_exception() throws Exception {
                          throw new Exception();
                      }
                  }
                  """));
        }

        @Test
        void replaceWhenNotUsingStringConstant() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          String expression = "{int} plus {int}";
                          Given(expression, (Integer a, Integer b) -> {
                              int c = a + b;
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          String expression = "{int} plus {int}";
                          /*~~(TODO Migrate manually)~~>*/Given(expression, (Integer a, Integer b) -> {
                              int c = a + b;
                          });
                      }
                  }
                  """));
        }

        @Test
        void replaceWhenUsingStringConstant() {
            // For simplicity, we only replace when using a String literal for
            // now
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      private static final String expression = "{int} plus {int}";
                      public CalculatorStepDefinitions() {
                          Given(expression, (Integer a, Integer b) -> {
                              int c = a + b;
                          });
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      private static final String expression = "{int} plus {int}";
                      public CalculatorStepDefinitions() {
                          /*~~(TODO Migrate manually)~~>*/Given(expression, (Integer a, Integer b) -> {
                              int c = a + b;
                          });
                      }
                  }
                  """));
        }

        @Test
        void replaceMethodReference() {
            // For simplicity, we only replace when using lambda for now
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          Given("{int} plus {int}", Integer::sum);
                      }
                  }""", """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class CalculatorStepDefinitions implements En {
                      public CalculatorStepDefinitions() {
                          /*~~(TODO Migrate manually)~~>*/Given("{int} plus {int}", Integer::sum);
                      }
                  }
                  """));
        }

    }

    @Nested
    class HookMigration {
        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void cucumberJava8Hooks() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;
                  import io.cucumber.java8.Scenario;
                  import io.cucumber.java8.Status;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          Before(() -> {
                              a = 0;
                          });

                          Before("abc", () -> a = 0);

                          Before("not abc", 0, () -> {
                              a = 0;
                          });

                          Before(1, () -> {
                              a = 0;
                          });

                          Before(2, scn -> {
                              a = 0;
                          });

                          After((Scenario scn) -> {
                              if (scn.getStatus() == Status.FAILED) {
                                  scn.log("after scenario");
                              }
                          });

                          After("abc", (Scenario scn) -> {
                              scn.log("after scenario");
                          });

                          AfterStep(scn -> {
                              a = 0;
                          });
                      }

                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.*;

                  public class HookStepDefinitions {

                      private int a;

                      @Before
                      public void before() {
                          a = 0;
                      }

                      @Before("abc")
                      public void before_tag_abc() {
                          a = 0;
                      }

                      @Before(order = 0, value = "not abc")
                      public void before_tag_not_abc_order_0() {
                          a = 0;
                      }

                      @Before(order = 1)
                      public void before_order_1() {
                          a = 0;
                      }

                      @Before(order = 2)
                      public void before_order_2(Scenario scn) {
                          a = 0;
                      }

                      @After
                      public void after(Scenario scn) {
                          if (scn.getStatus() == Status.FAILED) {
                              scn.log("after scenario");
                          }
                      }

                      @After("abc")
                      public void after_tag_abc(Scenario scn) {
                          scn.log("after scenario");
                      }

                      @AfterStep
                      public void afterStep(Scenario scn) {
                          a = 0;
                      }

                  }
                  """));
        }

        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void importScenarioWhereTheHookBodyLeavesItImplicit() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          After(scn -> {
                              a = 0;
                          });
                      }

                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.After;
                  import io.cucumber.java.Scenario;

                  public class HookStepDefinitions {

                      private int a;

                      @After
                      public void after(Scenario scn) {
                          a = 0;
                      }

                  }
                  """));
        }

        @Test
        void convertAnonymousClasses() {
            // For simplicity, anonymous classes are not converted for now; it's
            // not how cucumber-java8 usage was intended
            rewriteRun(
              spec -> spec.cycles(2),
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;
                  import io.cucumber.java8.HookBody;
                  import io.cucumber.java8.HookNoArgsBody;
                  import io.cucumber.java8.Scenario;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          Before(new HookNoArgsBody() {
                              @Override
                              public void accept() {
                                  a = 0;
                              }
                          });

                          Before(new HookBody() {
                              @Override
                              public void accept(Scenario scenario) {
                                  a = 0;
                              }
                          });
                      }

                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java8.En;
                  import io.cucumber.java8.HookBody;
                  import io.cucumber.java8.HookNoArgsBody;
                  import io.cucumber.java8.Scenario;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          /*~~(TODO Migrate manually)~~>*/Before(new HookNoArgsBody() {
                              @Override
                              public void accept() {
                                  a = 0;
                              }
                          });

                          /*~~(TODO Migrate manually)~~>*/Before(new HookBody() {
                              @Override
                              public void accept(Scenario scenario) {
                                  a = 0;
                              }
                          });
                      }

                  }
                  """));
        }

        @Test
        void convertMethodReference() {
            // Not converted yet; the referred method can potentially be
            // annotated and be made public
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          Before(this::connect);
                      }

                      private void connect() {
                      }

                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class HookStepDefinitions implements En {

                      private int a;

                      public HookStepDefinitions() {
                          /*~~(TODO Migrate manually)~~>*/Before(this::connect);
                      }

                      private void connect() {
                      }

                  }
                  """));
        }
    }

    @Nested
    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/2")
    class TypeDefinitionMigration {

        @Test
        void dataTableTypes() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.datatable.DataTable;
                  import io.cucumber.java8.En;

                  import java.util.List;
                  import java.util.Map;

                  public class TypeDefinitions implements En {

                      public TypeDefinitions() {
                          DataTableType((Map<String, String> entry) -> new Author(entry.get("name"), entry.get("birthDate")));

                          DataTableType("[blank]", (List<String> row) -> {
                              return new Author(row.get(0), row.get(1));
                          });

                          DataTableType((String cell) -> new Title(cell));

                          DataTableType((DataTable table) -> new Library(table.asMaps()));
                      }

                      static class Author {
                          Author(String name, String birthDate) {
                          }
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }

                      static class Library {
                          Library(Object entries) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.datatable.DataTable;
                  import io.cucumber.java.DataTableType;

                  import java.util.List;
                  import java.util.Map;

                  public class TypeDefinitions {

                      @DataTableType
                      public Author author(Map<String, String> entry) {
                          return new Author(entry.get("name"), entry.get("birthDate"));
                      }

                      @DataTableType(replaceWithEmptyString = "[blank]")
                      public Author author2(List<String> row) {
                          return new Author(row.get(0), row.get(1));
                      }

                      @DataTableType
                      public Title title(String cell) {
                          return new Title(cell);
                      }

                      @DataTableType
                      public Library library(DataTable table) {
                          return new Library(table.asMaps());
                      }

                      static class Author {
                          Author(String name, String birthDate) {
                          }
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }

                      static class Library {
                          Library(Object entries) {
                          }
                      }
                  }
                  """));
        }

        @Test
        void parameterTypeAndDocStringType() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class TypeDefinitions implements En {

                      public TypeDefinitions() {
                          ParameterType("iso8601Date", "\\\\d{4}-\\\\d{2}-\\\\d{2}", (String date) -> new Title(date));

                          ParameterType("amount in {word}", "(\\\\d+) (\\\\w+)", (String amount, String currency) -> new Title(amount + currency));

                          DocStringType("json", (String docString) -> new Title(docString));
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.DocStringType;
                  import io.cucumber.java.ParameterType;

                  public class TypeDefinitions {

                      @ParameterType("\\\\d{4}-\\\\d{2}-\\\\d{2}")
                      public Title iso8601Date(String date) {
                          return new Title(date);
                      }

                      @ParameterType(name = "amount in {word}", value = "(\\\\d+) (\\\\w+)")
                      public Title amountinword(String amount, String currency) {
                          return new Title(amount + currency);
                      }

                      @DocStringType(contentType = "json")
                      public Title title(String docString) {
                          return new Title(docString);
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """));
        }

        @Test
        void defaultTransformers() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  import java.lang.reflect.Type;
                  import java.util.Map;

                  public class TypeDefinitions implements En {

                      private final ObjectMapper mapper = new ObjectMapper();

                      public TypeDefinitions() {
                          DefaultParameterTransformer((String fromValue, Type toValueType) -> mapper.convert(fromValue, toValueType));

                          DefaultDataTableCellTransformer((String fromValue, Type toValueType) -> mapper.convert(fromValue, toValueType));

                          DefaultDataTableEntryTransformer("[blank]", (Map<String, String> fromValue, Type toValueType) -> mapper.convert(fromValue, toValueType));
                      }

                      static class ObjectMapper {
                          Object convert(Object fromValue, Type toValueType) {
                              return fromValue;
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.DefaultDataTableCellTransformer;
                  import io.cucumber.java.DefaultDataTableEntryTransformer;
                  import io.cucumber.java.DefaultParameterTransformer;

                  import java.lang.reflect.Type;
                  import java.util.Map;

                  public class TypeDefinitions {

                      private final ObjectMapper mapper = new ObjectMapper();

                      @DefaultParameterTransformer
                      public Object defaultParameterTransformer(String fromValue, Type toValueType) {
                          return mapper.convert(fromValue, toValueType);
                      }

                      @DefaultDataTableCellTransformer
                      public Object defaultDataTableCellTransformer(String fromValue, Type toValueType) {
                          return mapper.convert(fromValue, toValueType);
                      }

                      @DefaultDataTableEntryTransformer(replaceWithEmptyString = "[blank]")
                      public Object defaultDataTableEntryTransformer(Map<String, String> fromValue, Type toValueType) {
                          return mapper.convert(fromValue, toValueType);
                      }

                      static class ObjectMapper {
                          Object convert(Object fromValue, Type toValueType) {
                              return fromValue;
                          }
                      }
                  }
                  """));
        }

        @Test
        void implicitLambdaParameterTypes() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class TypeDefinitions implements En {

                      public TypeDefinitions() {
                          DocStringType("json", docString -> new Title(docString));

                          DefaultParameterTransformer((fromValue, toValueType) -> fromValue);
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.DefaultParameterTransformer;
                  import io.cucumber.java.DocStringType;

                  import java.lang.reflect.Type;

                  public class TypeDefinitions {

                      @DocStringType(contentType = "json")
                      public Title title(String docString) {
                          return new Title(docString);
                      }

                      @DefaultParameterTransformer
                      public Object defaultParameterTransformer(String fromValue, Type toValueType) {
                          return fromValue;
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """));
        }

        @Test
        void importTheTypeARegistrationIsKeyedBy() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.model;

                  import java.util.Map;

                  public class Authors {
                      public static Author of(Map<String, String> entry) {
                          return new Author();
                      }

                      public static class Author {
                      }
                  }
                  """
              ),
              // language=java
              java(
                """
                  package com.example.app;

                  import com.example.model.Authors;
                  import io.cucumber.java8.En;

                  import java.util.Map;

                  public class TypeDefinitions implements En {

                      public TypeDefinitions() {
                          DataTableType((Map<String, String> entry) -> Authors.of(entry));
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import com.example.model.Authors;
                  import io.cucumber.java.DataTableType;

                  import java.util.Map;

                  public class TypeDefinitions {

                      @DataTableType
                      public Authors.Author author(Map<String, String> entry) {
                          return Authors.of(entry);
                      }
                  }
                  """));
        }

        @Test
        void flagRegistrationsTheAnnotationCannotExpress() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class TypeDefinitions implements En {

                      static final String BLANK = "[blank]";

                      public TypeDefinitions() {
                          DataTableType(BLANK, (String cell) -> new Title(cell));

                          DocStringType("json", Title::new);
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java8.En;

                  public class TypeDefinitions implements En {

                      static final String BLANK = "[blank]";

                      public TypeDefinitions() {
                          /*~~(TODO Migrate manually)~~>*/DataTableType(BLANK, (String cell) -> new Title(cell));

                          /*~~(TODO Migrate manually)~~>*/DocStringType("json", Title::new);
                      }

                      static class Title {
                          Title(String value) {
                          }
                      }
                  }
                  """));
        }

        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void stepsHooksAndTypeDefinitionsInOneClass() {
            rewriteRun(
              // language=java
              java(
                """
                  package com.example.app;

                  import io.cucumber.java8.En;
                  import io.cucumber.java8.Scenario;

                  import java.util.Map;

                  public class TypeDefinitions implements En {

                      private Author author;

                      public TypeDefinitions() {
                          Before((Scenario scn) -> {
                              author = null;
                          });

                          DataTableType((Map<String, String> entry) -> new Author(entry.get("name")));

                          Given("an author {author}", (Author a) -> {
                              author = a;
                          });
                      }

                      static class Author {
                          Author(String name) {
                          }
                      }
                  }
                  """,
                """
                  package com.example.app;

                  import io.cucumber.java.Before;
                  import io.cucumber.java.DataTableType;
                  import io.cucumber.java.Scenario;
                  import io.cucumber.java.en.Given;

                  import java.util.Map;

                  public class TypeDefinitions {

                      private Author author;

                      @Before
                      public void before(Scenario scn) {
                          author = null;
                      }

                      @Given("an author {author}")
                      public void an_author_author(Author a) {
                          author = a;
                      }

                      @DataTableType
                      public Author author(Map<String, String> entry) {
                          return new Author(entry.get("name"));
                      }

                      static class Author {
                          Author(String name) {
                          }
                      }
                  }
                  """));
        }
    }

    @Nested
    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    class DependencyMigration {

        @Test
        void dropCucumberJava8OnceAllGlueIsMigrated() {
            rewriteRun(
              mavenProject("app",
                srcTestJava(
                  // language=java
                  java(
                    """
                      package com.example.app;

                      import io.cucumber.java8.En;

                      public class CalculatorStepDefinitions implements En {
                          public CalculatorStepDefinitions() {
                              Given("a calculator I just turned on", () -> {
                              });

                              DataTableType((String cell) -> new Money(cell));
                          }

                          static class Money {
                              Money(String amount) {
                              }
                          }
                      }
                      """,
                    """
                      package com.example.app;

                      import io.cucumber.java.DataTableType;
                      import io.cucumber.java.en.Given;

                      public class CalculatorStepDefinitions {

                          @Given("a calculator I just turned on")
                          public void a_calculator_i_just_turned_on() {
                          }

                          @DataTableType
                          public Money money(String cell) {
                              return new Money(cell);
                          }

                          static class Money {
                              Money(String amount) {
                              }
                          }
                      }
                      """)),
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
                                <artifactId>cucumber-java8</artifactId>
                                <version>7.34.6</version>
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
                                <version>7.34.6</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """)));
        }

        @SuppressWarnings("CodeBlock2Expr")
        @Test
        void retainCucumberJava8WhereLambdaGlueRemains() {
            rewriteRun(
              // Adding `cucumber-java` keys off the `cucumber-java8` glue that is there to begin with, so it lands
              // in the same cycle that migrates the glue; only dropping `cucumber-java8` waits on the cycle after
              spec -> spec.expectedCyclesThatMakeChanges(1),
              mavenProject("app",
                srcTestJava(
                  // language=java
                  java(
                    """
                      package com.example.app;

                      import io.cucumber.java8.En;

                      public class CalculatorStepDefinitions implements En {
                          public CalculatorStepDefinitions() {
                              Given("a calculator I just turned on", () -> {
                              });

                              DataTableType(BLANK, (String cell) -> new Money(cell));
                          }

                          static final String BLANK = "[blank]";

                          static class Money {
                              Money(String amount) {
                              }
                          }
                      }
                      """,
                    """
                      package com.example.app;

                      import io.cucumber.java.en.Given;
                      import io.cucumber.java8.En;

                      public class CalculatorStepDefinitions implements En {
                          public CalculatorStepDefinitions() {

                              /*~~(TODO Migrate manually)~~>*/DataTableType(BLANK, (String cell) -> new Money(cell));
                          }

                          @Given("a calculator I just turned on")
                          public void a_calculator_i_just_turned_on() {
                          }

                          static final String BLANK = "[blank]";

                          static class Money {
                              Money(String amount) {
                              }
                          }
                      }
                      """)),
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
                                <artifactId>cucumber-java8</artifactId>
                                <version>7.34.6</version>
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
                                <version>7.34.6</version>
                                <scope>test</scope>
                            </dependency>
                            <dependency>
                                <groupId>io.cucumber</groupId>
                                <artifactId>cucumber-java8</artifactId>
                                <version>7.34.6</version>
                                <scope>test</scope>
                            </dependency>
                        </dependencies>
                    </project>
                    """)));
        }
    }
}
