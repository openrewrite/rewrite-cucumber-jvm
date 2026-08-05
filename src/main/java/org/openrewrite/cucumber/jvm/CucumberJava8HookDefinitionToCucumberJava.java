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
import lombok.With;
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
import org.openrewrite.java.tree.JavaType.Primitive;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

@EqualsAndHashCode(callSuper = false)
@Value
public class CucumberJava8HookDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_HOOK_BODY = "io.cucumber.java8.HookBody";
    private static final String IO_CUCUMBER_JAVA8_HOOK_NO_ARGS_BODY = "io.cucumber.java8.HookNoArgsBody";

    private static final String HOOK_BODY_DEFINITION = IO_CUCUMBER_JAVA8 +
            ".LambdaGlue *(.., " + IO_CUCUMBER_JAVA8_HOOK_BODY + ")";
    private static final String HOOK_NO_ARGS_BODY_DEFINITION = IO_CUCUMBER_JAVA8 +
            ".LambdaGlue *(.., " + IO_CUCUMBER_JAVA8_HOOK_NO_ARGS_BODY + ")";

    private static final MethodMatcher HOOK_BODY_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            HOOK_BODY_DEFINITION);
    private static final MethodMatcher HOOK_NO_ARGS_BODY_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            HOOK_NO_ARGS_BODY_DEFINITION);


    String displayName = "Replace `cucumber-java8` hook definition with `cucumber-java`";

    String description = "Replace `LambdaGlue` hook definitions with new annotated methods with the same body, or, " +
            "for a method reference, with a body calling the method it refers to.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesMethod<>(HOOK_BODY_DEFINITION, true),
                        new UsesMethod<>(HOOK_NO_ARGS_BODY_DEFINITION, true))
                , new JavaVisitor<ExecutionContext>() {

                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        J.MethodInvocation methodInvocation = (J.MethodInvocation) super.visitMethodInvocation(mi, ctx);
                        if (!isHookDefinition(methodInvocation)) {
                            return methodInvocation;
                        }

                        // Extract arguments passed to method
                        HookArguments hookArguments = parseHookArguments(methodInvocation);
                        if (hookArguments == null) {
                            return SearchResult.found(methodInvocation, "TODO Migrate manually");
                        }

                        // Add new template method at end of class declaration
                        J.ClassDeclaration parentClass = getCursor()
                                .dropParentUntil(J.ClassDeclaration.class::isInstance)
                                .getValue();
                        J.MethodDeclaration glueDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        doAfterVisit(new CucumberJava8ClassVisitor(
                                parentClass.getType(),
                                glueDeclaration == null ? null : glueDeclaration.getId(),
                                hookArguments.replacementImports(),
                                emptyList(),
                                hookArguments.template(),
                                hookArguments.parameters(),
                                null));

                        // Remove original method invocation; it's replaced in the above
                        // visitor
                        // noinspection DataFlowIssue
                        return null;
                    }
                });
    }

    private static boolean isHookDefinition(J.MethodInvocation methodInvocation) {
        return HOOK_BODY_DEFINITION_METHOD_MATCHER.matches(methodInvocation) ||
                HOOK_NO_ARGS_BODY_DEFINITION_METHOD_MATCHER.matches(methodInvocation);
    }

    /**
     * @return whether this invocation is a hook definition this recipe replaces with an annotated method, rather
     * than one it leaves where it is for a manual migration
     */
    static boolean converts(J.MethodInvocation methodInvocation) {
        return isHookDefinition(methodInvocation) && parseHookArguments(methodInvocation) != null;
    }

    /**
     * Parse up to three arguments: - last one is always the hook body; - first
     * can also be a String or int. - second can be an int;
     *
     * @return {@code null} where an argument is not one the replacing annotation or method can hold
     */
    private static @Nullable HookArguments parseHookArguments(J.MethodInvocation methodInvocation) {
        List<Expression> arguments = methodInvocation.getArguments();
        int argumentsSize = arguments.size();
        // Replacement annotations can only handle literals or constants
        for (int i = 0; i < argumentsSize - 1; i++) {
            if (!(arguments.get(i) instanceof J.Literal)) {
                return null;
            }
        }

        // The body is always last, and can either take a Scenario argument, or none
        HookArguments hookArguments = parseHookBody(methodInvocation.getSimpleName(),
                arguments.get(argumentsSize - 1),
                HOOK_BODY_DEFINITION_METHOD_MATCHER.matches(methodInvocation));
        if (hookArguments == null || argumentsSize == 1) {
            return hookArguments;
        }

        J.Literal firstArgument = (J.Literal) arguments.get(0);
        if (argumentsSize == 2) {
            // First argument is either a String or an int
            if (firstArgument.getType() == Primitive.String) {
                return hookArguments.withTagExpression((String) firstArgument.getValue());
            }
            return hookArguments.withOrder((Integer) firstArgument.getValue());
        }
        // First argument is always a String, second argument always an int
        return hookArguments
                .withTagExpression((String) firstArgument.getValue())
                .withOrder((Integer) ((J.Literal) arguments.get(1)).getValue());
    }

    /**
     * @param takesScenario whether the hook body is a {@link #IO_CUCUMBER_JAVA8_HOOK_BODY} rather than a
     *                      {@link #IO_CUCUMBER_JAVA8_HOOK_NO_ARGS_BODY}, and so is handed the running scenario
     */
    private static @Nullable HookArguments parseHookBody(String annotationName, Expression body,
                                                         boolean takesScenario) {
        if (body instanceof J.Lambda) {
            J.Lambda lambda = (J.Lambda) body;
            J parameter = lambda.getParameters().getParameters().get(0);
            return new HookArguments(annotationName, null, null,
                    parameter instanceof J.VariableDeclarations ?
                            ((J.VariableDeclarations) parameter).getVariables().get(0).getSimpleName() : null,
                    lambda.getBody());
        }
        if (!(body instanceof J.MemberReference)) {
            return null;
        }

        J.MemberReference reference = (J.MemberReference) body;
        int parameterCount = takesScenario ? 1 : 0;
        MemberReferences.Kind kind = MemberReferences.kind(reference, parameterCount);
        if (kind == null) {
            return null;
        }
        List<String> parameterNames = MemberReferences.parameterNames(reference, kind, parameterCount);
        return new HookArguments(annotationName, null, null,
                takesScenario ? parameterNames.get(0) : null,
                MemberReferences.body(reference, kind, parameterNames));
    }

}

