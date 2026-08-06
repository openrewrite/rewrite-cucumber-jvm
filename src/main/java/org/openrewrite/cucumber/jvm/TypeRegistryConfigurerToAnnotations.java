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
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.jspecify.annotations.Nullable;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.*;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.*;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;
import org.openrewrite.staticanalysis.UnnecessaryThrows;
import org.openrewrite.staticanalysis.VariableReferences;
import org.openrewrite.trait.Comments;

import java.time.Duration;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.openrewrite.cucumber.jvm.GlueMethods.PARAMETER_TYPE_IMPORTS;
import static org.openrewrite.cucumber.jvm.GlueMethods.decapitalize;
import static org.openrewrite.cucumber.jvm.GlueMethods.declaredMethodNames;
import static org.openrewrite.cucumber.jvm.GlueMethods.fullyQualifiedName;
import static org.openrewrite.cucumber.jvm.GlueMethods.literalSource;
import static org.openrewrite.cucumber.jvm.GlueMethods.sanitize;
import static org.openrewrite.cucumber.jvm.GlueMethods.stringLiteral;
import static org.openrewrite.cucumber.jvm.GlueMethods.typeOf;
import static org.openrewrite.cucumber.jvm.GlueMethods.uniqueMethodName;


@EqualsAndHashCode(callSuper = false)
@Value
public class TypeRegistryConfigurerToAnnotations extends Recipe {

    // Cucumber-JVM 5.0.0 moved these from `cucumber.api` to `io.cucumber.core.api`
    private static final TypeMatcher TYPE_REGISTRY = new TypeMatcher("*..api.TypeRegistry");
    private static final TypeMatcher TYPE_REGISTRY_CONFIGURER = new TypeMatcher("*..api.TypeRegistryConfigurer");
    private static final MethodMatcher CONFIGURE_TYPE_REGISTRY = new MethodMatcher(
            "*..api.TypeRegistryConfigurer configureTypeRegistry(*..api.TypeRegistry)", true);

    private static final String MANUAL_MIGRATION = "TODO Cucumber-JVM 7.0.0 removed TypeRegistryConfigurer; " +
            "migrate to @ParameterType, @DataTableType and @DocStringType annotated methods by hand";

    private static final String IO_CUCUMBER_JAVA = "io.cucumber.java.";
    private static final String IO_CUCUMBER_JAVA_PARAMETER_TYPE = IO_CUCUMBER_JAVA + "ParameterType";
    private static final String IO_CUCUMBER_JAVA_DATA_TABLE_TYPE = IO_CUCUMBER_JAVA + "DataTableType";
    private static final String IO_CUCUMBER_JAVA_DOC_STRING_TYPE = IO_CUCUMBER_JAVA + "DocStringType";

    private static final List<String> OBSOLETE_IMPORTS = Arrays.asList(
            "cucumber.api.TypeRegistry",
            "cucumber.api.TypeRegistryConfigurer",
            "io.cucumber.core.api.TypeRegistry",
            "io.cucumber.core.api.TypeRegistryConfigurer",
            "io.cucumber.cucumberexpressions.CaptureGroupTransformer",
            "io.cucumber.cucumberexpressions.ParameterByTypeTransformer",
            "io.cucumber.cucumberexpressions.ParameterType",
            "io.cucumber.cucumberexpressions.Transformer",
            "io.cucumber.datatable.DataTableType",
            "io.cucumber.datatable.TableCellByTypeTransformer",
            "io.cucumber.datatable.TableCellTransformer",
            "io.cucumber.datatable.TableEntryByTypeTransformer",
            "io.cucumber.datatable.TableEntryTransformer",
            "io.cucumber.datatable.TableRowTransformer",
            "io.cucumber.datatable.TableTransformer",
            "io.cucumber.docstring.DocStringType",
            "java.util.Locale");

    /**
     * The parameters the annotated method replacing each transformer interface takes, for lambdas that leave their
     * types implicit and method references that name no parameters at all. Only the parameters the annotation
     * accepts are listed, which is fewer than the interface declares for {@code TableEntryByTypeTransformer}.
     */
    private static final Map<String, List<String>> TRANSFORMER_PARAMETERS = new HashMap<>();

    /**
     * The trailing `ParameterType` constructor arguments, in order, as the annotation attributes they become.
     */
    private static final List<String> PARAMETER_TYPE_FLAGS = asList(
            "useForSnippets", "preferForRegexMatch", "useRegexpMatchAsStrongTypeHint");

