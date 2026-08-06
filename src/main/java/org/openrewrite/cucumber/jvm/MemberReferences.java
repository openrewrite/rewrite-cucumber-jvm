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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.openrewrite.Tree.randomId;

/**
 * A method reference brings no parameters or body of its own to the method that replaces it, the way a lambda does;
 * both have to be recovered from the method it refers to, and from the interface it implements.
 */
class MemberReferences {

    enum Kind {
        CONSTRUCTOR, STATIC, BOUND, UNBOUND
    }

    /**
     * @return how to call the method reference to stand in for a method taking {@code parameterCount} parameters,
     * or {@code null} where that cannot be told with certainty
     */
    static @Nullable Kind kind(J.@Nullable MemberReference reference, int parameterCount) {
        if (reference == null || reference.getMethodType() == null) {
            return null;
        }
        JavaType.Method methodType = reference.getMethodType();
        int arity = methodType.getParameterTypes().size();
        Expression containing = reference.getContaining();
        if (methodType.isConstructor()) {
            boolean namesAClass = containing instanceof J.Identifier || containing instanceof J.FieldAccess ||
                    containing instanceof J.ParameterizedType;
            return namesAClass && arity == parameterCount ? Kind.CONSTRUCTOR : null;
        }
        if (methodType.hasFlags(Flag.Static)) {
            return arity == parameterCount ? Kind.STATIC : null;
        }
        if (isTypeReference(containing)) {
            // An unbound reference such as `String::trim` takes its receiver from the first parameter
            return 0 < parameterCount && arity == parameterCount - 1 ? Kind.UNBOUND : null;
        }
        return arity == parameterCount ? Kind.BOUND : null;
    }

    /**
     * @return the parameter types of the interface the reference implements, to declare the parameters of the method
     * replacing it with, or {@code null} where one of them has no name to declare a parameter with
     */
    static @Nullable List<JavaType.Class> functionalInterfaceParameters(J.MemberReference reference) {
        JavaType functionalInterface = reference.getType();
        if (functionalInterface instanceof JavaType.Parameterized) {
            List<JavaType.Class> parameters = new ArrayList<>();
            for (JavaType typeParameter : ((JavaType.Parameterized) functionalInterface).getTypeParameters()) {
                // A wildcard, type variable or parameterized type has no simple name to declare a parameter with
                if (!(typeParameter instanceof JavaType.Class)) {
                    return null;
                }
                parameters.add((JavaType.Class) typeParameter);
            }
            return parameters;
        }
        // An interface with no type parameters, such as `StepDefinitionBody.A0`, declares no parameters either
        return functionalInterface instanceof JavaType.FullyQualified ? emptyList() : null;
    }

    /**
     * @return the names to give the parameters of the method replacing the reference, taken from the method it
     * refers to, and numbered where those names are not all there to take
     */
    static List<String> parameterNames(J.MemberReference reference, Kind kind, int parameterCount) {
        // A bound or static reference calls through the name it is written on, which a parameter of that same name
        // would shadow in the body the call becomes; an unbound one calls through a parameter instead
        String shadowed = kind == Kind.UNBOUND ? null : leadingName(reference.getContaining());
        List<String> names = new ArrayList<>();
        if (kind == Kind.UNBOUND) {
            names.add(receiverName(reference.getContaining().getType()));
        }
        names.addAll(Objects.requireNonNull(reference.getMethodType()).getParameterNames());
        if (names.size() != parameterCount) {
            return numberedNames(parameterCount, shadowed);
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String name : names) {
            if (!isIdentifier(name) || name.equals(shadowed) || !distinct.add(name)) {
                return numberedNames(parameterCount, shadowed);
            }
        }
        return names;
    }