@Value
class HookArguments {

    /**
     * Imported as the `io.cucumber.java8` type the hook body still accepts, rather than as the
     * `io.cucumber.java.Scenario` it will become: the file is only retyped once every hook and step in it has
     * migrated, and until then the simple name has to resolve against the import already there.
     */
    private static final String IO_CUCUMBER_JAVA8_SCENARIO = "io.cucumber.java8.Scenario";

    String annotationName;

    @With
    @Nullable
    String tagExpression;

    @With
    @Nullable
    Integer order;

    @Nullable
    String scenarioName;

    J body;

    List<String> replacementImports() {
        String annotationImport = String.format("io.cucumber.java.%s", annotationName);
        return scenarioName != null ?
                Arrays.asList(annotationImport, IO_CUCUMBER_JAVA8_SCENARIO) :
                singletonList(annotationImport);
    }

    String template() {
        return "@#{}#{}\npublic void #{}(#{}) throws Exception {\n\t#{any()};\n}";
    }

    private String formatAnnotationArguments() {
        if (tagExpression == null && order == null) {
            return "";
        }
        StringBuilder template = new StringBuilder();
        template.append('(');
        if (order != null) {
            template.append("order = ").append(order);
            if (tagExpression != null) {
                template.append(", value = \"").append(tagExpression).append('"');
            }
        } else {
            template.append('"').append(tagExpression).append('"');
        }
        template.append(')');
        return template.toString();
    }

    private String formatMethodName() {
        return String.format("%s%s%s",
                annotationName
                        .replaceFirst("^Before", "before")
                        .replaceFirst("^After", "after"),
                tagExpression == null ? "" :
                        "_tag_" + tagExpression
                                .replaceAll("[^A-Za-z0-9]", "_"),
                order == null ? "" : "_order_" + order);
    }

    private String formatMethodArguments() {
        return scenarioName == null ? "" : String.format("Scenario %s", scenarioName);
    }

    public Object[] parameters() {
        return new Object[]{
                annotationName,
                formatAnnotationArguments(),
                formatMethodName(),
                formatMethodArguments(),
                body};
    }

}
