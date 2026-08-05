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
import org.openrewrite.Issue;
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
        // The recipe migrates onto types from the current release, but away from `TypeRegistryConfigurer`,
        // which no release still ships, so that one comes from a type table
        spec.recipe(new TypeRegistryConfigurerToAnnotations())
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-java-7",
              "cucumber-expressions", "datatable", "docstring", "cucumber-core-6.11.0")
            .dependsOn(AUTHOR));
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
          // Cucumber-JVM 4.x carries both the `cucumber.api` and the `io.cucumber.core.api` variant, so swap
          // the whole release out rather than adding it alongside the one the other tests parse against
          spec -> spec.parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(), "cucumber-java-7",
              "cucumber-expressions", "datatable", "docstring", "cucumber-core-4.8.1")
            .dependsOn(AUTHOR)),
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
              """,
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
                      // TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand
                      typeRegistry.defineParameterType(new ParameterType<>(
                              "author", "[A-Z][a-z]+", Author.class, TRANSFORMER));
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void flagOnlyTheRegistrationThatBlockedTheConversion() {
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

              public class AuthorConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDataTableType(new DataTableType(Author.class,
                              (Map<String, String> entry) -> new Author(entry.get("name"))));
                      typeRegistry.defineParameterType(authorParameterType());
                  }

                  private ParameterType<Author> authorParameterType() {
                      return new ParameterType<>("author", "[A-Z][a-z]+", Author.class, Author::new);
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;
              import io.cucumber.datatable.DataTableType;

              import java.util.Locale;
              import java.util.Map;

              public class AuthorConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDataTableType(new DataTableType(Author.class,
                              (Map<String, String> entry) -> new Author(entry.get("name"))));
                      // TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand
                      typeRegistry.defineParameterType(authorParameterType());
                  }

                  private ParameterType<Author> authorParameterType() {
                      return new ParameterType<>("author", "[A-Z][a-z]+", Author.class, Author::new);
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void anonymousClassTransformer() {
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
              import java.util.Map;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDataTableType(new DataTableType(Author.class, new TableEntryTransformer<Author>() {
                          @Override
                          public Author transform(Map<String, String> entry) {
                              return new Author(entry.get("name"));
                          }
                      }));
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

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void inlineLocalsHoldingTheTransformerAndTheRegistration() {
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
              import java.util.Map;

              public class DataTableConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      TableEntryTransformer<Author> transformer = new TableEntryTransformer<Author>() {
                          @Override
                          public Author transform(Map<String, String> entry) throws Throwable {
                              return new Author(entry.get("name"));
                          }
                      };
                      DataTableType tableType = new DataTableType(Author.class, transformer);
                      typeRegistry.defineDataTableType(tableType);
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

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void retainLocalsNoRegistrationReads() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
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
                      String unused = register(typeRegistry);
                      typeRegistry.defineDataTableType(new DataTableType(Author.class,
                              (Map<String, String> entry) -> new Author(entry.get("name"))));
                  }

                  private String register(TypeRegistry typeRegistry) {
                      return "registered";
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
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
                      // TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand
                      String unused = register(typeRegistry);
                      typeRegistry.defineDataTableType(new DataTableType(Author.class,
                              (Map<String, String> entry) -> new Author(entry.get("name"))));
                  }

                  private String register(TypeRegistry typeRegistry) {
                      return "registered";
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void constructorReferenceTransformer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;

              import java.util.Locale;

              public class ParameterTypeConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineParameterType(new ParameterType<>("author", "[A-Z][a-z]+", Author.class, Author::new));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.ParameterType;

              public class ParameterTypeConfigurer {
                  @ParameterType("[A-Z][a-z]+")
                  public Author author(String value) {
                      return new Author(value);
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void staticAndBoundMethodReferenceTransformers() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.datatable.DataTableType;
              import io.cucumber.docstring.DocStringType;

              import java.util.Locale;

              public class AuthorConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineDataTableType(new DataTableType(Author.class, AuthorConfigurer::parse));
                      typeRegistry.defineDocStringType(new DocStringType(Author.class, "author", this::read));
                  }

                  static Author parse(String cell) {
                      return new Author(cell);
                  }

                  Author read(String docString) {
                      return new Author(docString.trim());
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.DataTableType;
              import io.cucumber.java.DocStringType;

              public class AuthorConfigurer {

                  static Author parse(String cell) {
                      return new Author(cell);
                  }

                  Author read(String docString) {
                      return new Author(docString.trim());
                  }

                  @DataTableType
                  public Author author(String cell) {
                      return AuthorConfigurer.parse(cell);
                  }

                  @DocStringType(contentType = "author")
                  public Author author2(String docString) {
                      return this.read(docString);
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void unboundMethodReferenceTransformer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
              import io.cucumber.cucumberexpressions.ParameterType;

              import java.util.Locale;

              public class ParameterTypeConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.defineParameterType(new ParameterType<>("trimmed", ".*", String.class, String::trim));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.ParameterType;

              public class ParameterTypeConfigurer {
                  @ParameterType(".*")
                  public String trimmed(String value) {
                      return value.trim();
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void defaultTransformersBecomeAnnotatedGlueMethods() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public class DefaultTransformerConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.setDefaultParameterTransformer((fromValue, toValueType) -> fromValue);
                      typeRegistry.setDefaultDataTableCellTransformer((fromValue, toValueType) -> fromValue);
                      typeRegistry.setDefaultDataTableEntryTransformer((fromValue, toValueType, cellTransformer) -> fromValue);
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

              public class DefaultTransformerConfigurer {
                  @DefaultParameterTransformer
                  public Object defaultParameterTransformer(String fromValue, Type toValueType) {
                      return fromValue;
                  }

                  @DefaultDataTableCellTransformer
                  public Object defaultDataTableCellTransformer(String fromValue, Type toValueType) {
                      return fromValue;
                  }

                  @DefaultDataTableEntryTransformer
                  public Object defaultDataTableEntryTransformer(Map<String, String> fromValue, Type toValueType) {
                      return fromValue;
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void retainDefaultDataTableEntryTransformerThatReadsTheCellTransformer() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public class DefaultTransformerConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      typeRegistry.setDefaultDataTableEntryTransformer(
                              (fromValue, toValueType, cellTransformer) -> cellTransformer.transform(fromValue.toString(), toValueType));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public class DefaultTransformerConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }

                  @Override
                  public void configureTypeRegistry(TypeRegistry typeRegistry) {
                      // TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand
                      typeRegistry.setDefaultDataTableEntryTransformer(
                              (fromValue, toValueType, cellTransformer) -> cellTransformer.transform(fromValue.toString(), toValueType));
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void parameterTypeOverloadsCarryTheirFlagsOntoTheAnnotation() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistry;
              import io.cucumber.core.api.TypeRegistryConfigurer;
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
                              "author", "[A-Z][a-z]+", Author.class, (String name) -> new Author(name), false, true));
                      typeRegistry.defineParameterType(new ParameterType<>(
                              "writer", "[A-Z][a-z]+", Author.class, (String name) -> new Author(name), true, false, true));
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.java.ParameterType;

              public class ParameterTypeConfigurer {
                  @ParameterType(value = "[A-Z][a-z]+", useForSnippets = false, preferForRegexMatch = true)
                  public Author author(String name) {
                      return new Author(name);
                  }

                  @ParameterType(value = "[A-Z][a-z]+", useRegexpMatchAsStrongTypeHint = true)
                  public Author writer(String name) {
                      return new Author(name);
                  }
              }
              """));
    }

    @Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/47")
    @Test
    void flagClassesThatDoNotDeclareConfigureTypeRegistry() {
        rewriteRun(
          //language=java
          java(
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              public abstract class BaseConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
                  }
              }
              """,
            """
              package com.example.app;

              import io.cucumber.core.api.TypeRegistryConfigurer;

              import java.util.Locale;

              // TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand
              public abstract class BaseConfigurer implements TypeRegistryConfigurer {
                  @Override
                  public Locale locale() {
                      return Locale.ENGLISH;
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
