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
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

class CucumberApiToIoCucumberTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.CucumberApiToIoCucumber")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "cucumber-core-4.8.1",
              "cucumber-java-4.8.1",
              "cucumber-junit-4.8.1",
              "cucumber-testng-4.8.1"));
    }

    @DocumentExample
    @Test
    void typeRegistryConfigurerMovesToCoreApi() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.TypeRegistry;
              import cucumber.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                  }
              }
              """
          ));
    }

    @Test
    void glueTypesMoveToIoCucumberJava() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.PendingException;
              import cucumber.api.Scenario;
              import cucumber.api.java.Before;
              import cucumber.api.java.en.Given;

              public class StepDefinitions {
                  @Before
                  public void before(Scenario scenario) {
                  }

                  @Given("a step")
                  public void aStep() {
                      throw new PendingException();
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.Before;
              import io.cucumber.java.en.Given;
              import io.cucumber.java.PendingException;
              import io.cucumber.java.Scenario;

              public class StepDefinitions {
                  @Before
                  public void before(Scenario scenario) {
                  }

                  @Given("a step")
                  public void aStep() {
                      throw new PendingException();
                  }
              }
              """
          ));
    }

    @Test
    void junitRunnerMovesToIoCucumberJunit() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.CucumberOptions;
              import cucumber.api.SnippetType;
              import cucumber.api.junit.Cucumber;

              @CucumberOptions(snippets = SnippetType.CAMELCASE)
              public class RunCucumberTest {
                  Class<?> runner = Cucumber.class;
              }
              """,
            """
              package com.example.app;

              import io.cucumber.junit.Cucumber;
              import io.cucumber.junit.CucumberOptions;
              import io.cucumber.junit.CucumberOptions.SnippetType;

              @CucumberOptions(snippets = CucumberOptions.SnippetType.CAMELCASE)
              public class RunCucumberTest {
                  Class<?> runner = Cucumber.class;
              }
              """
          ));
    }

    @Test
    void ngRunnerMovesToIoCucumberTestNg() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.CucumberOptions;
              import cucumber.api.testng.AbstractTestNGCucumberTests;

              @CucumberOptions(features = "classpath:features")
              public class RunCucumberTest extends AbstractTestNGCucumberTests {
              }
              """,
            """
              package com.example.app;

              import io.cucumber.testng.AbstractTestNGCucumberTests;
              import io.cucumber.testng.CucumberOptions;

              @CucumberOptions(features = "classpath:features")
              public class RunCucumberTest extends AbstractTestNGCucumberTests {
              }
              """
          ));
    }

    @Test
    void pluginTypesMoveToIoCucumberPlugin() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.SummaryPrinter;
              import cucumber.api.event.EventListener;
              import cucumber.api.event.EventPublisher;
              import cucumber.api.event.TestRunFinished;
              import cucumber.api.formatter.ColorAware;

              public class MyFormatter implements EventListener, ColorAware, SummaryPrinter {
                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }

                  @Override
                  public void setMonochrome(boolean monochrome) {
                  }

                  public void finished(TestRunFinished event) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.plugin.ColorAware;
              import io.cucumber.plugin.SummaryPrinter;
              import io.cucumber.plugin.event.EventPublisher;
              import io.cucumber.plugin.event.TestRunFinished;
              import io.cucumber.plugin.EventListener;

              public class MyFormatter implements EventListener, ColorAware, SummaryPrinter {
                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }

                  @Override
                  public void setMonochrome(boolean monochrome) {
                  }

                  public void finished(TestRunFinished event) {
                  }
              }
              """
          ));
    }

    @Test
    void objectFactoryMovesToCoreBackend() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.java.ObjectFactory;

              public abstract class MyObjectFactory implements ObjectFactory {
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.backend.ObjectFactory;

              public abstract class MyObjectFactory implements ObjectFactory {
              }
              """
          ));
    }

    @Test
    void dataTableMovesToIoCucumberDatatable() {
        rewriteRun(
          // `cucumber.api.DataTable` was gone by 4.x, and 1.2.5 redeclares most of the types used above
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-core-1.2.5")),
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.DataTable;

              public class StepDefinitions {
                  public void aStep(DataTable table) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.datatable.DataTable;

              public class StepDefinitions {
                  public void aStep(DataTable table) {
                  }
              }
              """
          ));
    }

    @Test
    void cliMainMovesToCoreCli() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.cli.Main;

              public class Runner {
                  public static void main(String[] args) {
                      Main.main(args);
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.cli.Main;

              public class Runner {
                  public static void main(String[] args) {
                      Main.main(args);
                  }
              }
              """
          ));
    }
}
