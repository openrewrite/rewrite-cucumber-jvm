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
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.java.tree.TypeUtils;

public class DropStrictAware extends Recipe {

    private static final String IO_CUCUMBER_PLUGIN_STRICT_AWARE = "io.cucumber.plugin.StrictAware";
    private static final String IO_CUCUMBER_PLUGIN_PLUGIN = "io.cucumber.plugin.Plugin";
    private static final MethodMatcher SET_STRICT = new MethodMatcher(IO_CUCUMBER_PLUGIN_STRICT_AWARE + " setStrict(boolean)", true);

    @Getter
    final String displayName = "Drop `StrictAware`";

    @Getter
    final String description = "Cucumber-JVM 8.0.0 removed `StrictAware`, which Cucumber-JVM 7 only ever called with `setStrict(true)`. " +
            "Remove it from `implements` along with the `setStrict(boolean)` override, and implement `Plugin` instead " +
            "when the class implements no other plugin interface.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(IO_CUCUMBER_PLUGIN_STRICT_AWARE, null), new JavaIsoVisitor<ExecutionContext>() {

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, ctx);
                if (classDeclaration.getImplements() == null ||
                        classDeclaration.getImplements().stream().noneMatch(DropStrictAware::isStrictAware)) {
                    return classDeclaration;
                }
                boolean otherwiseAPlugin = isPlugin(classDeclaration.getExtends()) ||
                        classDeclaration.getImplements().stream().anyMatch(t -> !isStrictAware(t) && isPlugin(t));
                if (!otherwiseAPlugin) {
                    // `StrictAware` is what makes this class a plugin, so it has to remain one
                    doAfterVisit(new ChangeType(IO_CUCUMBER_PLUGIN_STRICT_AWARE, IO_CUCUMBER_PLUGIN_PLUGIN, true).getVisitor());
                    return classDeclaration;
                }
                maybeRemoveImport(IO_CUCUMBER_PLUGIN_STRICT_AWARE);
                return classDeclaration.withImplements(ListUtils.map(classDeclaration.getImplements(),
                        t -> isStrictAware(t) ? null : t));
            }

            @Override
            public J.@Nullable MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                if (SET_STRICT.matches(method.getMethodType()) && !isDeclaredInAnonymousStrictAware()) {
                    return null;
                }
                return super.visitMethodDeclaration(method, ctx);
            }

            @Override
            public J.@Nullable MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                if (SET_STRICT.matches(method) && getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    return null;
                }
                return super.visitMethodInvocation(method, ctx);
            }

            private boolean isDeclaredInAnonymousStrictAware() {
                Object enclosing = getCursor()
                        .dropParentUntil(p -> p instanceof J.ClassDeclaration || p instanceof J.NewClass)
                        .getValue();
                return enclosing instanceof J.NewClass &&
                        ((J.NewClass) enclosing).getClazz() != null &&
                        isStrictAware(((J.NewClass) enclosing).getClazz());
            }
        });
    }

    private static boolean isPlugin(@Nullable TypeTree typeTree) {
        return typeTree != null && TypeUtils.isAssignableTo(IO_CUCUMBER_PLUGIN_PLUGIN, typeTree.getType());
    }

    private static boolean isStrictAware(TypeTree typeTree) {
        return TypeUtils.isOfClassType(typeTree.getType(), IO_CUCUMBER_PLUGIN_STRICT_AWARE);
    }
}
