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
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static org.openrewrite.cucumber.jvm.GlueMethods.PARAMETER_TYPE_IMPORTS;
import static org.openrewrite.cucumber.jvm.GlueMethods.decapitalize;
import static org.openrewrite.cucumber.jvm.GlueMethods.declaredMethodNames;
import static org.openrewrite.cucumber.jvm.GlueMethods.fullyQualifiedName;
import static org.openrewrite.cucumber.jvm.GlueMethods.literalSource;
import static org.openrewrite.cucumber.jvm.GlueMethods.sanitize;
import static org.openrewrite.cucumber.jvm.GlueMethods.stringLiteral;
import static org.openrewrite.cucumber.jvm.GlueMethods.uniqueMethodName;

/**
 * The `LambdaGlue` registrations that are not step or hook definitions: each registers a transformation keyed by the
 * type it returns, which `cucumber-java` expresses as an annotated method with that return type.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class CucumberJava8TypeDefinitionToCucumberJava extends Recipe {

    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8.";
    private static final String LAMBDA_GLUE = IO_CUCUMBER_JAVA8 + "LambdaGlue ";

    private static final String DATA_TABLE_TYPE = "DataTableType";
    private static final String PARAMETER_TYPE = "ParameterType";
    private static final String DOC_STRING_TYPE = "DocStringType";

    /**
     * The names of the registrations already claimed by the enclosing class, keyed by the invocation each was
     * claimed for. Held per class, as the names have to be handed out in declaration order however many of the
     * registrations are still to be visited.
     */
    private static final String TYPE_DEFINITIONS = "cucumberTypeDefinitions";

    private static final List<MethodMatcher> TYPE_DEFINITION_MATCHERS = new ArrayList<>();

    /**
     * The types the parameters of the annotated method take, per functional interface the lambda implements. None of
     * these are generic, so the lambda only contributes the parameter names; one that leaves its types implicit is
     * migrated just as well as one that spells them out.
     */
    private static final Map<String, List<String>> BODY_PARAMETERS = new HashMap<>();

    /**
     * The bodies that name no type of their own, as they are handed the type to convert to at runtime.
     */
    private static final Set<String> OBJECT_RETURNING_BODIES = new HashSet<>(asList(
            IO_CUCUMBER_JAVA8 + "DefaultParameterTransformerBody",
            IO_CUCUMBER_JAVA8 + "DefaultDataTableCellTransformerBody",
            IO_CUCUMBER_JAVA8 + "DefaultDataTableEntryTransformerBody"));

    static {
        for (String typeDefinition : asList(DATA_TABLE_TYPE, PARAMETER_TYPE, DOC_STRING_TYPE,
                "DefaultParameterTransformer", "DefaultDataTableCellTransformer", "DefaultDataTableEntryTransformer")) {
            TYPE_DEFINITION_MATCHERS.add(new MethodMatcher(LAMBDA_GLUE + typeDefinition + "(..)"));
        }

        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DataTableCellDefinitionBody", singletonList("String"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DataTableDefinitionBody", singletonList("DataTable"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DataTableEntryDefinitionBody", singletonList("Map<String, String>"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DataTableRowDefinitionBody", singletonList("List<String>"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DocStringDefinitionBody", singletonList("String"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DefaultParameterTransformerBody", asList("String", "Type"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DefaultDataTableCellTransformerBody", asList("String", "Type"));
        BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "DefaultDataTableEntryTransformerBody", asList("Map<String, String>", "Type"));
        for (int arity = 1; arity <= 9; arity++) {
            BODY_PARAMETERS.put(IO_CUCUMBER_JAVA8 + "ParameterDefinitionBody$A" + arity, nCopies(arity, "String"));
        }
    }

    String displayName = "Replace `cucumber-java8` type definitions with `cucumber-java`";

    String description = "Replace `LambdaGlue` `DataTableType`, `ParameterType`, `DocStringType` and " +
            "`Default*Transformer` registrations with `cucumber-java` annotated methods with the same body.";

    Duration estimatedEffortPerOccurrence = Duration.ofMinutes(10);

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        TreeVisitor<?, ExecutionContext> usesTypeDefinition = null;
        for (MethodMatcher matcher : TYPE_DEFINITION_MATCHERS) {
            UsesMethod<ExecutionContext> usesMethod = new UsesMethod<>(matcher);
            usesTypeDefinition = usesTypeDefinition == null ? usesMethod :
                    Preconditions.or(usesTypeDefinition, usesMethod);
        }
        return Preconditions.check(
                usesTypeDefinition,
                new JavaVisitor<ExecutionContext>() {

                    @Override
                    public J visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                        getCursor().putMessage(TYPE_DEFINITIONS, typeDefinitions(cd));
                        return super.visitClassDeclaration(cd, ctx);
                    }

                    @Override
                    public @Nullable J visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                        J.MethodInvocation methodInvocation = (J.MethodInvocation) super.visitMethodInvocation(mi, ctx);
                        if (!isTypeDefinition(methodInvocation)) {
                            return methodInvocation;
                        }

                        Map<UUID, TypeDefinitionArguments> typeDefinitions =
                                getCursor().getNearestMessage(TYPE_DEFINITIONS, emptyMap());
                        TypeDefinitionArguments arguments = typeDefinitions.get(methodInvocation.getId());
                        if (arguments == null) {
                            return SearchResult.found(methodInvocation, "TODO Migrate manually");
                        }

                        J.ClassDeclaration parentClass = getCursor()
                                .dropParentUntil(J.ClassDeclaration.class::isInstance)
                                .getValue();
                        J.MethodDeclaration glueDeclaration = getCursor().firstEnclosing(J.MethodDeclaration.class);
                        doAfterVisit(new CucumberJava8ClassVisitor(
                                parentClass.getType(),
                                glueDeclaration == null ? null : glueDeclaration.getId(),
                                arguments.getReplacementImports(),
                                arguments.template(),
                                arguments.parameters(),
                                arguments.getReturnJavaType()));

                        // Remove original method invocation; it's replaced in the above visitor
                        // noinspection DataFlowIssue
                        return null;
                    }
                });
    }

    /**
     * @return whether this invocation registers a type transformation, which is neither a step nor a hook definition,
     * even though the {@code String} prefixed overloads look like one
     */
    static boolean isTypeDefinition(J.MethodInvocation methodInvocation) {
        for (MethodMatcher matcher : TYPE_DEFINITION_MATCHERS) {
            if (matcher.matches(methodInvocation)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether this invocation is a registration this recipe replaces with an annotated method, rather than
     * one it leaves where it is for a manual migration
     */
    static boolean converts(J.MethodInvocation methodInvocation) {
        return isTypeDefinition(methodInvocation) && parse(methodInvocation, null) != null;
    }

    /**
     * The type a registration is keyed by names the method it becomes, so a class registering two transformations to
     * the same type needs the second name told apart from the first. Resolved for the class as a whole, in
     * declaration order, so that every registration lands on the same name whichever one is being replaced.
     *
     * @return what to declare the method replacing each convertible registration in the class with
     */
    private static Map<UUID, TypeDefinitionArguments> typeDefinitions(J.ClassDeclaration classDeclaration) {
        Set<String> methodNames = declaredMethodNames(classDeclaration);
        Map<UUID, TypeDefinitionArguments> typeDefinitions = new HashMap<>();
        new JavaIsoVisitor<Integer>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, Integer p) {
                // A nested class declares its own methods, so its registrations claim no name here
                return cd.getId().equals(classDeclaration.getId()) ? super.visitClassDeclaration(cd, p) : cd;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, Integer p) {
                if (isTypeDefinition(mi)) {
                    TypeDefinitionArguments arguments = parse(mi, classDeclaration.getType());
                    if (arguments != null) {
                        typeDefinitions.put(mi.getId(), arguments.withMethodName(
                                uniqueMethodName(arguments.getMethodName(), methodNames)));
                    }
                }
                return super.visitMethodInvocation(mi, p);
            }
        }.visit(classDeclaration, 0);
        return typeDefinitions;
    }

    /**
     * @return what to declare the method replacing this registration with, or {@code null} where the registration
     * cannot be converted, such as a body passed as a method reference or a type that could not be resolved
     */
    private static @Nullable TypeDefinitionArguments parse(J.MethodInvocation methodInvocation,
            JavaType.@Nullable FullyQualified parentClass) {
        List<Expression> arguments = methodInvocation.getArguments();
        if (arguments.isEmpty() || !(arguments.get(arguments.size() - 1) instanceof J.Lambda)) {
            return null;
        }
        for (int i = 0; i < arguments.size() - 1; i++) {
            if (stringLiteral(arguments.get(i)) == null) {
                return null;
            }
        }
        J.Lambda lambda = (J.Lambda) arguments.get(arguments.size() - 1);
        String functionalInterface = fullyQualifiedName(lambda.getType());
        List<String> bodyParameters = functionalInterface == null ? null : BODY_PARAMETERS.get(functionalInterface);
        if (bodyParameters == null) {
            return null;
        }
        List<String> parameters = parameters(lambda, bodyParameters);
        if (parameters == null) {
            return null;
        }

        Set<String> imports = new LinkedHashSet<>();
        for (String bodyParameter : bodyParameters) {
            String parameterTypeImport = PARAMETER_TYPE_IMPORTS.get(bodyParameter);
            if (parameterTypeImport != null) {
                imports.add(parameterTypeImport);
            }
        }

        String annotationName = methodInvocation.getSimpleName();
        // The registrations are keyed by the type they return, which the functional interface carries as its type
        // argument; the default transformers declare none, as they return whichever type was asked for
        JavaType returnJavaType = null;
        String returnType = "Object";
        if (!OBJECT_RETURNING_BODIES.contains(functionalInterface)) {
            if (!(lambda.getType() instanceof JavaType.Parameterized)) {
                return null;
            }
            List<JavaType> typeParameters = ((JavaType.Parameterized) lambda.getType()).getTypeParameters();
            if (typeParameters.size() != 1) {
                return null;
            }
            returnJavaType = typeParameters.get(0);
            returnType = typeName(returnJavaType, parentClass, imports);
            if (returnType == null) {
                return null;
            }
        }

        String annotationArguments;
        String methodName;
        switch (annotationName) {
            case PARAMETER_TYPE:
                String name = stringLiteral(arguments.get(0));
                if (arguments.size() != 3 || name == null) {
                    return null;
                }
                methodName = sanitize(name, "parameterType");
                // The annotation defaults the parameter type name to the name of the method it annotates
                annotationArguments = methodName.equals(name) ?
                        "(" + literalSource(arguments.get(1)) + ")" :
                        "(name = " + literalSource(arguments.get(0)) + ", value = " + literalSource(arguments.get(1)) + ")";
                break;
            case DOC_STRING_TYPE:
                if (arguments.size() != 2) {
                    return null;
                }
                annotationArguments = "(contentType = " + literalSource(arguments.get(0)) + ")";
                methodName = sanitize(decapitalize(returnType), "docStringType");
                break;
            case DATA_TABLE_TYPE:
                annotationArguments = replaceWithEmptyString(arguments);
                methodName = sanitize(decapitalize(returnType), "dataTableType");
                break;
            default:
                // The default transformers take no name of their own, only an empty string replacement
                annotationArguments = replaceWithEmptyString(arguments);
                methodName = decapitalize(annotationName);
                break;
        }
        imports.add("io.cucumber.java." + annotationName);
        return new TypeDefinitionArguments(annotationName, annotationArguments, returnType, returnJavaType, methodName,
                String.join(", ", parameters), new ArrayList<>(imports), lambda.getBody());
    }

    private static String replaceWithEmptyString(List<Expression> arguments) {
        return arguments.size() == 1 ? "" :
                "(replaceWithEmptyString = " + literalSource(arguments.get(0)) + ")";
    }

    /**
     * @return the parameters of the annotated method, taking the names from the lambda and the types from the
     * functional interface it implements, or {@code null} where the lambda declares something other than a plain
     * parameter list
     */
    private static @Nullable List<String> parameters(J.Lambda lambda, List<String> bodyParameters) {
        List<J> declared = lambda.getParameters().getParameters();
        if (declared.size() != bodyParameters.size()) {
            return null;
        }
        List<String> parameters = new ArrayList<>();
        for (int i = 0; i < declared.size(); i++) {
            if (!(declared.get(i) instanceof J.VariableDeclarations)) {
                return null;
            }
            J.VariableDeclarations declaration = (J.VariableDeclarations) declared.get(i);
            if (declaration.getVariables().size() != 1) {
                return null;
            }
            parameters.add(bodyParameters.get(i) + " " + declaration.getVariables().get(0).getSimpleName());
        }
        return parameters;
    }

    private static @Nullable String typeName(@Nullable JavaType type, JavaType.@Nullable FullyQualified parentClass,
            Collection<String> imports) {
        if (type instanceof JavaType.Parameterized) {
            JavaType.Parameterized parameterized = (JavaType.Parameterized) type;
            String rawName = typeName(parameterized.getType(), parentClass, imports);
            if (rawName == null) {
                return null;
            }
            StringBuilder name = new StringBuilder(rawName).append('<');
            List<JavaType> typeParameters = parameterized.getTypeParameters();
            for (int i = 0; i < typeParameters.size(); i++) {
                String typeParameter = typeName(typeParameters.get(i), parentClass, imports);
                if (typeParameter == null) {
                    return null;
                }
                name.append(i == 0 ? "" : ", ").append(typeParameter);
            }
            return name.append('>').toString();
        }
        if (type instanceof JavaType.Array) {
            String elementType = typeName(((JavaType.Array) type).getElemType(), parentClass, imports);
            return elementType == null ? null : elementType + "[]";
        }
        if (type instanceof JavaType.Primitive) {
            return ((JavaType.Primitive) type).getKeyword();
        }
        if (type instanceof JavaType.FullyQualified) {
            JavaType.FullyQualified fullyQualified = (JavaType.FullyQualified) type;
            String inScope = nameInScope(fullyQualified, parentClass);
            if (inScope != null) {
                return inScope;
            }
            // A nested type is named through its outer classes, so it is the outermost one that has to be imported
            JavaType.FullyQualified outermost = fullyQualified;
            while (outermost.getOwningClass() != null) {
                outermost = outermost.getOwningClass();
            }
            imports.add(outermost.getFullyQualifiedName());
            return fullyQualified.getClassName();
        }
        // A type variable or wildcard names no one type to key the registration by
        return null;
    }

    /**
     * @return how to name a type nested in the class the method is being added to, or in one of its outer classes,
     * as those are named without going through the class declaring them; {@code null} for any other type
     */
    private static @Nullable String nameInScope(JavaType.FullyQualified type,
            JavaType.@Nullable FullyQualified parentClass) {
        Set<String> inScope = new HashSet<>();
        for (JavaType.FullyQualified enclosing = parentClass; enclosing != null; enclosing = enclosing.getOwningClass()) {
            inScope.add(enclosing.getFullyQualifiedName());
        }
        for (JavaType.FullyQualified owner = type.getOwningClass(); owner != null; owner = owner.getOwningClass()) {
            if (inScope.contains(owner.getFullyQualifiedName())) {
                return type.getClassName().substring(owner.getClassName().length() + 1);
            }
        }
        return null;
    }

}

@Value
class TypeDefinitionArguments {

    String annotationName;
    String annotationArguments;
    String returnType;

    @Nullable
    JavaType returnJavaType;

    @With
    String methodName;

    String parameters;
    List<String> replacementImports;
    J body;

    String template() {
        // The body is passed as a template parameter to retain its type attribution
        String statement = body instanceof J.Block ? "#{any()}" : "return #{any()};";
        return "@#{}#{}\npublic #{} #{}(#{}) throws Exception {\n" + statement + "\n}";
    }

    Object[] parameters() {
        return new Object[]{annotationName, annotationArguments, returnType, methodName, parameters, body};
    }

}
