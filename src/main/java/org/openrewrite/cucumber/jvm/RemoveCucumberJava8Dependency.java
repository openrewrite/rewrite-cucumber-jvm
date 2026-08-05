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
import org.openrewrite.ScanningRecipe;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.concurrent.atomic.AtomicBoolean;

@EqualsAndHashCode(callSuper = false)
@Value
public class RemoveCucumberJava8Dependency extends ScanningRecipe<AtomicBoolean> {

    private static final String IO_CUCUMBER = "io.cucumber";
    private static final String CUCUMBER_JAVA8 = "cucumber-java8";
    private static final String IO_CUCUMBER_JAVA8 = "io.cucumber.java8";
    private static final String IO_CUCUMBER_JAVA8_LAMBDA_GLUE = "io.cucumber.java8.LambdaGlue";
    private static final String IO_CUCUMBER_JAVA8_SCENARIO = "io.cucumber.java8.Scenario";
    private static final String IO_CUCUMBER_JAVA8_STATUS = "io.cucumber.java8.Status";

    String displayName = "Remove `cucumber-java8` once nothing is left needing it";

    String description = "Removes the `cucumber-java8` dependency where every `LambdaGlue` call migrates to " +
            "`cucumber-java`, and retains it wherever one is left behind. Read from the glue as it stands before " +
            "the migration, as what the migration leaves behind only becomes visible to a scanning recipe in the " +
            "cycle after, which a build tool run never reaches. Glue left anywhere retains the dependency " +
            "everywhere, an unused dependency being the one outcome here that still compiles.";

    @Override
    public AtomicBoolean getInitialValue(ExecutionContext ctx) {
        return new AtomicBoolean();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(AtomicBoolean lambdaGlueRemains) {
        return new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, ExecutionContext ctx) {
                if (isLambdaGlue(mi) &&
                        !CucumberJava8StepDefinitionToCucumberJava.converts(mi) &&
                        !CucumberJava8HookDefinitionToCucumberJava.converts(mi) &&
                        !CucumberJava8TypeDefinitionToCucumberJava.converts(mi)) {
                    lambdaGlueRemains.set(true);
                }
                return super.visitMethodInvocation(mi, ctx);
            }

            @Override
            public J.Identifier visitIdentifier(J.Identifier identifier, ExecutionContext ctx) {
                flagTypeWithNowhereToGo(identifier.getType());
                return identifier;
            }

            @Override
            public J.FieldAccess visitFieldAccess(J.FieldAccess fieldAccess, ExecutionContext ctx) {
                flagTypeWithNowhereToGo(fieldAccess.getType());
                return super.visitFieldAccess(fieldAccess, ctx);
            }

            private boolean isLambdaGlue(J.MethodInvocation methodInvocation) {
                if (methodInvocation.getMethodType() == null) {
                    return false;
                }
                JavaType.FullyQualified declaringType = methodInvocation.getMethodType().getDeclaringType();
                return IO_CUCUMBER_JAVA8.equals(declaringType.getPackageName()) &&
                        TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_LAMBDA_GLUE, declaringType);
            }

            /**
             * The language interfaces go the way of the glue they contribute, and `Scenario` and `Status` are
             * retyped to their `cucumber-java` counterparts; any other `io.cucumber.java8` type named in the
             * source, such as a `HookBody` written as an anonymous class, has nowhere to go and keeps needing
             * the dependency.
             */
            private void flagTypeWithNowhereToGo(@Nullable JavaType type) {
                JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(type);
                if (fullyQualified != null &&
                        IO_CUCUMBER_JAVA8.equals(fullyQualified.getPackageName()) &&
                        !TypeUtils.isOfClassType(fullyQualified, IO_CUCUMBER_JAVA8_SCENARIO) &&
                        !TypeUtils.isOfClassType(fullyQualified, IO_CUCUMBER_JAVA8_STATUS) &&
                        !TypeUtils.isAssignableTo(IO_CUCUMBER_JAVA8_LAMBDA_GLUE, fullyQualified)) {
                    lambdaGlueRemains.set(true);
                }
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(AtomicBoolean lambdaGlueRemains) {
        return new TreeVisitor<Tree, ExecutionContext>() {

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (lambdaGlueRemains.get()) {
                    return tree;
                }
                // Offered to both, as either leaves alone whatever is not the build file it reads
                Tree afterMaven = new org.openrewrite.maven.RemoveDependency(IO_CUCUMBER, CUCUMBER_JAVA8, null)
                        .getVisitor().visit(tree, ctx);
                return new org.openrewrite.gradle.RemoveDependency(IO_CUCUMBER, CUCUMBER_JAVA8, null)
                        .getVisitor().visit(afterMaven, ctx);
            }
        };
    }
}
