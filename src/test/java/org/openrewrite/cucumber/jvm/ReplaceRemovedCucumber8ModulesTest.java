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
import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/63")
class ReplaceRemovedCucumber8ModulesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.ReplaceRemovedCucumber8Modules")
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "cucumber-junit-7", "cucumber-junit-platform-engine-7", "junit-platform-suite-api-1")
            // The type tables hold neither the removed nor the replacement modules
            //language=java
            .dependsOn(
              """
                package io.cucumber.cdi2;
                public final class Cdi2Factory implements io.cucumber.core.backend.ObjectFactory {}
                """,
              """
                package io.cucumber.jakarta.cdi;
                public final class CdiJakartaFactory implements io.cucumber.core.backend.ObjectFactory {}
                """,
              """
                package io.cucumber.core.backend;
                public interface ObjectFactory {}
                """
            ));
    }

    @DocumentExample
    @Test
    void sharedVersionProperty() {
        rewriteRun(
          //language=xml
          pomXml(
            """
              <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <properties>
                      <cucumber.version>7.34.3</cucumber.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-cdi2</artifactId>
                          <version>${cucumber.version}</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-openejb</artifactId>
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
                      <cucumber.version>7.34.3</cucumber.version>
                  </properties>
                  <dependencies>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jakarta-cdi</artifactId>
                          <version>${cucumber.version}</version>
                          <scope>test</scope>
                      </dependency>
                      <dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-jakarta-openejb</artifactId>
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
    void objectFactoryProperty() {
        rewriteRun(
          properties(
            "cucumber.object-factory=io.cucumber.cdi2.Cdi2Factory",
            "cucumber.object-factory=io.cucumber.jakarta.cdi.CdiJakartaFactory",
            spec -> spec.path("src/test/resources/cucumber.properties")
          ),
          properties(
            "cucumber.object-factory=io.cucumber.openejb.OpenEJBObjectFactory",
            "cucumber.object-factory=io.cucumber.jakarta.openejb.OpenEJBObjectFactory",
            spec -> spec.path("src/test/resources/junit-platform.properties")
          )
        );
    }

    @Test
    void otherObjectFactory() {
        rewriteRun(
          properties(
            "cucumber.object-factory=io.cucumber.spring.SpringFactory",
            spec -> spec.path("src/test/resources/cucumber.properties")
          )
        );
    }

    @Test
    void objectFactoryInCucumberOptions() {
        rewriteRun(
          //language=java
          java(
            """
              import io.cucumber.cdi2.Cdi2Factory;
              import io.cucumber.junit.CucumberOptions;

              @CucumberOptions(objectFactory = Cdi2Factory.class)
              public class RunCucumberTest {
              }
              """,
            """
              import io.cucumber.jakarta.cdi.CdiJakartaFactory;
              import io.cucumber.junit.CucumberOptions;

              @CucumberOptions(objectFactory = CdiJakartaFactory.class)
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void objectFactoryInConfigurationParameter() {
        rewriteRun(
          //language=java
          java(
            """
              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

              @Suite
              @ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.openejb.OpenEJBObjectFactory")
              public class RunCucumberTest {
              }
              """,
            """
              import org.junit.platform.suite.api.ConfigurationParameter;
              import org.junit.platform.suite.api.Suite;

              import static io.cucumber.junit.platform.engine.Constants.OBJECT_FACTORY_PROPERTY_NAME;

              @Suite
              @ConfigurationParameter(key = OBJECT_FACTORY_PROPERTY_NAME, value = "io.cucumber.jakarta.openejb.OpenEJBObjectFactory")
              public class RunCucumberTest {
              }
              """
          )
        );
    }

    @Test
    void flagDeltaspike() {
        rewriteRun(
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
                          <artifactId>cucumber-deltaspike</artifactId>
                          <version>7.34.3</version>
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
                      <!--~~(io.cucumber:cucumber-deltaspike:7.34.3)~~>--><dependency>
                          <groupId>io.cucumber</groupId>
                          <artifactId>cucumber-deltaspike</artifactId>
                          <version>7.34.3</version>
                          <scope>test</scope>
                      </dependency>
                  </dependencies>
              </project>
              """
          )
        );
    }
}