    static {
        TRANSFORMER_PARAMETERS.put("io.cucumber.cucumberexpressions.CaptureGroupTransformer", singletonList("String[] values"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.cucumberexpressions.ParameterByTypeTransformer", asList("String fromValue", "Type toValueType"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.cucumberexpressions.Transformer", singletonList("String value"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableCellByTypeTransformer", asList("String fromValue", "Type toValueType"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableCellTransformer", singletonList("String cell"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableEntryByTypeTransformer", asList("Map<String, String> fromValue", "Type toValueType"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableEntryTransformer", singletonList("Map<String, String> entry"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableRowTransformer", singletonList("List<String> row"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.datatable.TableTransformer", singletonList("DataTable table"));
        TRANSFORMER_PARAMETERS.put("io.cucumber.docstring.DocStringType$Transformer", singletonList("String docString"));
    }

    String displayName = "Replace `TypeRegistryConfigurer` with cucumber-java annotations";

    String description = "Cucumber-JVM 7.0.0 removed `TypeRegistryConfigurer`; replace implementations with " +
            "`@ParameterType`, `@DataTableType`, `@DocStringType` and `@Default*Transformer` annotated glue methods. " +
            "Classes whose `configureTypeRegistry` method cannot be converted in full are left untouched, " +
            "with a `TODO` comment added above the registration that could not be converted.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(TYPE_REGISTRY_CONFIGURER.getSignature(), true),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        J.ClassDeclaration c = super.visitClassDeclaration(cd, ctx);
                        if (c.getImplements() == null || c.getImplements().stream()
                                .noneMatch(TYPE_REGISTRY_CONFIGURER::matches)) {
                            return c;
                        }

                        J.MethodDeclaration configureTypeRegistry = null;
                        for (Statement statement : c.getBody().getStatements()) {
                            if (statement instanceof J.MethodDeclaration &&
                                    CONFIGURE_TYPE_REGISTRY.matches((J.MethodDeclaration) statement, c)) {
                                configureTypeRegistry = (J.MethodDeclaration) statement;
                            }
                        }
                        if (configureTypeRegistry == null || configureTypeRegistry.getBody() == null) {
                            // No registration to comment, so flag the class; the blank line the class is
                            // separated from the imports by would otherwise land between the two
                            return Comments.of(new Cursor(getCursor().getParent(), c))
                                    .comment(" " + MANUAL_MIGRATION, Comments.Placement.BEFORE, "\n");
                        }

                        Set<String> methodNames = declaredMethodNames(c);

                        Locals locals = new Locals();
                        List<GlueMethod> glueMethods = new ArrayList<>();
                        for (Statement statement : configureTypeRegistry.getBody().getStatements()) {
                            if (locals.declare(statement)) {
                                continue;
                            }
                            GlueMethod glueMethod = glueMethod(statement, methodNames, locals);
                            if (glueMethod == null) {
                                // Leave the whole class for manual migration rather than convert it halfway
                                return flagForManualMigration(c, configureTypeRegistry, statement);
                            }
                            glueMethods.add(glueMethod);
                        }
                        Statement strayLocal = locals.notReadExactlyOnce();
                        if (strayLocal != null) {
                            // Inlining a local read twice duplicates it; dropping one nothing reads loses what it does
                            return flagForManualMigration(c, configureTypeRegistry, strayLocal);
                        }

                        J.MethodDeclaration removedConfigureTypeRegistry = configureTypeRegistry;
                        c = c.withImplements(ListUtils.map(c.getImplements(), i -> TYPE_REGISTRY_CONFIGURER.matches(i) ? null : i))
                                .withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(),
                                        s -> s == removedConfigureTypeRegistry || isLocaleMethod(s) ? null : s)));
                        OBSOLETE_IMPORTS.forEach(this::maybeRemoveImport);

                        JavaParser.Builder<?, ?> javaParser = JavaParser.fromJavaVersion()
                                .classpathFromResources(ctx, "cucumber-java-7");
                        for (GlueMethod glueMethod : glueMethods) {
                            maybeAddImport(glueMethod.getAnnotationImport());
                            List<String> imports = new ArrayList<>();
                            imports.add(glueMethod.getAnnotationImport());
                            for (String parameterTypeImport : glueMethod.getParameterTypeImports()) {
                                maybeAddImport(parameterTypeImport, null, false);
                                imports.add(parameterTypeImport);
                            }
                            c = JavaTemplate.builder(glueMethod.template())
                                    .contextSensitive()
                                    .javaParser(javaParser)
                                    .imports(imports.toArray(new String[0]))
                                    .build()
                                    .apply(updateCursor(c), c.getBody().getCoordinates().lastStatement(), glueMethod.templateArguments());
                            c = c.withBody(c.getBody().withStatements(ListUtils.mapLast(c.getBody().getStatements(),
                                    s -> completeGlueMethod(s, glueMethod))));
                        }

                        doAfterVisit(new RemoveUnneededBlock().getVisitor());
                        doAfterVisit(new UnnecessaryThrows().getVisitor());
                        return c;
                    }

