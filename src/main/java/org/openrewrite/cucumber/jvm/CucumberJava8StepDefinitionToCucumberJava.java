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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class CucumberJava8StepDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = "io.cucumber.java8.* *(String, ..)";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY = "io.cucumber.java8.StepDefinitionBody";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    String displayName = "Replace `cucumber-java8` step definitions with `cucumber-java`";

    String description = "Replace `StepDefinitionBody` methods with `StepDefinitionAnnotations` on new methods with the same body.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(IO_CUCUMBER_JAVA8_STEP_DEFINITION, true),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(methodInvocation, ctx);
                        if (!STEP_DEFINITION_METHOD_MATCHER.matches(m)) {
                            return m;
                        }

                        // Skip any methods not containing a second argument, such as
                        // Scenario.log(String)
                        List<Expression> arguments = m.getArguments();
                        if (arguments.size() < 2) {
                            return m;
                        }

                        // Annotations require a String literal, and the body has to be a lambda to move
                        if (!converts(m)) {
                            return SearchResult.found(m, "TODO Migrate manually");
                        }

                        StepDefinitionArguments stepArguments = new StepDefinitionArguments(
                                m.getSimpleName(),
                                (J.Literal) arguments.get(0),
                                (J.Lambda) arguments.get(1));

                        // Determine step definitions class name
                        J.ClassDeclaration parentClass = getCursor()
                                .dropParentUntil(J.ClassDeclaration.class::isInstance)
                                .getValue();
                        if (m.getMethodType() == null) {
                            return m;
                        }
                        String replacementImport = String.format("%s.%s",
                                m.getMethodType().getDeclaringType().getFullyQualifiedName()
                                        .replace("java8", "java").toLowerCase(),
                                m.getSimpleName());
                        J.MethodDeclaration glueDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        doAfterVisit(new CucumberJava8ClassVisitor(
                                parentClass.getType(),
                                glueDeclaration == null ? null : glueDeclaration.getId(),
                                singletonList(replacementImport),
                                stepArguments.template(),
                                stepArguments.parameters()));

                        // Remove original method invocation; it's replaced in the above
                        // visitor
                        // noinspection DataFlowIssue
                        return null;
                    }
                });
    }

    /**
     * @return whether this invocation is a step definition this recipe replaces with an annotated method, rather
     * than one it leaves where it is for a manual migration
     */
    static boolean converts(J.MethodInvocation methodInvocation) {
        if (!STEP_DEFINITION_METHOD_MATCHER.matches(methodInvocation)) {
            return false;
        }
        List<Expression> arguments = methodInvocation.getArguments();
        // Skip any methods not containing a second argument, such as Scenario.log(String)
        return arguments.size() >= 2 &&
                arguments.get(0) instanceof J.Literal &&
                arguments.get(1) instanceof J.Lambda &&
                TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY, arguments.get(1).getType());
    }

}

@Value
class StepDefinitionArguments {

    String annotationName;
    J.Literal cucumberExpression;
    J.Lambda lambda;

    String template() {
        return "@#{}(#{any()})\npublic void #{}(#{}) throws Exception {\n\t#{any()};\n}";
    }

    private String formatMethodName() {
        return ((String) cucumberExpression.getValue())
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_]", "")
                .toLowerCase();
    }

    private String formatMethodArguments() {
        // TODO Type loss here, but my attempts to pass these as J failed:
        // __P__.<java.lang.Object>/*__p0__*/p <error>()
        return lambda.getParameters().getParameters().stream()
                .filter(org.openrewrite.java.tree.J.VariableDeclarations.class::isInstance)
                .map(org.openrewrite.java.tree.J.VariableDeclarations.class::cast)
                .map(J.VariableDeclarations::toString)
                .collect(joining(", "));
    }

    Object[] parameters() {
        return new Object[]{
                annotationName,
                cucumberExpression,
                formatMethodName(),
                formatMethodArguments(),
                lambda.getBody()};
    }

}
