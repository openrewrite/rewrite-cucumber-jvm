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
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;

@EqualsAndHashCode(callSuper = false)
@Value
public class CucumberJava8StepDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION = "io.cucumber.java8.* *(String, ..)";
    private static final String IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY = "io.cucumber.java8.StepDefinitionBody";
    private static final MethodMatcher STEP_DEFINITION_METHOD_MATCHER = new MethodMatcher(
            IO_CUCUMBER_JAVA8_STEP_DEFINITION);

    String displayName = "Replace `cucumber-java8` step definitions with `cucumber-java`";

    String description = "Replace `StepDefinitionBody` methods with `StepDefinitionAnnotations` on new methods with " +
            "the same body, or, for a method reference, with a body calling the method it refers to.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(IO_CUCUMBER_JAVA8_STEP_DEFINITION, true),
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation methodInvocation, ExecutionContext ctx) {
                        J.MethodInvocation m = (J.MethodInvocation) super.visitMethodInvocation(methodInvocation, ctx);
                        if (!STEP_DEFINITION_METHOD_MATCHER.matches(m) || m.getMethodType() == null) {
                            return m;
                        }

                        // Skip any methods not containing a second argument, such as
                        // Scenario.log(String)
                        if (m.getArguments().size() < 2) {
                            return m;
                        }

                        // Determine step definitions class name
                        J.ClassDeclaration parentClass = getCursor()
                                .dropParentUntil(J.ClassDeclaration.class::isInstance)
                                .getValue();
                        JavaType.FullyQualified parentType = parentClass.getType();

                        // Annotations require a String literal, and the body a lambda or method reference to move
                        List<String> replacementImports = new ArrayList<>();
                        StepDefinitionArguments stepArguments = stepDefinitionArguments(m,
                                parentType == null ? "" : parentType.getPackageName(), replacementImports);
                        if (stepArguments == null) {
                            return SearchResult.found(m, "TODO Migrate manually");
                        }

                        replacementImports.add(0, String.format("%s.%s",
                                m.getMethodType().getDeclaringType().getFullyQualifiedName()
                                        .replace("java8", "java").toLowerCase(),
                                m.getSimpleName()));
                        J.MethodDeclaration glueDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        doAfterVisit(new CucumberJava8ClassVisitor(
                                parentType,
                                glueDeclaration == null ? null : glueDeclaration.getId(),
                                replacementImports,
                                stepArguments.getParameterTypes(),
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
        // Only whether the step definition converts is asked here, not what it converts to, so the package the
        // parameter types are named relative to makes no difference
        return STEP_DEFINITION_METHOD_MATCHER.matches(methodInvocation) &&
                stepDefinitionArguments(methodInvocation, "", new ArrayList<>()) != null;
    }

    /**
     * @param replacementImports collects the imports the parameters of the replacing method need, for the types a
     *                           method reference names nowhere in the file it is migrated in
     * @return the arguments to build the annotated method from, or {@code null} where the step definition is one to
     * leave where it is for a manual migration
     */
    private static @Nullable StepDefinitionArguments stepDefinitionArguments(J.MethodInvocation methodInvocation,
                                                                             String packageName,
                                                                             List<String> replacementImports) {
        List<Expression> arguments = methodInvocation.getArguments();
        if (arguments.size() < 2 || !(arguments.get(0) instanceof J.Literal)) {
            return null;
        }
        Expression definitionBody = arguments.get(1);
        if (!TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_STEP_DEFINITION_BODY, definitionBody.getType())) {
            return null;
        }
        String annotationName = methodInvocation.getSimpleName();
        J.Literal cucumberExpression = (J.Literal) arguments.get(0);
        if (definitionBody instanceof J.Lambda) {
            J.Lambda lambda = (J.Lambda) definitionBody;
            return new StepDefinitionArguments(annotationName, cucumberExpression,
                    declaredParameters(lambda), emptyList(), lambda.getBody());
        }
        if (!(definitionBody instanceof J.MemberReference)) {
            return null;
        }

        J.MemberReference reference = (J.MemberReference) definitionBody;
        List<JavaType.Class> parameterTypes = MemberReferences.functionalInterfaceParameters(reference);
        if (parameterTypes == null) {
            return null;
        }
        MemberReferences.Kind kind = MemberReferences.kind(reference, parameterTypes.size());
        if (kind == null) {
            return null;
        }
        List<String> parameterNames = MemberReferences.parameterNames(reference, kind, parameterTypes.size());
        StringBuilder parameters = new StringBuilder();
        for (int i = 0; i < parameterTypes.size(); i++) {
            JavaType.Class parameterType = parameterTypes.get(i);
            String parameterTypeImport = requiredImport(parameterType, packageName);
            if (parameterTypeImport != null) {
                replacementImports.add(parameterTypeImport);
            }
            parameters.append(i == 0 ? "" : ", ")
                    .append(typeName(parameterType, packageName)).append(' ').append(parameterNames.get(i));
        }
        return new StepDefinitionArguments(annotationName, cucumberExpression, parameters.toString(),
                new ArrayList<>(parameterTypes), MemberReferences.body(reference, kind, parameterNames));
    }

    private static String declaredParameters(J.Lambda lambda) {
        // TODO Type loss here, but my attempts to pass these as J failed:
        // __P__.<java.lang.Object>/*__p0__*/p <error>()
        return lambda.getParameters().getParameters().stream()
                .filter(J.VariableDeclarations.class::isInstance)
                .map(J.VariableDeclarations.class::cast)
                .map(J.VariableDeclarations::toString)
                .collect(joining(", "));
    }

    /**
     * @return the name to declare a parameter of this type with, qualified through the class it is nested in where
     * that class is what the import, or the package the file is in, brings into scope
     */
    private static String typeName(JavaType.FullyQualified type, String packageName) {
        String className = type.getClassName();
        return isInScope(type, packageName) ? className : className.substring(className.lastIndexOf('.') + 1);
    }

    private static @Nullable String requiredImport(JavaType.FullyQualified type, String packageName) {
        return isInScope(type, packageName) ? null : type.getPackageName() + '.' + type.getClassName();
    }

    private static boolean isInScope(JavaType.FullyQualified type, String packageName) {
        return type.getPackageName().equals(packageName) || "java.lang".equals(type.getPackageName());
    }

}

@Value
class StepDefinitionArguments {

    String annotationName;
    J.Literal cucumberExpression;
    String methodParameters;
    List<JavaType> parameterTypes;
    J body;

    String template() {
        return "@#{}(#{any()})\npublic void #{}(#{}) throws Exception {\n\t#{any()};\n}";
    }

    private String formatMethodName() {
        return ((String) cucumberExpression.getValue())
                .replaceAll("\\s+", "_")
                .replaceAll("[^A-Za-z0-9_]", "")
                .toLowerCase();
    }

    Object[] parameters() {
        return new Object[]{
                annotationName,
                cucumberExpression,
                formatMethodName(),
                methodParameters,
                body};
    }

}
