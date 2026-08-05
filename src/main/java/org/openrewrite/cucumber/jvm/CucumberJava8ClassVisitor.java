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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.SearchResult;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;
import org.openrewrite.staticanalysis.UnnecessaryThrows;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;

@RequiredArgsConstructor
class CucumberJava8ClassVisitor extends JavaIsoVisitor<ExecutionContext> {

    private static final String IO_CUCUMBER_JAVA = "io.cucumber.java";
    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_LAMBDA_GLUE = "io.cucumber.java8.LambdaGlue";
    private static final String MIGRATE_MANUALLY = "TODO Migrate manually";

    private final JavaType.FullyQualified stepDefinitionsClass;

    /**
     * Identifies the constructor, or occasionally method, the lambda was declared in, as that is where any state
     * the lambda closed over is declared, and what is left empty once the lambda has been hoisted out of it.
     */
    private final @Nullable UUID glueDeclarationId;

    private final List<String> replacementImports;

    /**
     * The types of the parameters the new method declares, in order, for as far as they are known; like
     * {@link #returnType}, a type the project itself declares comes back from the template unattributed.
     */
    private final List<JavaType> parameterTypes;

    private final String template;
    private final Object[] templateParameters;

    /**
     * The template is parsed with only Cucumber on its classpath, so a return type declared by the project itself
     * comes back unattributed.
     */
    private final @Nullable JavaType returnType;

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

        // Import Given/When/Then or Before/After, and Scenario, as applicable
        replacementImports.forEach(this::maybeAddImport);

