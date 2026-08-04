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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class CollapseCucumberOptionsTags extends Recipe {

    private static final String JUNIT_CUCUMBER_OPTIONS = "io.cucumber.junit.CucumberOptions";
    private static final String TESTNG_CUCUMBER_OPTIONS = "io.cucumber.testng.CucumberOptions";

    private static final AnnotationMatcher JUNIT_MATCHER = new AnnotationMatcher('@' + JUNIT_CUCUMBER_OPTIONS);
    private static final AnnotationMatcher TESTNG_MATCHER = new AnnotationMatcher('@' + TESTNG_CUCUMBER_OPTIONS);

    @Getter
    final String displayName = "Collapse `@CucumberOptions` tags into a single tag expression";

    @Getter
    final String description = "Cucumber-JVM 6.0.0 narrowed `@CucumberOptions#tags` from `String[]` to a single " +
            "`String`. The elements of the array were combined with `and`, such that " +
            "`tags = {\"@a\", \"@b\"}` becomes `tags = \"(@a) and (@b)\"`.";

    @Getter
    final Duration estimatedEffortPerOccurrence = Duration.ofMinutes(1);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(JUNIT_CUCUMBER_OPTIONS, null),
                        new UsesType<>(TESTNG_CUCUMBER_OPTIONS, null)),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                        J.Annotation a = super.visitAnnotation(annotation, ctx);
                        if (!JUNIT_MATCHER.matches(a) && !TESTNG_MATCHER.matches(a)) {
                            return a;
                        }
                        return a.withArguments(ListUtils.map(a.getArguments(), argument -> {
                            if (!(argument instanceof J.Assignment)) {
                                return argument;
                            }
                            J.Assignment assignment = (J.Assignment) argument;
                            if (!(assignment.getVariable() instanceof J.Identifier) ||
                                    !"tags".equals(((J.Identifier) assignment.getVariable()).getSimpleName()) ||
                                    !(assignment.getAssignment() instanceof J.NewArray)) {
                                return argument;
                            }
                            J.NewArray tags = (J.NewArray) assignment.getAssignment();
                            J.Literal collapsed = collapse(tags.getInitializer());
                            if (collapsed == null) {
                                return argument;
                            }
                            return assignment.withAssignment(collapsed.withPrefix(tags.getPrefix()));
                        }));
                    }
                });
    }

    private static J.@Nullable Literal collapse(@Nullable List<Expression> initializer) {
        if (initializer == null) {
            return null;
        }
        List<J.Literal> literals = new ArrayList<>(initializer.size());
        for (Expression expression : initializer) {
            if (expression instanceof J.Empty) {
                continue;
            }
            if (!(expression instanceof J.Literal) || !(((J.Literal) expression).getValue() instanceof String) ||
                    unquote((J.Literal) expression) == null) {
                return null;
            }
            literals.add((J.Literal) expression);
        }
        if (literals.size() == 1) {
            return literals.get(0);
        }

        StringBuilder value = new StringBuilder();
        StringBuilder valueSource = new StringBuilder();
        for (J.Literal literal : literals) {
            if (value.length() > 0) {
                value.append(" and ");
                valueSource.append(" and ");
            }
            value.append('(').append(literal.getValue()).append(')');
            valueSource.append('(').append(unquote(literal)).append(')');
        }
        return new J.Literal(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                value.toString(), '"' + valueSource.toString() + '"', null, JavaType.Primitive.String);
    }

    private static @Nullable String unquote(J.Literal literal) {
        String source = literal.getValueSource();
        if (source == null || source.length() < 2 || source.charAt(0) != '"' ||
                source.charAt(source.length() - 1) != '"') {
            return null;
        }
        return source.substring(1, source.length() - 1);
    }
}
