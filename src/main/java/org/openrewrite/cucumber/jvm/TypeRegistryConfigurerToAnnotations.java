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
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.staticanalysis.RemoveUnneededBlock;
import org.openrewrite.staticanalysis.UnnecessaryThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


@EqualsAndHashCode(callSuper = false)
@Value
public class TypeRegistryConfigurerToAnnotations extends Recipe {

    private static final String CUCUMBER_API_TYPE_REGISTRY = "cucumber.api.TypeRegistry";
    private static final String CUCUMBER_API_TYPE_REGISTRY_CONFIGURER = "cucumber.api.TypeRegistryConfigurer";
    private static final String CORE_API_TYPE_REGISTRY = "io.cucumber.core.api.TypeRegistry";
    private static final String CORE_API_TYPE_REGISTRY_CONFIGURER = "io.cucumber.core.api.TypeRegistryConfigurer";

    private static final String IO_CUCUMBER_JAVA_PARAMETER_TYPE = "io.cucumber.java.ParameterType";
    private static final String IO_CUCUMBER_JAVA_DATA_TABLE_TYPE = "io.cucumber.java.DataTableType";
    private static final String IO_CUCUMBER_JAVA_DOC_STRING_TYPE = "io.cucumber.java.DocStringType";

    private static final List<String> OBSOLETE_IMPORTS = Arrays.asList(
            CUCUMBER_API_TYPE_REGISTRY,
            CUCUMBER_API_TYPE_REGISTRY_CONFIGURER,
            CORE_API_TYPE_REGISTRY,
            CORE_API_TYPE_REGISTRY_CONFIGURER,
            "io.cucumber.cucumberexpressions.CaptureGroupTransformer",
            "io.cucumber.cucumberexpressions.ParameterType",
            "io.cucumber.cucumberexpressions.Transformer",
            "io.cucumber.datatable.DataTableType",
            "io.cucumber.datatable.TableCellTransformer",
            "io.cucumber.datatable.TableEntryTransformer",
            "io.cucumber.datatable.TableRowTransformer",
            "io.cucumber.datatable.TableTransformer",
            "io.cucumber.docstring.DocStringType",
            "java.util.Locale");

    /**
     * The single parameter type of each transformer interface a {@code define*Type} call can be handed, so that
     * lambdas which leave their parameter types implicit can still be turned into method declarations.
     */
    private static final Map<String, String> TRANSFORMER_PARAMETER_TYPES = new HashMap<>();

    private static final Map<String, String> PARAMETER_TYPE_IMPORTS = new HashMap<>();