                    /**
                     * Comment the registration that blocked the conversion, rather than the class as a whole, as
                     * that is the line to pick the manual migration up from. Left uncommented, the class silently
                     * keeps a `TypeRegistryConfigurer` that no longer exists on the version being upgraded to.
                     */
                    private J.ClassDeclaration flagForManualMigration(J.ClassDeclaration c,
                                                                      J.MethodDeclaration configureTypeRegistry,
                                                                      Statement unconvertible) {
                        J.Block configureTypeRegistryBody = configureTypeRegistry.getBody();
                        if (configureTypeRegistryBody == null) {
                            return c;
                        }
                        Cursor classCursor = new Cursor(getCursor().getParent(), c);
                        Cursor bodyCursor = new Cursor(new Cursor(classCursor, c.getBody()), configureTypeRegistry);
                        Statement commented = Comments
                                .of(new Cursor(new Cursor(bodyCursor, configureTypeRegistryBody), unconvertible))
                                .comment(" " + MANUAL_MIGRATION);
                        if (commented == unconvertible) {
                            // Already commented, on an earlier cycle
                            return c;
                        }
                        return c.withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(), s -> {
                            if (s != configureTypeRegistry) {
                                return s;
                            }
                            return configureTypeRegistry.withBody(configureTypeRegistryBody.withStatements(
                                    ListUtils.map(configureTypeRegistryBody.getStatements(),
                                            statement -> statement == unconvertible ? commented : statement)));
                        })));
                    }

                    private Statement completeGlueMethod(Statement statement, GlueMethod glueMethod) {
                        if (!(statement instanceof J.MethodDeclaration)) {
                            return statement;
                        }
                        J.MethodDeclaration method = (J.MethodDeclaration) statement;
                        if (glueMethod.getReturnTypeTree() != null) {
                            method = retypeReturnType(method, glueMethod.getReturnTypeTree());
                        }
                        if (glueMethod.getReference() != null && glueMethod.getReferenceKind() != null) {
                            method = expandMemberReference(method, glueMethod.getReference(), glueMethod.getReferenceKind());
                        }
                        return method;
                    }

                    /**
                     * The template is parsed without the project on its classpath, so the return type of the new method comes
                     * back unattributed; copy the type back over from the {@code Foo.class} argument it was taken from.
                     */
                    private J.MethodDeclaration retypeReturnType(J.MethodDeclaration method, TypeTree returnTypeTree) {
                        JavaType returnType = returnTypeTree.getType();
                        if (method.getReturnTypeExpression() == null || method.getMethodType() == null || returnType == null) {
                            return method;
                        }
                        JavaType.Method methodType = method.getMethodType().withReturnType(returnType);
                        return method
                                .withReturnTypeExpression(returnTypeTree.withPrefix(method.getReturnTypeExpression().getPrefix()))
                                .withMethodType(methodType)
                                .withName(method.getName().withType(methodType));
                    }

                    /**
                     * A method reference cannot be templated in as an expression the way a lambda body can, as the
                     * template is parsed without the type it refers to on the classpath.
                     */
                    private J.MethodDeclaration expandMemberReference(J.MethodDeclaration method,
                                                                     J.MemberReference reference, MemberReferences.Kind kind) {
                        List<Expression> arguments = new ArrayList<>();
                        for (Statement parameter : method.getParameters()) {
                            if (parameter instanceof J.VariableDeclarations) {
                                arguments.add(((J.VariableDeclarations) parameter).getVariables().get(0).getName()
                                        .withPrefix(Space.EMPTY));
                            }
                        }
                        J.Block body = method.getBody();
                        if (body == null || body.getStatements().size() != 1 ||
                                !(body.getStatements().get(0) instanceof J.Return)) {
                            return method;
                        }
                        J.Return placeholder = (J.Return) body.getStatements().get(0);
                        Expression invocation = MemberReferences.invocation(reference, kind, arguments)
                                .withPrefix(Space.SINGLE_SPACE);
                        return method.withBody(body.withStatements(singletonList(
                                placeholder.withExpression(invocation))));
                    }

                    private @Nullable GlueMethod glueMethod(Statement statement, Set<String> methodNames, Locals locals) {
                        if (!(statement instanceof J.MethodInvocation)) {
                            return null;
                        }
                        J.MethodInvocation definition = (J.MethodInvocation) statement;
                        if (definition.getSelect() == null || !TYPE_REGISTRY.matches(definition.getSelect().getType()) ||
                                definition.getArguments().size() != 1) {
                            return null;
                        }
                        Expression argument = locals.read(definition.getArguments().get(0));
                        switch (definition.getSimpleName()) {
                            case "setDefaultParameterTransformer":
                                return defaultTransformer("DefaultParameterTransformer", argument, methodNames);
                            case "setDefaultDataTableCellTransformer":
                                return defaultTransformer("DefaultDataTableCellTransformer", argument, methodNames);
                            case "setDefaultDataTableEntryTransformer":
                                // The annotation camel cases the entry headers first, where the registry passed them through
                                return defaultTransformer("DefaultDataTableEntryTransformer", argument, methodNames,
                                        "headersToProperties = false");
                            default:
                                break;
                        }
                        if (!(argument instanceof J.NewClass)) {
                            return null;
                        }
                        List<Expression> arguments = ListUtils.map(((J.NewClass) argument).getArguments(), locals::read);
                        if (arguments == null) {
                            return null;
                        }
                        switch (definition.getSimpleName()) {
                            case "defineParameterType":
                                return parameterType(arguments, methodNames);
                            case "defineDataTableType":
                                return dataTableType(arguments, methodNames);
                            case "defineDocStringType":
                                return docStringType(arguments, methodNames);
                            default:
                                return null;
                        }
                    }

                    private @Nullable GlueMethod parameterType(List<Expression> arguments, Set<String> methodNames) {
                        // ParameterType(String name, String regexp, Class<T> type, Transformer<T> transformer
                        //         [, boolean useForSnippets, boolean preferForRegexpMatch[, boolean strongTypeHint]])
                        if (arguments.size() != 4 && arguments.size() != 6 && arguments.size() != 7) {
                            return null;
                        }
                        String name = stringLiteral(arguments.get(0));
                        String regexp = literalSource(arguments.get(1));
                        TypeTree returnType = classLiteral(arguments.get(2));
                        if (name == null || regexp == null || returnType == null) {
                            return null;
                        }
                        String methodName = uniqueMethodName(sanitize(name, "parameterType"), methodNames);
                        List<String> attributes = new ArrayList<>();
                        if (!methodName.equals(name)) {
                            attributes.add("name = " + literalSource(arguments.get(0)));
                        }
                        for (int i = 4; i < arguments.size(); i++) {
                            Boolean value = booleanLiteral(arguments.get(i));
                            if (value == null) {
                                return null;
                            }
                            // Each flag defaults to false on the annotation, where the constructor defaults
                            // `useForSnippets` to true, so only a true has to be spelled out
                            if (value) {
                                attributes.add(PARAMETER_TYPE_FLAGS.get(i - 4) + " = true");
                            }
                        }
                        String annotation = attributes.isEmpty() ?
                                "@ParameterType(" + regexp + ")" :
                                "@ParameterType(value = " + regexp + ", " + String.join(", ", attributes) + ")";
                        return glueMethod(annotation, IO_CUCUMBER_JAVA_PARAMETER_TYPE, returnType, methodName,
                                arguments.get(3));
                    }

                    private @Nullable GlueMethod dataTableType(List<Expression> arguments, Set<String> methodNames) {
                        // DataTableType(Class<T> type, Table[Entry|Row|Cell]Transformer<T> transformer)
                        if (arguments.size() != 2) {
                            return null;
                        }
                        TypeTree returnType = classLiteral(arguments.get(0));
                        if (returnType == null) {
                            return null;
                        }
                        String methodName = uniqueMethodName(
                                sanitize(decapitalize(returnType.printTrimmed(getCursor())), "parameterType"), methodNames);
                        return glueMethod("@DataTableType", IO_CUCUMBER_JAVA_DATA_TABLE_TYPE, returnType, methodName,
                                arguments.get(1));
                    }

                    private @Nullable GlueMethod docStringType(List<Expression> arguments, Set<String> methodNames) {
                        // DocStringType(Class<T> type, String contentType, Transformer<T> transformer)
                        if (arguments.size() != 3) {
                            return null;
                        }
                        TypeTree returnType = classLiteral(arguments.get(0));
                        String contentType = literalSource(arguments.get(1));
                        if (returnType == null || contentType == null) {
                            return null;
                        }
                        String methodName = uniqueMethodName(
                                sanitize(decapitalize(returnType.printTrimmed(getCursor())), "parameterType"), methodNames);
                        return glueMethod("@DocStringType(contentType = " + contentType + ")",
                                IO_CUCUMBER_JAVA_DOC_STRING_TYPE, returnType, methodName, arguments.get(2));
                    }

                    private @Nullable GlueMethod defaultTransformer(String annotation, Expression transformer,
                                                                    Set<String> methodNames, String... attributes) {
                        String methodName = uniqueMethodName(decapitalize(annotation), methodNames);
                        String suffix = attributes.length == 0 ? "" : "(" + String.join(", ", attributes) + ")";
                        return glueMethod("@" + annotation + suffix, IO_CUCUMBER_JAVA + annotation, null, methodName,
                                transformer);
                    }

                    private @Nullable GlueMethod glueMethod(String annotation, String annotationImport,
                                                            @Nullable TypeTree returnTypeTree, String methodName,
                                                            Expression transformer) {
                        JavaType castInterface = null;
                        if (transformer instanceof J.TypeCast) {
                            castInterface = ((J.TypeCast) transformer).getClazz().getTree().getType();
                            transformer = ((J.TypeCast) transformer).getExpression();
                        }

                        List<? extends J> declaredParameters = null;
                        J body = null;
                        J.MemberReference reference = null;
                        JavaType functionalInterface;
                        if (transformer instanceof J.Lambda) {
                            J.Lambda lambda = (J.Lambda) transformer;
                            declaredParameters = lambda.getParameters().getParameters();
                            body = lambda.getBody();
                            functionalInterface = lambda.getType();
                        } else if (transformer instanceof J.NewClass && ((J.NewClass) transformer).getBody() != null) {
                            J.NewClass anonymousClass = (J.NewClass) transformer;
                            List<Statement> members = anonymousClass.getBody().getStatements();
                            if (members.size() != 1 || !(members.get(0) instanceof J.MethodDeclaration)) {
                                return null;
                            }
                            J.MethodDeclaration transform = (J.MethodDeclaration) members.get(0);
                            if (transform.getBody() == null) {
                                return null;
                            }
                            declaredParameters = transform.getParameters();
                            body = transform.getBody();
                            functionalInterface = anonymousClass.getClazz() == null ? null : anonymousClass.getClazz().getType();
                        } else if (transformer instanceof J.MemberReference) {
                            reference = (J.MemberReference) transformer;
                            functionalInterface = reference.getType();
                        } else {
                            return null;
                        }
                        if (castInterface != null) {
                            functionalInterface = castInterface;
                        }

                        List<String> transformerParameters = TRANSFORMER_PARAMETERS.get(fullyQualifiedName(functionalInterface));
                        MemberReferences.Kind referenceKind = null;
                        List<String> parameters;
                        if (declaredParameters == null) {
                            if (transformerParameters == null) {
                                return null;
                            }
                            referenceKind = MemberReferences.kind(reference, transformerParameters.size());
                            if (referenceKind == null) {
                                return null;
                            }
                            parameters = transformerParameters;
                        } else {
                            parameters = parameters(declaredParameters, transformerParameters, body);
                        }
                        if (parameters == null) {
                            return null;
                        }

                        Set<String> parameterTypeImports = new LinkedHashSet<>();
                        for (String parameter : parameters) {
                            String parameterTypeImport = PARAMETER_TYPE_IMPORTS.get(typeOf(parameter));
                            if (parameterTypeImport != null) {
                                parameterTypeImports.add(parameterTypeImport);
                            }
                        }
                        String returnType = returnTypeTree == null ? "Object" : returnTypeTree.printTrimmed(getCursor());
                        return new GlueMethod(annotation, annotationImport, parameterTypeImports, returnTypeTree,
                                returnType, methodName, String.join(", ", parameters), body, reference, referenceKind);
                    }

                    /**
                     * @param transformerParameters the parameters the annotated method takes, which override any the
                     *                              lambda or anonymous class declares, and cut off any it declares
                     *                              beyond them
                     */
                    private @Nullable List<String> parameters(List<? extends J> declaredParameters,
                                                              @Nullable List<String> transformerParameters,
                                                              @Nullable J body) {
                        List<String> parameters = new ArrayList<>();
                        for (int i = 0; i < declaredParameters.size(); i++) {
                            J parameter = declaredParameters.get(i);
                            if (parameter instanceof J.Empty) {
                                continue;
                            }
                            if (!(parameter instanceof J.VariableDeclarations)) {
                                return null;
                            }
                            J.VariableDeclarations declaration = (J.VariableDeclarations) parameter;
                            if (declaration.getVariables().size() != 1) {
                                return null;
                            }
                            J.Identifier name = declaration.getVariables().get(0).getName();
                            if (transformerParameters != null && transformerParameters.size() <= i) {
                                if (body == null || isReferenced(body, name)) {
                                    return null;
                                }
                                continue;
                            }
                            String type = transformerParameters != null ? typeOf(transformerParameters.get(i)) :
                                    declaration.getTypeExpression() == null ? null :
                                            declaration.getTypeExpression().printTrimmed(getCursor());
                            if (type == null) {
                                return null;
                            }
                            parameters.add(type + " " + name.getSimpleName());
                        }
                        return parameters;
                    }

                    /**
                     * @return the type named by a {@code Foo.class} expression
                     */
                    private @Nullable TypeTree classLiteral(Expression expression) {
                        if (!(expression instanceof J.FieldAccess) || !"class".equals(((J.FieldAccess) expression).getSimpleName())) {
                            return null;
                        }
                        Expression target = ((J.FieldAccess) expression).getTarget();
                        return target instanceof TypeTree ? (TypeTree) target : null;
                    }
                });
    }

    private static boolean isReferenced(J body, J.Identifier name) {
        return !VariableReferences.findRhsReferences(body, name).isEmpty() ||
                !VariableReferences.findLhsReferences(body, name).isEmpty();
    }

    private static boolean isLocaleMethod(Statement statement) {
        if (!(statement instanceof J.MethodDeclaration)) {
            return false;
        }
        J.MethodDeclaration method = (J.MethodDeclaration) statement;
        return "locale".equals(method.getSimpleName()) && method.getParameters().stream()
                .noneMatch(J.VariableDeclarations.class::isInstance);
    }

    private static @Nullable Boolean booleanLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof Boolean ?
                (Boolean) ((J.Literal) expression).getValue() : null;
    }

}

