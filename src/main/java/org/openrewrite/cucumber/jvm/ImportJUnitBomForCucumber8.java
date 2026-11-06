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
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.maven.tree.ManagedDependency;
import org.openrewrite.maven.tree.ResolvedManagedDependency;
import org.openrewrite.maven.tree.ResolvedPom;
import org.openrewrite.xml.XPathMatcher;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.ArrayList;
import java.util.List;

public class ImportJUnitBomForCucumber8 extends Recipe {

    private static final XPathMatcher MANAGED_DEPENDENCIES = new XPathMatcher("/project/dependencyManagement/dependencies");
    private static final String JUNIT_BOM_VERSION = "6.1.3";

    @Getter
    final String displayName = "Import `junit-bom` 6 ahead of a BOM that manages JUnit 5";

    @Getter
    final String description = "The Cucumber-JVM 8 `cucumber-junit-platform-engine` requires JUnit Platform 6.1. Where JUnit " +
            "5 is managed by a BOM the project imports itself, such as `spring-boot-dependencies` or a JHipster " +
            "`jhipster-dependencies`, import `junit-bom` " + JUNIT_BOM_VERSION + " ahead of it, as the first import " +
            "of a managed dependency is the one Maven uses.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!MANAGED_DEPENDENCIES.matches(getCursor()) || !importsBomManagingJUnit5() || importsJUnitBom(t)) {
                    return t;
                }
                Xml.Tag junitBom = Xml.Tag.build(
                        "<dependency>\n" +
                        "<groupId>org.junit</groupId>\n" +
                        "<artifactId>junit-bom</artifactId>\n" +
                        "<version>" + JUNIT_BOM_VERSION + "</version>\n" +
                        "<type>pom</type>\n" +
                        "<scope>import</scope>\n" +
                        "</dependency>");
                // First, as the first import of a managed dependency is the one Maven uses
                List<Content> content = new ArrayList<>();
                content.add(junitBom.withPrefix(t.getChildren().get(0).getPrefix()));
                content.addAll(t.getContent());
                t = autoFormat(t.withContent(content), ctx, getCursor().getParentOrThrow());
                maybeUpdateModel();
                return t;
            }

            /**
             * Whether JUnit 5 is managed through a BOM imported by this very POM, rather than through one a parent
             * manages it with, which an import here cannot override.
             */
            private boolean importsBomManagingJUnit5() {
                ResolvedPom pom = getResolutionResult().getPom();
                for (ResolvedManagedDependency managed : pom.getDependencyManagement()) {
                    if (!"org.junit.jupiter".equals(managed.getGroupId()) ||
                            !"junit-jupiter-api".equals(managed.getArtifactId()) ||
                            managed.getVersion() == null || !managed.getVersion().startsWith("5.") ||
                            managed.getRequestedBom() == null) {
                        continue;
                    }
                    ManagedDependency bom = managed.getRequestedBom();
                    for (ManagedDependency requested : pom.getRequested().getDependencyManagement()) {
                        if (requested instanceof ManagedDependency.Imported &&
                                bom.getGroupId().equals(pom.getValue(requested.getGroupId())) &&
                                bom.getArtifactId().equals(pom.getValue(requested.getArtifactId()))) {
                            return true;
                        }
                    }
                }
                return false;
            }

            private boolean importsJUnitBom(Xml.Tag dependencies) {
                return dependencies.getChildren("dependency").stream().anyMatch(d ->
                        "org.junit".equals(d.getChildValue("groupId").orElse(null)) &&
                        "junit-bom".equals(d.getChildValue("artifactId").orElse(null)));
            }
        };
    }
}