    /**
     * @return the body of the method replacing the reference: a call of the method it refers to, on the parameters
     * that method now declares
     */
    static Expression body(J.MemberReference reference, Kind kind, List<String> parameterNames) {
        JavaType.Method methodType = Objects.requireNonNull(reference.getMethodType());
        List<Expression> arguments = new ArrayList<>();
        for (int i = 0; i < parameterNames.size(); i++) {
            JavaType type = kind == Kind.UNBOUND ?
                    i == 0 ? reference.getContaining().getType() : methodType.getParameterTypes().get(i - 1) :
                    methodType.getParameterTypes().get(i);
            arguments.add(new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                    parameterNames.get(i), type, null));
        }
        return invocation(reference, kind, arguments);
    }

    static Expression invocation(J.MemberReference reference, Kind kind, List<Expression> arguments) {
        JavaType.Method methodType = Objects.requireNonNull(reference.getMethodType());
        Expression containing = reference.getContaining();
        if (kind == Kind.CONSTRUCTOR) {
            return new J.NewClass(randomId(), Space.EMPTY, Markers.EMPTY, null, Space.EMPTY,
                    ((TypeTree) containing).withPrefix(Space.SINGLE_SPACE),
                    arguments(arguments), null, methodType);
        }
        Expression select = kind == Kind.UNBOUND ? arguments.get(0) : containing.withPrefix(Space.EMPTY);
        List<Expression> invocationArguments = kind == Kind.UNBOUND ?
                arguments.subList(1, arguments.size()) : arguments;
        J.Identifier name = new J.Identifier(randomId(), Space.EMPTY, Markers.EMPTY, emptyList(),
                reference.getReference().getSimpleName(), methodType, null);
        return new J.MethodInvocation(randomId(), Space.EMPTY, Markers.EMPTY,
                JRightPadded.build(select), null, name, arguments(invocationArguments), methodType);
    }

    private static JContainer<Expression> arguments(List<Expression> arguments) {
        if (arguments.isEmpty()) {
            return JContainer.build(singletonList(JRightPadded.build(
                    new J.Empty(randomId(), Space.EMPTY, Markers.EMPTY))));
        }
        List<JRightPadded<Expression>> padded = new ArrayList<>();
        for (int i = 0; i < arguments.size(); i++) {
            padded.add(JRightPadded.build(arguments.get(i)
                    .withPrefix(i == 0 ? Space.EMPTY : Space.SINGLE_SPACE)));
        }
        return JContainer.build(padded);
    }

    private static boolean isTypeReference(Expression containing) {
        if (containing instanceof J.Identifier) {
            J.Identifier identifier = (J.Identifier) containing;
            return identifier.getFieldType() == null &&
                    !"this".equals(identifier.getSimpleName()) && !"super".equals(identifier.getSimpleName()) &&
                    identifier.getType() instanceof JavaType.FullyQualified;
        }
        return containing instanceof J.FieldAccess && ((J.FieldAccess) containing).getName().getFieldType() == null;
    }

    /**
     * @return the name to give the parameter an unbound reference takes its receiver from, which the method it
     * refers to does not declare and so does not name
     */
    private static String receiverName(@Nullable JavaType type) {
        JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
        if (fullyQualified == null) {
            return "";
        }
        String className = fullyQualified.getClassName();
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * @return the name the reference is written on, which is what a parameter of the same name shadows; {@code null}
     * where the reference is written on something other than a name
     */
    private static @Nullable String leadingName(Expression containing) {
        if (containing instanceof J.Identifier) {
            return ((J.Identifier) containing).getSimpleName();
        }
        if (containing instanceof J.FieldAccess) {
            return leadingName(((J.FieldAccess) containing).getTarget());
        }
        if (containing instanceof J.ParameterizedType) {
            NameTree clazz = ((J.ParameterizedType) containing).getClazz();
            return clazz instanceof Expression ? leadingName((Expression) clazz) : null;
        }
        return null;
    }

    private static List<String> numberedNames(int parameterCount, @Nullable String shadowed) {
        List<String> names = new ArrayList<>();
        for (int i = 1; i <= parameterCount; i++) {
            StringBuilder name = new StringBuilder("arg").append(i);
            while (name.toString().equals(shadowed)) {
                name.append('_');
            }
            names.add(name.toString());
        }
        return names;
    }

    private static boolean isIdentifier(String name) {
        if (name.isEmpty() || !Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }
        for (char c : name.toCharArray()) {
            if (!Character.isJavaIdentifierPart(c)) {
                return false;
            }
        }
        return true;
    }

}
