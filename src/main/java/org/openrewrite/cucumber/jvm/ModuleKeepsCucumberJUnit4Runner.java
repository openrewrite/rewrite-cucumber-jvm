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

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Preconditions;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;
import org.openrewrite.marker.SearchResult;

@EqualsAndHashCode(callSuper = false)
@Value
public class ModuleKeepsCucumberJUnit4Runner extends ScanningRecipe<Set<@Nullable JavaProject>> {

    @Option(displayName = "Invert marking",
            description = "If `true`, marks the sources of the modules that keep no Cucumber JUnit 4 runner instead.",
            required = false)
    @Nullable
    Boolean invertMarking;

    String displayName = "Module keeps a Cucumber JUnit 4 runner";

    String description = "Marks all sources of a module with a class that `CucumberRunWithToSuite` keeps on the JUnit 4 " +
            "`@RunWith(Cucumber.class)` runner, rather than migrating it to a JUnit Platform `@Suite`. Intended as a " +
            "precondition for the dependency changes of that migration, which decide on the sources as they were " +
            "before the migration changed them.";

    @Override
    public Set<@Nullable JavaProject> getInitialValue(ExecutionContext ctx) {
        return new HashSet<>();
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Set<@Nullable JavaProject> acc) {
        return Preconditions.check(new UsesType<>("org.junit.runner.RunWith", false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                if (CucumberRunWithToSuite.keepsJUnit4Runner(classDecl, service(AnnotationService.class).getAllAnnotations(getCursor()))) {
                    acc.add(getCursor().firstEnclosingOrThrow(JavaSourceFile.class).getMarkers().findFirst(JavaProject.class).orElse(null));
                }
                return super.visitClassDeclaration(classDecl, ctx);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                return method;
            }
        });
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Set<@Nullable JavaProject> acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                JavaProject project = ((SourceFile) tree).getMarkers().findFirst(JavaProject.class).orElse(null);
                return acc.contains(project) != Boolean.TRUE.equals(invertMarking) ? SearchResult.found(tree) : tree;
            }
        };
    }
}
