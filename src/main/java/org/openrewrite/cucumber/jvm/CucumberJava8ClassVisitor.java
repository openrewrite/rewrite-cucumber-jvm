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

import lombok.RequiredArgsConstructor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.tree.*;
import org.openrewrite.java.tree.JavaType.FullyQualified;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
class CucumberJava8ClassVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String IO_CUCUMBER_JAVA = "io.cucumber.java";
    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";

    private final FullyQualified stepDefinitionsClass;
    private final String replacementImport;
    private final String template;
    private final Object[] templateParameters;

    @Override
    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
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
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext p) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(md, p);
                if (methodDeclaration.isConstructor() && (methodDeclaration.getBody() == null
                        || methodDeclaration.getBody().getStatements().isEmpty())) {
                    // noinspection DataFlowIssue
                    return null;
                }
                return methodDeclaration;
            }
        });

        // Remove nested braces from lambda body block inserted into new method
        doAfterVisit(new org.openrewrite.staticanalysis.RemoveUnneededBlock().getVisitor());

        // Remove unnecessary throws from templates that maybe-throw-exceptions
        doAfterVisit(new org.openrewrite.staticanalysis.UnnecessaryThrows().getVisitor());

        // Update implements & add new method
        J.ClassDeclaration applied = JavaTemplate.builder(template)
                .contextSensitive()
                .javaParser(
                        JavaParser.fromJavaVersion().classpath("cucumber-java", "cucumber-java8"))
                .imports(replacementImport)
                .build().apply(getCursor(), coordinatesForNewMethod(classDeclaration.getBody()), templateParameters);
        return applied.withImplements(retained);
    }

    /**
     * Remove imports & usage of Cucumber-Java8 interfaces.
     *
     * @return retained implementing interfaces
     */
    private List<TypeTree> filterImplementingInterfaces(J.ClassDeclaration classDeclaration) {
        List<TypeTree> retained = new ArrayList<>();
        for (TypeTree typeTree : Optional.ofNullable(classDeclaration.getImplements())
                .orElse(Collections.emptyList())) {
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
                .map(firstMethod -> (J.MethodDeclaration) firstMethod)
                .filter(method -> method.getAllAnnotations().stream()
                        .anyMatch(ann -> ann.getAnnotationType().getType() != null
                                && ((JavaType.Class) ann.getAnnotationType().getType()).getPackageName()
                                        .startsWith(IO_CUCUMBER_JAVA)))
                .map(method -> method.getCoordinates().after())
                .reduce((a, b) -> b)
                // After last constructor
                .orElseGet(() -> body.getStatements().stream()
                        .filter(J.MethodDeclaration.class::isInstance)
                        .map(firstMethod -> (J.MethodDeclaration) firstMethod)
                        .filter(J.MethodDeclaration::isConstructor)
                        .map(constructor -> constructor.getCoordinates().after())
                        .reduce((a, b) -> b)
                        // At end of class
                        .orElseGet(() -> body.getCoordinates().lastStatement()));
    }
}
