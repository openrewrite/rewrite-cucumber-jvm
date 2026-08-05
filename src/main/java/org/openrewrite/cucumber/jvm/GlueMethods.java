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

import org.jspecify.annotations.Nullable;
import org.openrewrite.internal.StringUtils;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeUtils;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared by the recipes that turn a registered transformation into a `cucumber-java` annotated method, whether it
 * was registered through `TypeRegistryConfigurer` or through `cucumber-java8` lambda glue.
 */
final class GlueMethods {

    /**
     * The imports the parameters of a generated glue method need, keyed by the type as it is declared.
     */
    static final Map<String, String> PARAMETER_TYPE_IMPORTS = new HashMap<>();

    static {
        PARAMETER_TYPE_IMPORTS.put("DataTable", "io.cucumber.datatable.DataTable");
        PARAMETER_TYPE_IMPORTS.put("List<String>", "java.util.List");
        PARAMETER_TYPE_IMPORTS.put("Map<String, String>", "java.util.Map");
        PARAMETER_TYPE_IMPORTS.put("Type", "java.lang.reflect.Type");
    }

    private GlueMethods() {
    }

    static @Nullable String stringLiteral(Expression expression) {
        return expression instanceof J.Literal && ((J.Literal) expression).getValue() instanceof String ?
                (String) ((J.Literal) expression).getValue() : null;
    }

    /**
     * @return the string literal as it was written, escapes and all, rather than the value it stands for
     */
    static @Nullable String literalSource(Expression expression) {
        return stringLiteral(expression) == null ? null : ((J.Literal) expression).getValueSource();
    }

    static @Nullable String fullyQualifiedName(@Nullable JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        return fullyQualified == null ? null : fullyQualified.getFullyQualifiedName();
    }

    /**
     * @return the name with everything a method name cannot hold dropped, as a cucumber expression or content type
     * is free to contain spaces and punctuation
     */
    static String sanitize(String name, String fallback) {
        StringBuilder methodName = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (methodName.length() == 0 ? Character.isJavaIdentifierStart(c) : Character.isJavaIdentifierPart(c)) {
                methodName.append(c);
            }
        }
        return methodName.length() == 0 ? fallback : methodName.toString();
    }

    /**
     * @return the name a type reads as when it names a method rather than a type, dropping any package qualification,
     * enclosing classes and type arguments
     */
    static String decapitalize(String typeName) {
        int typeArguments = typeName.indexOf('<');
        String rawName = typeArguments == -1 ? typeName : typeName.substring(0, typeArguments);
        return StringUtils.uncapitalize(rawName.substring(rawName.lastIndexOf('.') + 1));
    }

    static Set<String> declaredMethodNames(J.ClassDeclaration classDeclaration) {
        Set<String> methodNames = new LinkedHashSet<>();
        for (Statement statement : classDeclaration.getBody().getStatements()) {
            if (statement instanceof J.MethodDeclaration) {
                methodNames.add(((J.MethodDeclaration) statement).getSimpleName());
            }
        }
        return methodNames;
    }

    /**
     * Claims the name for the method about to be generated, so that a class registering several transformations to
     * the same type gets a distinct name for each.
     */
    static String uniqueMethodName(String candidate, Set<String> methodNames) {
        String methodName = candidate;
        for (int i = 2; !methodNames.add(methodName); i++) {
            methodName = candidate + i;
        }
        return methodName;
    }

    /**
     * @return the type of a {@code "Type name"} parameter declaration
     */
    static String typeOf(String parameter) {
        return parameter.substring(0, parameter.lastIndexOf(' '));
    }

}