    static {
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.cucumberexpressions.CaptureGroupTransformer", "String[]");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.cucumberexpressions.Transformer", "String");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.datatable.TableCellTransformer", "String");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.datatable.TableEntryTransformer", "Map<String, String>");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.datatable.TableRowTransformer", "List<String>");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.datatable.TableTransformer", "DataTable");
        TRANSFORMER_PARAMETER_TYPES.put("io.cucumber.docstring.DocStringType$Transformer", "String");

        PARAMETER_TYPE_IMPORTS.put("DataTable", "io.cucumber.datatable.DataTable");
        PARAMETER_TYPE_IMPORTS.put("List<String>", "java.util.List");
        PARAMETER_TYPE_IMPORTS.put("Map<String, String>", "java.util.Map");
    }

    String displayName = "Replace `TypeRegistryConfigurer` with cucumber-java annotations";

    String description = "Cucumber-JVM 7.0.0 removed `TypeRegistryConfigurer`; replace implementations with " +
            "`@ParameterType`, `@DataTableType` and `@DocStringType` annotated glue methods. " +
            "Classes whose `configureTypeRegistry` method cannot be converted in full are left untouched.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(15);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                Preconditions.or(
                        new UsesType<>(CUCUMBER_API_TYPE_REGISTRY_CONFIGURER, true),
                        new UsesType<>(CORE_API_TYPE_REGISTRY_CONFIGURER, true)),
                new TypeRegistryConfigurerVisitor());
    }

    static final class TypeRegistryConfigurerVisitor extends JavaIsoVisitor<ExecutionContext> {

        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
            J.ClassDeclaration c = super.visitClassDeclaration(cd, ctx);
            if (c.getImplements() == null || c.getImplements().stream()
                    .noneMatch(i -> isTypeRegistryConfigurer(i.getType()))) {
                return c;
            }

            J.MethodDeclaration configureTypeRegistry = null;
            for (Statement statement : c.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration &&
                        "configureTypeRegistry".equals(((J.MethodDeclaration) statement).getSimpleName())) {
                    configureTypeRegistry = (J.MethodDeclaration) statement;
                }
            }
            if (configureTypeRegistry == null || configureTypeRegistry.getBody() == null) {
                return c;
            }

            Set<String> methodNames = new LinkedHashSet<>();
            for (Statement statement : c.getBody().getStatements()) {
                if (statement instanceof J.MethodDeclaration) {
                    methodNames.add(((J.MethodDeclaration) statement).getSimpleName());
                }
            }

            List<GlueMethod> glueMethods = new ArrayList<>();
            for (Statement statement : configureTypeRegistry.getBody().getStatements()) {
                GlueMethod glueMethod = glueMethod(statement, methodNames);
                if (glueMethod == null) {
                    // Leave the whole class for manual migration rather than convert it halfway
                    return c;
                }
                glueMethods.add(glueMethod);
            }

            J.MethodDeclaration removedConfigureTypeRegistry = configureTypeRegistry;
            c = c.withImplements(ListUtils.map(c.getImplements(), i -> isTypeRegistryConfigurer(i.getType()) ? null : i))
                    .withBody(c.getBody().withStatements(ListUtils.map(c.getBody().getStatements(),
                            s -> s == removedConfigureTypeRegistry || isLocaleMethod(s) ? null : s)));
            OBSOLETE_IMPORTS.forEach(this::maybeRemoveImport);

            for (GlueMethod glueMethod : glueMethods) {
                maybeAddImport(glueMethod.getAnnotationImport());
                String[] imports = {glueMethod.getAnnotationImport()};
                if (glueMethod.getParameterTypeImport() != null) {
                    maybeAddImport(glueMethod.getParameterTypeImport(), null, false);
                    imports = new String[]{glueMethod.getAnnotationImport(), glueMethod.getParameterTypeImport()};
                }
                c = JavaTemplate.builder(glueMethod.template())
                        .contextSensitive()
                        .javaParser(JavaParser.fromJavaVersion().classpath("cucumber-java"))
                        .imports(imports)
                        .build()
                        .apply(updateCursor(c), c.getBody().getCoordinates().lastStatement(), glueMethod.getBody());
                c = c.withBody(c.getBody().withStatements(ListUtils.mapLast(c.getBody().getStatements(),
                        s -> retypeReturnType(s, glueMethod.getReturnTypeTree()))));
            }

            doAfterVisit(new RemoveUnneededBlock().getVisitor());
            doAfterVisit(new UnnecessaryThrows().getVisitor());
            return c;
        }

        /**
         * The template is parsed without the project on its classpath, so the return type of the new method comes
         * back unattributed; copy the type back over from the {@code Foo.class} argument it was taken from.
         */
        private Statement retypeReturnType(Statement statement, TypeTree returnTypeTree) {
            if (!(statement instanceof J.MethodDeclaration)) {
                return statement;
            }
            J.MethodDeclaration method = (J.MethodDeclaration) statement;
            JavaType returnType = returnTypeTree.getType();
            if (method.getReturnTypeExpression() == null || method.getMethodType() == null || returnType == null) {
                return statement;
            }
            JavaType.Method methodType = method.getMethodType().withReturnType(returnType);
            return method
                    .withReturnTypeExpression(returnTypeTree.withPrefix(method.getReturnTypeExpression().getPrefix()))
                    .withMethodType(methodType)
                    .withName(method.getName().withType(methodType));
        }

        private @Nullable GlueMethod glueMethod(Statement statement, Set<String> methodNames) {
            if (!(statement instanceof J.MethodInvocation)) {
                return null;
            }
            J.MethodInvocation definition = (J.MethodInvocation) statement;
            if (definition.getSelect() == null || !isTypeRegistry(definition.getSelect().getType()) ||
                    definition.getArguments().size() != 1 ||
                    !(definition.getArguments().get(0) instanceof J.NewClass)) {
                return null;
            }
            List<Expression> arguments = ((J.NewClass) definition.getArguments().get(0)).getArguments();
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
            // ParameterType(String name, String regexp, Class<T> type, Transformer<T> transformer)
            if (arguments.size() != 4) {
                return null;
            }
            String name = stringLiteral(arguments.get(0));
            String regexp = literalSource(arguments.get(1));
            TypeTree returnType = classLiteral(arguments.get(2));
            if (name == null || regexp == null || returnType == null) {
                return null;
            }
            String methodName = uniqueMethodName(sanitize(name), methodNames);
            String annotation = methodName.equals(name) ?
                    "@ParameterType(" + regexp + ")" :
                    "@ParameterType(value = " + regexp + ", name = " + literalSource(arguments.get(0)) + ")";
            return glueMethod(annotation, IO_CUCUMBER_JAVA_PARAMETER_TYPE, returnType, methodName, arguments.get(3));
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
            String methodName = uniqueMethodName(decapitalize(returnType.printTrimmed(getCursor())), methodNames);
            return glueMethod("@DataTableType", IO_CUCUMBER_JAVA_DATA_TABLE_TYPE, returnType, methodName, arguments.get(1));
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
            String methodName = uniqueMethodName(decapitalize(returnType.printTrimmed(getCursor())), methodNames);
            return glueMethod("@DocStringType(contentType = " + contentType + ")",
                    IO_CUCUMBER_JAVA_DOC_STRING_TYPE, returnType, methodName, arguments.get(2));
        }

        private @Nullable GlueMethod glueMethod(String annotation, String annotationImport, TypeTree returnType,
                                                String methodName, Expression transformer) {
            String castParameterType = null;
            if (transformer instanceof J.TypeCast) {
                castParameterType = TRANSFORMER_PARAMETER_TYPES.get(fullyQualifiedName(
                        ((J.TypeCast) transformer).getClazz().getTree().getType()));
                transformer = ((J.TypeCast) transformer).getExpression();
            }
            if (!(transformer instanceof J.Lambda)) {
                return null;
            }
            J.Lambda lambda = (J.Lambda) transformer;
            String implicitParameterType = castParameterType != null ? castParameterType :
                    TRANSFORMER_PARAMETER_TYPES.get(fullyQualifiedName(lambda.getType()));
            String parameters = parameters(lambda, implicitParameterType);
            if (parameters == null) {
                return null;
            }
            return new GlueMethod(annotation, annotationImport, PARAMETER_TYPE_IMPORTS.get(implicitParameterType),
                    returnType, returnType.printTrimmed(getCursor()), methodName, parameters, lambda.getBody());
        }

        /**
         * @param implicitParameterType the parameter type of the transformer interface the lambda implements, used
         *                              where the lambda itself does not declare its parameter types
         */
        private @Nullable String parameters(J.Lambda lambda, @Nullable String implicitParameterType) {
            List<String> parameters = new ArrayList<>();
            for (J parameter : lambda.getParameters().getParameters()) {
                if (!(parameter instanceof J.VariableDeclarations)) {
                    return null;
                }
                J.VariableDeclarations declaration = (J.VariableDeclarations) parameter;
                if (declaration.getVariables().size() != 1) {
                    return null;
                }
                String type;
                if (declaration.getTypeExpression() != null) {
                    type = declaration.getTypeExpression().printTrimmed(getCursor());
                } else if (implicitParameterType != null && parameters.isEmpty()) {
                    type = implicitParameterType;
                } else {
                    return null;
                }
                parameters.add(type + " " + declaration.getVariables().get(0).getSimpleName());
            }
            return String.join(", ", parameters);
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
    }

    private static @Nullable String fullyQualifiedName(@Nullable JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    private static boolean isTypeRegistry(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, CUCUMBER_API_TYPE_REGISTRY) ||
                TypeUtils.isOfClassType(type, CORE_API_TYPE_REGISTRY);
    }

    private static boolean isTypeRegistryConfigurer(@Nullable JavaType type) {
        return TypeUtils.isOfClassType(type, CUCUMBER_API_TYPE_REGISTRY_CONFIGURER) ||
                TypeUtils.isOfClassType(type, CORE_API_TYPE_REGISTRY_CONFIGURER);
    }

    private static boolean isLocaleMethod(Statement statement) {
        if (!(statement instanceof J.MethodDeclaration)) {
            return false;
        }
        J.MethodDeclaration method = (J.MethodDeclaration) statement;
        return "locale".equals(method.getSimpleName()) && method.getParameters().stream()
                .noneMatch(J.VariableDeclarations.class::isInstance);
    }

    private static @Nullable String stringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof String ?
                (String) ((J.Literal) expression).getValue() : null;
    }

    private static @Nullable String literalSource(Expression expression) {
        return stringLiteral(expression) == null ? null : ((J.Literal) expression).getValueSource();
    }

    private static String sanitize(String parameterTypeName) {
        StringBuilder methodName = new StringBuilder();
        for (char c : parameterTypeName.toCharArray()) {
            if (methodName.length() == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) {
                methodName.append(c);
            }
        }
        return methodName.length() == 0 ? "parameterType" : methodName.toString();
    }

    private static String decapitalize(String typeName) {
        String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private static String uniqueMethodName(String candidate, Set<String> methodNames) {
        String methodName = candidate;
        for (int i = 2; !methodNames.add(methodName); i++) {
            methodName = candidate + i;
        }
        return methodName;
    }

}

@Value
class GlueMethod {

    String annotation;
    String annotationImport;
    @Nullable
    String parameterTypeImport;
    TypeTree returnTypeTree;
    String returnType;
    String methodName;
    String parameters;
    J body;

    String template() {
        // The body is passed as a template parameter to retain its type attribution
        String statement = body instanceof J.Block ? "#{any()}" : "return #{any()};";
        return annotation + "\npublic " + returnType + " " + methodName + "(" + parameters + ") throws Exception {\n" +
                statement + "\n}";
    }

}
