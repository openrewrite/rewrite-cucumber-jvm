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

import lombok.Getter;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.singletonList;

public class AddTestContextToScenarios extends Recipe {

    private static final String ABSTRACT_TESTNG_CUCUMBER_TESTS = "io.cucumber.testng.AbstractTestNGCucumberTests";
    private static final String ITEST_CONTEXT = "org.testng.ITestContext";
    private static final String SCENARIOS = "scenarios";

    @Getter
    final String displayName = "Add `ITestContext` to `scenarios()` overrides";

    @Getter
    final String description = "Cucumber-JVM 8.0.0 replaced `AbstractTestNGCucumberTests.scenarios()` with " +
            "`scenarios(ITestContext)`, so the documented `@DataProvider(parallel = true)` override no longer " +
            "compiles. Add the `ITestContext` parameter to the override, and pass it on to `super.scenarios()`.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(ABSTRACT_TESTNG_CUCUMBER_TESTS, true), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration md = super.visitMethodDeclaration(method, ctx);
                if (!isScenariosOverride(md)) {
                    return md;
                }
                String parameterName = referencesIdentifier(md, "context") ? "testContext" : "context";
                md = JavaTemplate.builder("ITestContext " + parameterName)
                        .imports(ITEST_CONTEXT)
                        .javaParser(JavaParser.fromJavaVersion()
                                .dependsOn("package org.testng; public interface ITestContext {}"))
                        .build()
                        .apply(updateCursor(md), md.getCoordinates().replaceParameters());
                maybeAddImport(ITEST_CONTEXT);

                JavaType.Method methodType = md.getMethodType();
                J.VariableDeclarations parameter = (J.VariableDeclarations) md.getParameters().get(0);
                J.Identifier contextArgument = new J.Identifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY,
                        Collections.emptyList(), parameterName, parameter.getType(),
                        parameter.getVariables().get(0).getVariableType());
                return (J.MethodDeclaration) new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        J.MethodInvocation m = super.visitMethodInvocation(mi, ctx);
                        if (SCENARIOS.equals(m.getSimpleName()) &&
                                m.getSelect() instanceof J.Identifier &&
                                "super".equals(((J.Identifier) m.getSelect()).getSimpleName()) &&
                                m.getArguments().stream().allMatch(J.Empty.class::isInstance)) {
                            JavaType.Method type = m.getMethodType();
                            if (type != null && methodType != null) {
                                type = type.withParameterNames(singletonList(parameterName))
                                        .withParameterTypes(methodType.getParameterTypes());
                            }
                            return m.withArguments(singletonList(contextArgument)).withMethodType(type)
                                    .withName(m.getName().withType(type));
                        }
                        return m;
                    }
                }.visitNonNull(md, ctx, getCursor().getParentOrThrow());
            }

            private boolean isScenariosOverride(J.MethodDeclaration md) {
                if (!SCENARIOS.equals(md.getSimpleName()) ||
                        !md.getParameters().stream().allMatch(J.Empty.class::isInstance)) {
                    return false;
                }
                J.ClassDeclaration classDeclaration = getCursor().firstEnclosing(J.ClassDeclaration.class);
                return classDeclaration != null &&
                        TypeUtils.isAssignableTo(ABSTRACT_TESTNG_CUCUMBER_TESTS, classDeclaration.getType());
            }

            private boolean referencesIdentifier(J.MethodDeclaration md, String name) {
                AtomicBoolean found = new AtomicBoolean();
                new JavaIsoVisitor<AtomicBoolean>() {
                    @Override
                    public J.Identifier visitIdentifier(J.Identifier identifier, AtomicBoolean f) {
                        if (name.equals(identifier.getSimpleName())) {
                            f.set(true);
                        }
                        return identifier;
                    }
                }.visit(md.getBody(), found);
                return found.get();
            }
        });
    }
}