        // Remove the glue constructor if the migrated lambdas were all it held
        doAfterVisit(new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration md, ExecutionContext ctx) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(md, ctx);
                if (methodDeclaration.isConstructor() && methodDeclaration.getId().equals(glueDeclarationId) &&
                        (methodDeclaration.getBody() == null ||
                                methodDeclaration.getBody().getStatements().isEmpty())) {
                    // noinspection DataFlowIssue
                    return null;
                }
                return methodDeclaration;
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
                        JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "cucumber-java-7", "cucumber-java8-7", "datatable"))
                .imports(replacementImports.toArray(new String[0]))
                .build().apply(getCursor(), coordinatesForNewMethod(classDeclaration.getBody()), templateParameters);
        return retainGlueDeclarationState(retypeNewMethod(classDeclaration, applied).withImplements(retained), ctx);
    }

    /**
     * Found by id rather than by position, as {@code coordinatesForNewMethod} does not always append.
     */
    private J.ClassDeclaration retypeNewMethod(J.ClassDeclaration before, J.ClassDeclaration after) {
        if (returnType == null && parameterTypes.isEmpty()) {
            return after;
        }
        Set<UUID> existing = new HashSet<>();
        for (Statement statement : before.getBody().getStatements()) {
            existing.add(statement.getId());
        }
        return after.withBody(after.getBody().withStatements(ListUtils.map(after.getBody().getStatements(),
                statement -> existing.contains(statement.getId()) || !(statement instanceof J.MethodDeclaration) ?
                        statement : retypeNewMethod((J.MethodDeclaration) statement))));
    }

    private J.MethodDeclaration retypeNewMethod(J.MethodDeclaration method) {
        J.MethodDeclaration retyped = parameterTypes.isEmpty() ? method :
                method.withParameters(ListUtils.map(method.getParameters(), (i, parameter) -> {
                    if (!(parameter instanceof J.VariableDeclarations)) {
                        return parameter;
                    }
                    J.VariableDeclarations declaration = (J.VariableDeclarations) parameter;
                    if (declaration.getTypeExpression() == null || parameterTypes.size() <= i) {
                        return parameter;
                    }
                    JavaType type = parameterTypes.get(i);
                    J.VariableDeclarations.NamedVariable variable = declaration.getVariables().get(0);
                    JavaType.Variable variableType = variable.getVariableType() == null ? null :
                            variable.getVariableType().withType(type);
                    return declaration
                            .withTypeExpression(declaration.getTypeExpression().withType(type))
                            .withVariables(singletonList(variable
                                    .withVariableType(variableType)
                                    .withName(variable.getName().withType(type).withFieldType(variableType))));
                }));
        if (retyped.getMethodType() == null) {
            return retyped;
        }
        JavaType.Method methodType = retyped.getMethodType();
        if (!parameterTypes.isEmpty()) {
            methodType = methodType.withParameterTypes(parameterTypes);
        }
        if (returnType != null && retyped.getReturnTypeExpression() != null) {
            methodType = methodType.withReturnType(returnType);
            retyped = retyped.withReturnTypeExpression(retyped.getReturnTypeExpression().withType(returnType));
        }
        return retyped.withMethodType(methodType).withName(retyped.getName().withType(methodType));
    }

    /**
     * The lambdas moved out of the glue declaration commonly close over its arguments and local variables, which is
     * how `cucumber-java8` glue shares state. Those are out of scope in the methods the lambda bodies now live in,
     * so hold each one in a field instead.
     */
    private J.ClassDeclaration retainGlueDeclarationState(J.ClassDeclaration classDeclaration, ExecutionContext ctx) {
        J.MethodDeclaration glueDeclaration = methodById(classDeclaration, glueDeclarationId);
        // Fall back to the only constructor where the lambdas were declared in a method called from it, as that is
        // still where any injected collaborators they use are declared
        J.MethodDeclaration constructor = glueDeclaration != null && glueDeclaration.isConstructor() ?
                glueDeclaration : soleConstructor(classDeclaration);
        if (constructor == null && glueDeclaration == null) {
            return classDeclaration;
        }

        Set<String> fieldNames = declaredFieldNames(classDeclaration);
        Set<String> namesTaken = new HashSet<>(fieldNames);
        StringBuilder fields = new StringBuilder();
        StringBuilder assignments = new StringBuilder();
        if (constructor != null) {
            // Only a field the one constructor assigns is definitely assigned whichever constructor is called
            String modifiers = constructorCount(classDeclaration) == 1 ? "private final" : "private";
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
                fields.append(String.format("%s %s %s;%n", modifiers, fieldType(argument), name));
                assignments.append(String.format("this.%s = %s;%n", name, name));
            }
        }

        Set<String> capturedNames = glueDeclaration == null ? emptySet() :
                namesUsedByMigratedMethods(classDeclaration, glueDeclaration);
        List<String> promoted = new ArrayList<>();
        boolean anyDeclined = glueDeclaration != null &&
                capturedFromNestedScope(glueDeclaration, capturedNames, fieldNames);
        for (J.VariableDeclarations local : capturedLocalVariables(glueDeclaration, capturedNames)) {
            String name = local.getVariables().get(0).getSimpleName();
            if (canBecomeField(local) && namesTaken.add(name)) {
                fields.append(String.format("private %s %s;%n", fieldType(local), name));
                promoted.add(name);
            } else {
                anyDeclined = true;
            }
        }

        J.ClassDeclaration c = classDeclaration;
        if (fields.length() > 0) {
            c = JavaTemplate.builder(fields.toString())
                    .contextSensitive()
                    .build()
                    .apply(updateCursor(c), c.getBody().getCoordinates().firstStatement());
        }
        if (assignments.length() > 0) {
            J.MethodDeclaration reboundConstructor = sameDeclaration(c, constructor);
            if (reboundConstructor != null && reboundConstructor.getBody() != null) {
                // Appended rather than prepended, as `firstStatement` anchors on a statement that may since have been
                // replaced, and has nothing to anchor to at all once the lambdas have left the constructor empty
                c = JavaTemplate.builder(assignments.toString())
                        .contextSensitive()
                        .build()
                        .apply(updateCursor(c), reboundConstructor.getBody().getCoordinates().lastStatement());
            }
        }
        if (!promoted.isEmpty()) {
            c = assignInsteadOfDeclare(c, sameDeclaration(c, glueDeclaration), promoted, ctx);
        }
        if (anyDeclined) {
            J.MethodDeclaration declined = sameDeclaration(c, glueDeclaration);
            if (declined != null) {
                c = markForManualMigration(c, declined.getId());
            }
        }
        return c;
    }

    /**
     * @return the type to declare a field with, folding any dimensions declared after the variable name, as in
     * {@code int values[]}, back into the type
     */
    private String fieldType(J.VariableDeclarations declaration) {
        //noinspection DataFlowIssue
        StringBuilder type = new StringBuilder(declaration.getTypeExpression().printTrimmed(getCursor()));
        for (int dimension = declaration.getVariables().get(0).getDimensionsAfterName().size(); dimension > 0; dimension--) {
            type.append("[]");
        }
        return type.toString();
    }

    /**
     * Applying a template rebuilds the statements around the one it replaces, so anything looked up before an earlier
     * template was applied has to be found again by what it declares rather than by identity.
     */
    private static J.@Nullable MethodDeclaration sameDeclaration(J.ClassDeclaration classDeclaration,
            J.@Nullable MethodDeclaration declaration) {
        if (declaration == null) {
            return null;
        }
        J.MethodDeclaration byId = methodById(classDeclaration, declaration.getId());
        if (byId != null) {
            return byId;
        }
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                J.MethodDeclaration method = (J.MethodDeclaration) statement;
                if (method.getSimpleName().equals(declaration.getSimpleName()) &&
                        parameterNames(method).equals(parameterNames(declaration))) {
                    return method;
                }
            }
        }
        return null;
    }

    private static List<String> parameterNames(J.MethodDeclaration method) {
        List<String> names = new ArrayList<>();
        for (Statement parameter : method.getParameters()) {
            if (parameter instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) parameter).getVariables()) {
                    names.add(variable.getSimpleName());
                }
            }
        }
        return names;
    }

    /**
     * Now that the variables are declared as fields, what is left of their declaration is the assignment of their
     * initial value, in the place the declaration held.
     */
    private J.ClassDeclaration assignInsteadOfDeclare(J.ClassDeclaration classDeclaration,
            J.@Nullable MethodDeclaration declaration, List<String> promoted, ExecutionContext ctx) {
        if (declaration == null || declaration.getBody() == null) {
            return classDeclaration;
        }
        Set<UUID> declarations = new HashSet<>();
        for (Statement statement : declaration.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                J.VariableDeclarations local = (J.VariableDeclarations) statement;
                if (local.getVariables().size() == 1 &&
                        promoted.contains(local.getVariables().get(0).getSimpleName())) {
                    declarations.add(local.getId());
                }
            }
        }
        if (declarations.isEmpty()) {
            return classDeclaration;
        }
        return (J.ClassDeclaration) new JavaVisitor<ExecutionContext>() {

            @Override
            public J visitVariableDeclarations(J.VariableDeclarations vd, ExecutionContext ctx) {
                if (!declarations.contains(vd.getId())) {
                    return super.visitVariableDeclarations(vd, ctx);
                }
                J.VariableDeclarations.NamedVariable variable = vd.getVariables().get(0);
                return JavaTemplate.builder(String.format("%s = #{any()};", variable.getSimpleName()))
                        .contextSensitive()
                        .build()
                        .apply(getCursor(), vd.getCoordinates().replace(), variable.getInitializer());
            }
        }.visitNonNull(classDeclaration, ctx, getCursor().getParentOrThrow());
    }

    /**
     * @return the variables declared directly in the glue declaration body that the migrated methods still refer to,
     * whether or not they can be turned into a field
     */
    private static List<J.VariableDeclarations> capturedLocalVariables(J.@Nullable MethodDeclaration glueDeclaration,
            Set<String> capturedNames) {
        if (glueDeclaration == null || glueDeclaration.getBody() == null || capturedNames.isEmpty()) {
            return emptyList();
        }
        List<J.VariableDeclarations> captured = new ArrayList<>();
        for (Statement statement : glueDeclaration.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations &&
                    ((J.VariableDeclarations) statement).getVariables().stream()
                            .anyMatch(variable -> capturedNames.contains(variable.getSimpleName()))) {
                captured.add((J.VariableDeclarations) statement);
            }
        }
        return captured;
    }

    /**
     * A variable declared in a scope nested inside the glue declaration, such as the body of an {@code if}, cannot
     * become a field assigned where it was declared, as that assignment only runs when that scope is entered.
     *
     * @return whether a name the migrated methods use is declared in such a scope, leaving nothing for the name to
     * resolve against once the lambda body has moved out
     */
    private static boolean capturedFromNestedScope(J.MethodDeclaration glueDeclaration, Set<String> capturedNames,
            Set<String> fieldNames) {
        if (glueDeclaration.getBody() == null || capturedNames.isEmpty()) {
            return false;
        }
        Set<String> declaredInBody = new HashSet<>();
        for (Statement statement : glueDeclaration.getBody().getStatements()) {
            if (statement instanceof J.VariableDeclarations) {
                for (J.VariableDeclarations.NamedVariable variable : ((J.VariableDeclarations) statement).getVariables()) {
                    declaredInBody.add(variable.getSimpleName());
                }
            }
        }
        return new JavaIsoVisitor<AtomicBoolean>() {

            @Override
            public J.Lambda visitLambda(J.Lambda lambda, AtomicBoolean found) {
                // A lambda yet to migrate is a scope of its own, as is an anonymous class body
                return lambda;
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, AtomicBoolean found) {
                return newClass;
            }

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable,
                    AtomicBoolean found) {
                String name = variable.getSimpleName();
                if (capturedNames.contains(name) && !fieldNames.contains(name) && !declaredInBody.contains(name)) {
                    found.set(true);
                }
                return super.visitVariable(variable, found);
            }
        }.reduce(glueDeclaration.getBody(), new AtomicBoolean()).get();
    }

    /**
     * @return the names the migrated methods use without declaring, which is what a hoisted lambda body closed over
     */
    private static Set<String> namesUsedByMigratedMethods(J.ClassDeclaration classDeclaration,
            J.MethodDeclaration glueDeclaration) {
        Set<String> used = new HashSet<>();
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (!(statement instanceof J.MethodDeclaration)) {
                continue;
            }
            J.MethodDeclaration method = (J.MethodDeclaration) statement;
            if (method.getId().equals(glueDeclaration.getId()) || !isCucumberAnnotated(method)) {
                continue;
            }
            Set<String> referenced = referencedNames(method);
            referenced.removeAll(declaredNames(method));
            used.addAll(referenced);
        }
        return used;
    }

    private static Set<String> referencedNames(J.MethodDeclaration method) {
        return new JavaIsoVisitor<Set<String>>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, Set<String> names) {
                // The method name resolves against whatever it is invoked on, so it is no name of its own
                visit(mi.getSelect(), names);
                visit(mi.getArguments(), names);
                return mi;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, Set<String> names) {
                // Likewise the name after the dot is a member of what precedes it
                visit(fieldAccess.getTarget(), names);
                return fieldAccess;
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, Set<String> names) {
                names.add(identifier.getSimpleName());
                return identifier;
            }
        }.reduce(method, new HashSet<>());
    }

    private static Set<String> declaredNames(J.MethodDeclaration method) {
        return new JavaIsoVisitor<Set<String>>() {

            @Override
            public J.VariableDeclarations.NamedVariable visitVariable(J.VariableDeclarations.NamedVariable variable,
                    Set<String> names) {
                names.add(variable.getSimpleName());
                return super.visitVariable(variable, names);
            }
        }.reduce(method, new HashSet<>());
    }

    /**
     * Only a single variable declared with an explicit type can be swapped for a field assigned in place, as anything
     * else either has no one type to declare, or no one value to assign.
     */
    private static boolean canBecomeField(J.VariableDeclarations local) {
        if (local.getVariables().size() != 1) {
            return false;
        }
        Expression initializer = local.getVariables().get(0).getInitializer();
        if (initializer == null ||
                // An array initializer shorthand such as `{1, 2}` is only valid as part of a declaration
                initializer instanceof J.NewArray && ((J.NewArray) initializer).getTypeExpression() == null) {
            return false;
        }
        TypeTree typeExpression = local.getTypeExpression();
        return typeExpression != null &&
                !(typeExpression instanceof J.Identifier && "var".equals(((J.Identifier) typeExpression).getSimpleName()));
    }

    private static J.ClassDeclaration markForManualMigration(J.ClassDeclaration classDeclaration, UUID declarationId) {
        return classDeclaration.withBody(classDeclaration.getBody().withStatements(
                ListUtils.map(classDeclaration.getBody().getStatements(), statement ->
                        statement.getId().equals(declarationId) &&
                                !statement.getMarkers().findFirst(SearchResult.class).isPresent() ?
                                SearchResult.found(statement, MIGRATE_MANUALLY) : statement)));
    }

    private static J.@Nullable MethodDeclaration methodById(J.ClassDeclaration classDeclaration, @Nullable UUID id) {
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration && statement.getId().equals(id)) {
                return (J.MethodDeclaration) statement;
            }
        }
        return null;
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

    private static long constructorCount(J.ClassDeclaration classDeclaration) {
        return classDeclaration.getBody().getStatements().stream()
                .filter(J.MethodDeclaration.class::isInstance)
                .filter(statement -> ((J.MethodDeclaration) statement).isConstructor())
                .count();
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
     * Remove imports & usage of Cucumber-Java8 interfaces, unless any of the methods they contribute survived the
     * migration, as those are only in scope for as long as the class implements the interface declaring them.
     *
     * @return retained implementing interfaces
     */
    private List<TypeTree> filterImplementingInterfaces(J.ClassDeclaration classDeclaration) {
        List<TypeTree> implementings = Optional.ofNullable(classDeclaration.getImplements()).orElse(emptyList());
        if (lambdaGlueRemains(classDeclaration)) {
            return implementings;
        }
        List<TypeTree> retained = new ArrayList<>();
        for (TypeTree typeTree : implementings) {
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

    private static boolean lambdaGlueRemains(J.ClassDeclaration classDeclaration) {
        return new JavaIsoVisitor<AtomicBoolean>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, AtomicBoolean found) {
                return cd.getId().equals(classDeclaration.getId()) ? super.visitClassDeclaration(cd, found) : cd;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean found) {
                if (mi.getMethodType() != null) {
                    // The declaring type is the interface contributing the method, not the class implementing it,
                    // which itself is assignable to `LambdaGlue` for as long as it implements one of those interfaces
                    JavaType.FullyQualified declaringType = mi.getMethodType().getDeclaringType();
                    if (IO_CUCUMBER_JAVA8.equals(declaringType.getPackageName()) &&
                            TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_LAMBDA_GLUE, declaringType)) {
                        found.set(true);
                    }
                }
                return super.visitMethodInvocation(mi, found);
            }
        }.reduce(classDeclaration, new AtomicBoolean()).get();
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
                .filter(CucumberJava8ClassVisitor::isCucumberAnnotated)
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

    private static boolean isCucumberAnnotated(J.MethodDeclaration method) {
        return method.getAllAnnotations().stream()
                .map(annotation -> TypeUtils.asFullyQualified(annotation.getAnnotationType().getType()))
                .anyMatch(type -> type != null && type.getPackageName().startsWith(IO_CUCUMBER_JAVA));
    }
}
