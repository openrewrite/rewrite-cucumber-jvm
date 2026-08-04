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

class TypeRegistryConfigurerToAnnotationsTest implements RewriteTest {

    //language=java
    private static final String AUTHOR = """
      package com.example.app;

      public class Author {
          private final String name;

          public Author(String name) {
              this.name = name;
          }

          public String getName() {
              return name;
          }
      }
      """;

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new TypeRegistryConfigurerToAnnotations())
                .parser(cucumberParser("cucumber-core-6.11.0"));
    }

    /**
     * The recipe migrates onto types from the current release, but away from `TypeRegistryConfigurer`, which no
     * release still ships; take that one from a type table. Note that Cucumber-JVM 4.x carries both the
     * `cucumber.api` and the `io.cucumber.core.api` variant, so only ever put one release on the classpath.
     */
    private static JavaParser.Builder<?, ?> cucumberParser(String cucumberCore) {
        return JavaParser.fromJavaVersion()
                .classpathFromResources(new InMemoryExecutionContext(),
                        "cucumber-java-7", "cucumber-expressions", "datatable", "docstring", cucumberCore)
                .dependsOn(AUTHOR);
    }

    @DocumentExample
    @Test
    void parameterTypeAndDataTableTypeBecomeAnnotatedGlueMethods() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;
              import io.cucumber.datatable.DataTableType;

              import java.util.Locale;
              import java.util.Map;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineParameterType(new ParameterType<>(
                              "author", "[A-Z][a-z]+", Author.class, (String name) -> new Author(name)));
                      typeRegistry.defineDataTableType(new DataTableType(
                              Author.class, (Map<String, String> entry) -> new Author(entry.get("name"))));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.DataTableType;
              import io.cucumber.java.ParameterType;

              import java.util.Map;

              public class DataTableConfigurer {
                  @ParameterType("[A-Z][a-z]+")
                  public Author author(String name) {
                      return new Author(name);
                  }

                  @DataTableType
                  public Author author2(Map<String, String> entry) {
                      return new Author(entry.get("name"));
                  }
              }
              """));
    }

    @Test
    void docStringTypeWithBlockBodiedLambda() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.docstring.DocStringType;

              import java.util.Locale;

              public class DocStringConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDocStringType(new DocStringType(Author.class, "author", (String docString) -> {
                          String trimmed = docString.trim();
                          return new Author(trimmed);
                      }));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.DocStringType;

              public class DocStringConfigurer {
                  @DocStringType(contentType = "author")
                  public Author author(String docString) {
                      String trimmed = docString.trim();
                      return new Author(trimmed);
                  }
              }
              """));
    }

    @Test
    void takeParameterTypesFromCastTransformer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.datatable.DataTableType;
              import io.cucumber.datatable.TableEntryTransformer;

              import java.util.Locale;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDataTableType(new DataTableType(Author.class,
                              (TableEntryTransformer<Author>) entry -> new Author(entry.get("name"))));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.DataTableType;

              import java.util.Map;

              public class DataTableConfigurer {
                  @DataTableType
                  public Author author(Map<String, String> entry) {
                      return new Author(entry.get("name"));
                  }
              }
              """));
    }

    @Test
    void takeParameterTypeFromDocStringTransformer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.docstring.DocStringType;

              import java.util.Locale;

              public class DocStringConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDocStringType(new DocStringType(Author.class, "author", docString -> new Author(docString)));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.DocStringType;

              public class DocStringConfigurer {
                  @DocStringType(contentType = "author")
                  public Author author(String docString) {
                      return new Author(docString);
                  }
              }
              """));
    }

    @Test
    void convertPreCucumber5TypeRegistryConfigurer() {
        rewriteRun(
          spec -> spec.parser(cucumberParser("cucumber-core-4.8.1")),
          //language=java
          java(
            """
              package com.example.app;

              import cucumber.api.TypeRegistry;
              import cucumber.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;

              import java.util.Locale;

              public class ParameterTypeConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineParameterType(new ParameterType<>(
                              "author", "[A-Z][a-z]+", Author.class, (String name) -> new Author(name)));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.ParameterType;

              public class ParameterTypeConfigurer {
                  @ParameterType("[A-Z][a-z]+")
                  public Author author(String name) {
                      return new Author(name);
                  }
              }
              """));
    }

    @Test
    void retainClassesThatCanNotBeConvertedInFull() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;
              import io.cucumber.cucumberexpressions.Transformer;

              import java.util.Locale;

              public class ParameterTypeConfigurer implements TypeRegistryConfigurer {
                  private static final Transformer<Author> TRANSFORMER = Author::new;

                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineParameterType(new ParameterType<>(
                              "author", "[A-Z][a-z]+", Author.class, TRANSFORMER));
                  }
              }
              """));
    }

    @Test
    void retainClassesThatDoNotImplementTypeRegistryConfigurer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;

              public class NotAConfigurer {
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                  }
              }
              """));
    }
}
