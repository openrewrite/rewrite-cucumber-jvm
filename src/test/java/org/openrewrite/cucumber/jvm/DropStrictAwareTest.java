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
class DropStrictAwareTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new DropStrictAware())
          .parser(JavaParser.fromJavaVersion().classpathFromResources(new InMemoryExecutionContext(), "cucumber-plugin"));
    }

    @DocumentExample
    @Test
    void dropStrictAwareNextToAnotherPlugin() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.ConcurrentEventListener;
              import io.cucumber.plugin.StrictAware;
              import io.cucumber.plugin.event.EventPublisher;

              public class ReportPlugin implements ConcurrentEventListener, StrictAware {

                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }

                  @Override
                  public void setStrict(boolean strict) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.plugin.ConcurrentEventListener;
              import io.cucumber.plugin.event.EventPublisher;

              public class ReportPlugin implements ConcurrentEventListener {

                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }
              }
              """
          )
        );
    }

    @Test
    void replaceWithPluginWhenNothingElseIsAPlugin() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.StrictAware;

              public class StrictPlugin implements StrictAware, Runnable {

                  @Override
                  public void setStrict(boolean strict) {
                  }

                  @Override
                  public void run() {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.plugin.Plugin;

              public class StrictPlugin implements Plugin, Runnable {

                  @Override
                  public void run() {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepUnrelatedSetStrict() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.ColorAware;
              import io.cucumber.plugin.StrictAware;

              public class ReportPlugin implements StrictAware, ColorAware {

                  @Override
                  public void setStrict(boolean strict) {
                  }

                  @Override
                  public void setMonochrome(boolean monochrome) {
                  }
              }

              class Settings {
                  void setStrict(boolean strict) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.plugin.ColorAware;

              public class ReportPlugin implements ColorAware {

                  @Override
                  public void setMonochrome(boolean monochrome) {
                  }
              }

              class Settings {
                  void setStrict(boolean strict) {
                  }
              }
              """
          )
        );
    }

    @Test
    void keepPluginSuperclassInsteadOfAddingPlugin() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.ConcurrentEventListener;
              import io.cucumber.plugin.event.EventPublisher;

              public abstract class BasePlugin implements ConcurrentEventListener {

                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }
              }
              """
          ),
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.StrictAware;

              public class ReportPlugin extends BasePlugin implements StrictAware {

                  @Override
                  public void setStrict(boolean strict) {
                  }
              }
              """,
            """
              package com.example.app;

              public class ReportPlugin extends BasePlugin {
              }
              """
          )
        );
    }

    @Test
    void dropSetStrictCalls() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.ConcurrentEventListener;
              import io.cucumber.plugin.StrictAware;
              import io.cucumber.plugin.event.EventPublisher;

              public class ReportPlugin implements ConcurrentEventListener, StrictAware {

                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }

                  @Override
                  public void setStrict(boolean strict) {
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.plugin.ConcurrentEventListener;
              import io.cucumber.plugin.event.EventPublisher;

              public class ReportPlugin implements ConcurrentEventListener {

                  @Override
                  public void setEventPublisher(EventPublisher publisher) {
                  }
              }
              """
          ),
          // language=java
          java(
            """
              package com.example.app;

              class ReportPluginTest {
                  void test() {
                      ReportPlugin plugin = new ReportPlugin();
                      plugin.setStrict(true);
                  }
              }
              """,
            """
              package com.example.app;

              class ReportPluginTest {
                  void test() {
                      ReportPlugin plugin = new ReportPlugin();
                  }
              }
              """
          )
        );
    }

    @Test
    void keepSetStrictOfAnonymousStrictAware() {
        rewriteRun(
          // language=java
          java(
            """
              package com.example.app;

              import io.cucumber.plugin.StrictAware;

              class Plugins {
                  StrictAware plugin = new StrictAware() {
                      @Override
                      public void setStrict(boolean strict) {
                      }
                  };
              }
              """
          )
        );
    }
}
