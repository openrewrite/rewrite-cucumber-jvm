/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.cucumber.jvm;

import lombok.SneakyThrows;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.JavaCoordinates;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.text.RuleBasedCollator;
import java.time.Duration;
import java.util.Comparator;

public class CucumberAnnotationToSuite extends Recipe {

    private static final String IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER = "io.cucumber.junit.platform.engine.Cucumber";

    private static final String SUITE = "org.junit.platform.suite.api.Suite";
    private static final String SELECT_CLASSPATH_RESOURCE = "org.junit.platform.suite.api.SelectClasspathResource";

    @Override
    public String getDisplayName() {
        return "Replace `@Cucumber` with `@Suite`";
    }

    @Override
    public String getDescription() {
        return "Replace `@Cucumber` with `@Suite` and `@SelectClasspathResource(\"cucumber/annotated/class/package\")`.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER, null),
                new ExecutionContextJavaIsoVisitor());
    }

    class ExecutionContextJavaIsoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AnnotationMatcher cucumberAnnoMatcher = new AnnotationMatcher(
                "@" + IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);

        @SneakyThrows
        @Override
        public ClassDeclaration visitClassDeclaration(ClassDeclaration cd, ExecutionContext ctx) {
            ClassDeclaration classDecl = super.visitClassDeclaration(cd, ctx);
            if (classDecl.getAllAnnotations().stream().noneMatch(cucumberAnnoMatcher::matches)) {
                return classDecl;
            }

            JavaParser.Builder javaParserSupplier = JavaParser.fromJavaVersion().classpath("junit-platform-suite-api");
            JavaType.FullyQualified classFqn = TypeUtils.asFullyQualified(classDecl.getType());
            if (classFqn != null) {
                // Add suite annotation and select classpath resource
                JavaCoordinates coordinates = classDecl.getCoordinates().addAnnotation(Comparator.comparing(
                        J.Annotation::getSimpleName, new RuleBasedCollator("< SelectClasspathResource")));
                classDecl = JavaTemplate.builder("@Suite @SelectClasspathResource(\"#{}\")")
                        .contextSensitive()
                        .javaParser(javaParserSupplier)
                        .imports(SUITE, SELECT_CLASSPATH_RESOURCE)
                        .build()
                        .apply(getCursor(), coordinates, classFqn.getPackageName().replace('.', '/'));
                maybeAddImport(SUITE);
                maybeAddImport(SELECT_CLASSPATH_RESOURCE);

                // Remove cucumber annotation
                classDecl = classDecl.withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(),
                        ann -> cucumberAnnoMatcher.matches(ann) ? null : ann));
                maybeRemoveImport(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);
            }
            return classDecl;
        }
    }
}
