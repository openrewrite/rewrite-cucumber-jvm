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

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;
import org.openrewrite.staticanalysis.UnnecessaryThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.Collections.emptyList;

@RequiredArgsConstructor
class CucumberJava8ClassVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String IO_CUCUMBER_JAVA = "io.cucumber.java";
    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";

    private final JavaType.FullyQualified stepDefinitionsClass;
    private final String replacementImport;
    private final String template;
    private final Object[] templateParameters;

    @Override
    public J.@Nullable ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
        J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, ctx);
        if (!TypeUtils.isOfType(classDeclaration.getType(), stepDefinitionsClass)) {
            // We aren't looking at the specified class so return without making
            // any modifications
            return classDeclaration;
        }

        // Remove implement of Java8 interfaces & imports; return retained
        List<TypeTree> retained = filterImplementingInterfaces(classDeclaration);

        // Import Given/When/Then or Before/After as applicable
        maybeAddImport(replacementImport);

        // Remove empty constructor which might be left over after removing
        // method invocations with typical usage
        doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(md, ctx);
                if (methodDeclaration.isConstructor() && isStepDefinitionsClassMember() &&
                        (methodDeclaration.getBody() == null ||
                                methodDeclaration.getBody().getStatements().isEmpty())) {
                    // noinspection DataFlowIssue
                    return null;
                }
                return methodDeclaration;
            }

            /**
             * This visitor runs over the whole source file, which can hold classes other than the one being
             * migrated; only the step definitions class has a constructor left empty by this recipe.
             */
            private boolean isStepDefinitionsClassMember() {
                J.ClassDeclaration enclosing = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return enclosing != null && TypeUtils.isOfType(enclosing.getType(), stepDefinitionsClass);
            }
        });

        // Remove nested braces from lambda body block inserted into new method
        doAfterVisit(new RemoveUnneededBlock().getVisitor());

        // Remove unnecessary throws from templates that maybe-throw-exceptions
        doAfterVisit(new UnnecessaryThrows().getVisitor());

        // Update implements & add new method
        J.ClassDeclaration applied = JavaTemplate.builder(template)
                .contextSensitive()
                .javaParser(
                        JavaParser.fromJavaVersion().classpathFromResources(ctx, "cucumber-java-7", "cucumber-java8-7"))
                .imports(replacementImport)
                .build().apply(getCursor(), coordinatesForNewMethod(classDeclaration.getBody()), templateParameters);
        return retainConstructorArguments(applied.withImplements(retained));
    }

    /**
     * The lambdas moved out of the constructor commonly close over its arguments, which is how `cucumber-java8`
     * glue receives its dependency injected collaborators. Those arguments are out of scope in the methods the
     * lambda bodies now live in, so hold each one in a field that the constructor assigns.
     */
    private J.ClassDeclaration retainConstructorArguments(J.ClassDeclaration classDeclaration) {
        J.MethodDeclaration constructor = soleConstructor(classDeclaration);
        if (constructor == null) {
            return classDeclaration;
        }
        Set<String> namesTaken = declaredFieldNames(classDeclaration);
        StringBuilder fields = new StringBuilder();
        StringBuilder assignments = new StringBuilder();
        for (Statement parameter : constructor.getParameters()) {
            if (!(parameter instanceof J.VariableDeclarations)) {
                continue;
            }
            J.VariableDeclarations argument = (J.VariableDeclarations) parameter;
            if (argument.getTypeExpression() == null || argument.getVariables().size() != 1) {
                continue;
            }
            String name = argument.getVariables().get(0).getSimpleName();
            if (!namesTaken.add(name)) {
                // Already retained on an earlier pass over this same class, or taken by a field declared here
                continue;
            }
            fields.append(String.format("private final %s %s;%n",
                    argument.getTypeExpression().printTrimmed(getCursor()), name));
            assignments.append(String.format("this.%s = %s;%n", name, name));
        }
        if (fields.length() == 0) {
            return classDeclaration;
        }

        J.ClassDeclaration c = JavaTemplate.builder(fields.toString())
                .contextSensitive()
                .build()
                .apply(updateCursor(classDeclaration), classDeclaration.getBody().getCoordinates().firstStatement());
        J.MethodDeclaration reboundConstructor = soleConstructor(c);
        if (reboundConstructor == null || reboundConstructor.getBody() == null) {
            return c;
        }
        // Appended rather than prepended, as `firstStatement` anchors on a statement that may since have been
        // replaced, and has nothing to anchor to at all once the lambdas have left the constructor empty
        return JavaTemplate.builder(assignments.toString())
                .contextSensitive()
                .build()
                .apply(updateCursor(c), reboundConstructor.getBody().getCoordinates().lastStatement());
    }

    /**
     * @return the only constructor of the class, if it takes arguments; {@code null} when there is no such
     * constructor, or more than one, as then there is no single place to assign the fields from
     */
    private static J.@Nullable MethodDeclaration soleConstructor(J.ClassDeclaration classDeclaration) {
        J.MethodDeclaration constructor = null;
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration && ((J.MethodDeclaration) statement).isConstructor()) {
                if (constructor != null) {
                    return null;
                }
                constructor = (J.MethodDeclaration) statement;
            }
        }
        return constructor == null || constructor.getParameters().stream()
                .noneMatch(J.VariableDeclarations.class::isInstance) ? null : constructor;
    }

    private static Set<String> declaredFieldNames(J.ClassDeclaration classDeclaration) {
        Set<String> names = new HashSet<>();
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    names.add(variable.getSimpleName());
                }
            }
        }
        return names;
    }

    /**
     * Remove imports & usage of Cucumber-Java8 interfaces.
     *
     * @return retained implementing interfaces
     */
    private List<TypeTree> filterImplementingInterfaces(J.ClassDeclaration classDeclaration) {
        List<TypeTree> retained = new ArrayList<>();
        for (TypeTree typeTree : Optional.ofNullable(classDeclaration.getImplements())
                .orElse(emptyList())) {
            if (typeTree.getType() instanceof JavaType.Class) {
                JavaType.Class clazz = (JavaType.Class) typeTree.getType();
                if (IO_CUCUMBER_JAVA8.equals(clazz.getPackageName())) {
                    maybeRemoveImport(clazz.getFullyQualifiedName());
                    continue;
                }
            }
            retained.add(typeTree);
        }
        return retained;
    }

    /**
     * Place new methods after the last cucumber annotated method, or after the
     * constructor, or at end of class.
     */
    private static JavaCoordinates coordinatesForNewMethod(J.Block body) {
        // After last cucumber annotated method
        return body.getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .map(org.openrewrite.java.tree.J.MethodDeclaration.class::cast)
                .filter(method -> method.getAllAnnotations().stream()
                        .anyMatch(ann -> ann.getAnnotationType().getType() != null &&
                                ((JavaType.Class) ann.getAnnotationType().getType()).getPackageName()
                                        .startsWith(IO_CUCUMBER_JAVA)))
                .map(method -> method.getCoordinates().after())
                .reduce((a, b) -> b)
                // After last constructor
                .orElseGet(() -> body.getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(org.openrewrite.java.tree.J.MethodDeclaration.class::cast)
                        .filter(J.MethodDeclaration::isConstructor)
                        .map(constructor -> constructor.getCoordinates().after())
                        .reduce((a, b) -> b)
                        // At end of class
                        .orElseGet(() -> body.getCoordinates().lastStatement()));
    }
}
