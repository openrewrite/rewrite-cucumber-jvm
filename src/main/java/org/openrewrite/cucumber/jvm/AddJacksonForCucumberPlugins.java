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
import org.openrewrite.ExecutionContext;
import org.openrewrite.ScanningRecipe;
import org.openrewrite.SourceFile;
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.gradle.marker.GradleDependencyConfiguration;
import org.openrewrite.gradle.marker.GradleProject;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.dependencies.AddDependency;
import org.openrewrite.java.marker.JavaProject;
import org.openrewrite.java.marker.JavaSourceSet;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;
import org.openrewrite.maven.tree.MavenResolutionResult;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;

import java.nio.file.Path;
import java.util.*;

/**
 * Cucumber-JVM 8.0.0 no longer shades Jackson, so the plugins that write JSON need it on the classpath.
 */
@EqualsAndHashCode(callSuper = false)
@Value
public class AddJacksonForCucumberPlugins extends ScanningRecipe<AddJacksonForCucumberPlugins.Accumulator> {

    private static final String PLUGIN_PROPERTY = "cucumber.plugin";
    private static final String PUBLISH_ENABLED_PROPERTY = "cucumber.publish.enabled";
    private static final String PUBLISH_TOKEN_PROPERTY = "cucumber.publish.token";

    /**
     * The constants of `io.cucumber.junit.platform.engine.Constants` and `io.cucumber.core.options.Constants`
     * naming the properties above, as `@ConfigurationParameter` keys are usually written with them.
     */
    private static final Map<String, String> PROPERTY_CONSTANTS;

    static {
        Map<String, String> constants = new HashMap<>();
        constants.put("PLUGIN_PROPERTY_NAME", PLUGIN_PROPERTY);
        constants.put("PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME", PUBLISH_ENABLED_PROPERTY);
        constants.put("PLUGIN_PUBLISH_TOKEN_PROPERTY_NAME", PUBLISH_TOKEN_PROPERTY);
        PROPERTY_CONSTANTS = Collections.unmodifiableMap(constants);
    }

    /**
     * The plugins that serialize with Jackson, by their short name and by their class name; publishing writes
     * through the `message` plugin as well.
     */
    private static final Set<String> PLUGINS_NEEDING_JACKSON = new HashSet<>(Arrays.asList(
            "html", "json", "message", "timeline", "usage-json",
            "io.cucumber.core.plugin.HtmlFormatter",
            "io.cucumber.core.plugin.JsonFormatter",
            "io.cucumber.core.plugin.MessageFormatter",
            "io.cucumber.core.plugin.TimelineFormatter",
            "io.cucumber.core.plugin.UsageJsonFormatter"));

    private static final String JACKSON_FAMILY = "com.fasterxml.jackson*";

    String displayName = "Add Jackson for the Cucumber report plugins";

    String description = "Cucumber-JVM 8.0.0 no longer shades Jackson, which the `html`, `json`, `message`, " +
            "`timeline` and `usage-json` plugins and publishing to reports.cucumber.io now need on the classpath. " +
            "Where any of these is configured through `@CucumberOptions`, `@ConfigurationParameter`, " +
            "`junit-platform.properties` or `cucumber.properties`, add `jackson-databind` and " +
            "`jackson-datatype-jdk8` unless Jackson 2 is already there, or Jackson 3 is in use instead.";

    public static class Accumulator {
        final Set<JavaProject> usedInMain = new HashSet<>();
        final Set<JavaProject> usedInTest = new HashSet<>();
        final List<Delegate> main = new ArrayList<>();
        final List<Delegate> test = new ArrayList<>();
    }

    static class Delegate {
        final AddDependency recipe;
        final AddDependency.Accumulator acc;

        Delegate(AddDependency recipe, ExecutionContext ctx) {
            this.recipe = recipe;
            this.acc = recipe.getInitialValue(ctx);
        }
    }