/**
 * The local variables a {@code configureTypeRegistry} method declares, so that a transformer or a registration
 * assigned to one first can still be inlined into the glue method it becomes.
 */
class Locals {

    private final Map<String, Local> locals = new LinkedHashMap<>();

    /**
     * @return whether the statement was recorded here rather than left to be registered
     */
    boolean declare(Statement statement) {
        if (!(statement instanceof J.VariableDeclarations)) {
            return false;
        }
        List<J.VariableDeclarations.NamedVariable> variables = ((J.VariableDeclarations) statement).getVariables();
        if (variables.stream().anyMatch(variable -> variable.getInitializer() == null)) {
            return false;
        }
        for (J.VariableDeclarations.NamedVariable variable : variables) {
            locals.put(variable.getSimpleName(), new Local(statement, variable.getInitializer()));
        }
        return true;
    }

    Expression read(Expression expression) {
        if (!(expression instanceof J.Identifier)) {
            return expression;
        }
        Local local = locals.get(((J.Identifier) expression).getSimpleName());
        if (local == null) {
            return expression;
        }
        local.reads++;
        return read(local.initializer);
    }

    @Nullable
    Statement notReadExactlyOnce() {
        for (Local local : locals.values()) {
            if (local.reads != 1) {
                return local.declaration;
            }
        }
        return null;
    }

    @RequiredArgsConstructor
    private static class Local {
        private final Statement declaration;
        private final Expression initializer;
        private int reads;
    }

}

@Value
class GlueMethod {

    String annotation;
    String annotationImport;
    Collection<String> parameterTypeImports;

    @Nullable
    TypeTree returnTypeTree;

    String returnType;
    String methodName;
    String parameters;

    @Nullable
    J body;

    J.@Nullable MemberReference reference;
    MemberReferences.@Nullable Kind referenceKind;

    String template() {
        // The body is passed as a template parameter to retain its type attribution
        String statement = body == null ? "return null;" : body instanceof J.Block ? "#{any()}" : "return #{any()};";
        return annotation + "\npublic " + returnType + " " + methodName + "(" + parameters + ") throws Exception {\n" +
                statement + "\n}";
    }

    Object[] templateArguments() {
        return body == null ? new Object[0] : new Object[]{body};
    }

}