    @Override
    public Accumulator getInitialValue(ExecutionContext ctx) {
        Accumulator acc = new Accumulator();
        for (String artifact : new String[]{"com.fasterxml.jackson.core:jackson-databind", "com.fasterxml.jackson.datatype:jackson-datatype-jdk8"}) {
            String[] ga = artifact.split(":");
            acc.main.add(new Delegate(addDependency(ga[0], ga[1], "implementation", null), ctx));
            acc.test.add(new Delegate(addDependency(ga[0], ga[1], "testImplementation", "test"), ctx));
        }
        return acc;
    }

    private static AddDependency addDependency(String groupId, String artifactId, String configuration, @Nullable String scope) {
        return new AddDependency(groupId, artifactId, "2.x", null, null, null, JACKSON_FAMILY, null,
                configuration, scope, null, null, null, true);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getScanner(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                for (Delegate delegate : acc.main) {
                    delegate.recipe.getScanner(delegate.acc).visit(tree, ctx);
                }
                for (Delegate delegate : acc.test) {
                    delegate.recipe.getScanner(delegate.acc).visit(tree, ctx);
                }
                if (configuresPluginNeedingJackson(sourceFile, ctx)) {
                    sourceFile.getMarkers().findFirst(JavaProject.class).ifPresent(project -> {
                        boolean main = sourceFile.getMarkers().findFirst(JavaSourceSet.class)
                                .map(sourceSet -> "main".equals(sourceSet.getName()))
                                .orElse(false);
                        (main ? acc.usedInMain : acc.usedInTest).add(project);
                    });
                }
                return tree;
            }
        };
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor(Accumulator acc) {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (!(tree instanceof SourceFile)) {
                    return tree;
                }
                SourceFile sourceFile = (SourceFile) tree;
                Optional<JavaProject> project = sourceFile.getMarkers().findFirst(JavaProject.class);
                if (!project.isPresent() || usesJackson3(sourceFile)) {
                    return tree;
                }
                List<Delegate> delegates;
                if (acc.usedInMain.contains(project.get())) {
                    delegates = acc.main;
                } else if (acc.usedInTest.contains(project.get())) {
                    delegates = acc.test;
                } else {
                    return tree;
                }
                Tree t = tree;
                for (Delegate delegate : delegates) {
                    t = delegate.recipe.getVisitor(delegate.acc).visit(t, ctx);
                }
                return t;
            }
        };
    }

    private static boolean usesJackson3(SourceFile buildFile) {
        Optional<MavenResolutionResult> maven = buildFile.getMarkers().findFirst(MavenResolutionResult.class);
        if (maven.isPresent()) {
            return !maven.get().findDependencies("tools.jackson.core", "jackson-databind", null).isEmpty();
        }
        Optional<GradleProject> gradle = buildFile.getMarkers().findFirst(GradleProject.class);
        if (gradle.isPresent()) {
            for (GradleDependencyConfiguration configuration : gradle.get().getConfigurations()) {
                if (configuration.findResolvedDependency("tools.jackson.core", "jackson-databind") != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean configuresPluginNeedingJackson(SourceFile sourceFile, ExecutionContext ctx) {
        Set<Boolean> found = new HashSet<>();
        if (sourceFile instanceof J.CompilationUnit) {
            new JavaIsoVisitor<ExecutionContext>() {
                @Override
                public J.Annotation visitAnnotation(J.Annotation annotation, ExecutionContext ctx) {
                    if (isCucumberOptions(annotation)) {
                        for (Expression argument : arguments(annotation)) {
                            if (argument instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) argument;
                                String name = assignment.getVariable() instanceof J.Identifier ?
                                        ((J.Identifier) assignment.getVariable()).getSimpleName() : "";
                                if ("plugin".equals(name) && anyPluginNeedsJackson(assignment.getAssignment())) {
                                    found.add(true);
                                } else if ("publish".equals(name) && J.Literal.isLiteralValue(assignment.getAssignment(), true)) {
                                    found.add(true);
                                }
                            }
                        }
                    } else if (TypeUtils.isOfClassType(annotation.getType(), "org.junit.platform.suite.api.ConfigurationParameter")) {
                        String key = null;
                        Expression value = null;
                        for (Expression argument : arguments(annotation)) {
                            if (argument instanceof J.Assignment) {
                                J.Assignment assignment = (J.Assignment) argument;
                                String name = ((J.Identifier) assignment.getVariable()).getSimpleName();
                                if ("key".equals(name)) {
                                    key = propertyName(assignment.getAssignment());
                                } else if ("value".equals(name)) {
                                    value = assignment.getAssignment();
                                }
                            }
                        }
                        if (key != null && value instanceof J.Literal && ((J.Literal) value).getValue() instanceof String &&
                                propertyNeedsJackson(key, (String) ((J.Literal) value).getValue())) {
                            found.add(true);
                        }
                    }
                    return super.visitAnnotation(annotation, ctx);
                }
            }.visit(sourceFile, ctx);
        } else if (sourceFile instanceof Properties.File && isCucumberPropertiesFile(sourceFile.getSourcePath())) {
            new PropertiesVisitor<ExecutionContext>() {
                @Override
                public Properties visitEntry(Properties.Entry entry, ExecutionContext ctx) {
                    if (propertyNeedsJackson(entry.getKey(), entry.getValue().getText())) {
                        found.add(true);
                    }
                    return entry;
                }
            }.visit(sourceFile, ctx);
        }
        return !found.isEmpty();
    }

    private static boolean isCucumberOptions(J.Annotation annotation) {
        // `cucumber.api.CucumberOptions` as well, as the scan reads the sources as they are before `UpgradeCucumber5x`
        return TypeUtils.isOfClassType(annotation.getType(), "cucumber.api.CucumberOptions") ||
                TypeUtils.isOfClassType(annotation.getType(), "io.cucumber.junit.CucumberOptions") ||
                TypeUtils.isOfClassType(annotation.getType(), "io.cucumber.testng.CucumberOptions");
    }

    private static List<Expression> arguments(J.Annotation annotation) {
        return annotation.getArguments() == null ? Collections.emptyList() : annotation.getArguments();
    }

    private static boolean isCucumberPropertiesFile(Path sourcePath) {
        String fileName = sourcePath.getFileName().toString();
        return "junit-platform.properties".equals(fileName) || "cucumber.properties".equals(fileName);
    }

    private static @Nullable String propertyName(Expression key) {
        if (key instanceof J.Literal && ((J.Literal) key).getValue() instanceof String) {
            return (String) ((J.Literal) key).getValue();
        }
        JavaType.Variable field = null;
        if (key instanceof J.Identifier) {
            field = ((J.Identifier) key).getFieldType();
        } else if (key instanceof J.FieldAccess) {
            field = ((J.FieldAccess) key).getName().getFieldType();
        }
        return field == null ? null : PROPERTY_CONSTANTS.get(field.getName());
    }

    private static boolean propertyNeedsJackson(String key, String value) {
        switch (key) {
            case PLUGIN_PROPERTY:
                for (String plugin : value.split(",")) {
                    if (pluginNeedsJackson(plugin)) {
                        return true;
                    }
                }
                return false;
            case PUBLISH_ENABLED_PROPERTY:
                return "true".equalsIgnoreCase(value.trim());
            case PUBLISH_TOKEN_PROPERTY:
                return !value.trim().isEmpty();
            default:
                return false;
        }
    }

    private static boolean anyPluginNeedsJackson(Expression plugins) {
        if (plugins instanceof J.NewArray) {
            List<Expression> initializer = ((J.NewArray) plugins).getInitializer();
            return initializer != null && initializer.stream().anyMatch(AddJacksonForCucumberPlugins::anyPluginNeedsJackson);
        }
        return plugins instanceof J.Literal &&
                ((J.Literal) plugins).getValue() instanceof String &&
                pluginNeedsJackson((String) ((J.Literal) plugins).getValue());
    }

    /**
     * A plugin is written as its name or class name, optionally followed by a colon and where it writes to.
     */
    static boolean pluginNeedsJackson(String plugin) {
        String trimmed = plugin.trim();
        int colon = trimmed.indexOf(':');
        return PLUGINS_NEEDING_JACKSON.contains(colon < 0 ? trimmed : trimmed.substring(0, colon));
    }
}
