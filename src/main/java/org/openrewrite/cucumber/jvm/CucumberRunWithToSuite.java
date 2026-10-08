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
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.service.AnnotationService;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.*;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

public class CucumberRunWithToSuite extends Recipe {

    private static final String CUCUMBER = "io.cucumber.junit.Cucumber";
    private static final String CUCUMBER_OPTIONS = "io.cucumber.junit.CucumberOptions";
    private static final String CONSTANTS = "io.cucumber.junit.platform.engine.Constants";
    private static final String SUITE_API = "org.junit.platform.suite.api.";
    private static final AnnotationMatcher RUN_WITH_CUCUMBER = new AnnotationMatcher("@org.junit.runner.RunWith(" + CUCUMBER + ".class)");
    private static final AnnotationMatcher CUCUMBER_OPTIONS_MATCHER = new AnnotationMatcher("@" + CUCUMBER_OPTIONS);
    private static final String CLASSPATH_PREFIX = "classpath:";
    private static final String TEST_RESOURCES_PREFIX = "src/test/resources/";

    // Options of the JUnit 4 runner itself, with no counterpart on the JUnit Platform, and `strict`, which
    // `DropStrictOption` removes as Cucumber 7 always runs strict
    private static final Set<String> DROPPED_OPTIONS = new HashSet<>(asList("junit", "stepNotifications", "useFileNameCompatibleName", "strict"));

    @Getter
    final String displayName = "Cucumber JUnit 4 `@RunWith(Cucumber.class)` to JUnit Platform `@Suite`";

    @Getter
    final String description = "Replaces the Cucumber JUnit 4 runner with a JUnit Platform `@Suite` that runs the Cucumber engine. " +
            "The `@CucumberOptions` become `@ConfigurationParameter` annotations, and the features become " +
            "`@SelectClasspathResource` selectors where they are on the classpath. The JUnit 4 runner looks for glue in " +
            "the package of the annotated class by default, and the Cucumber engine in the whole classpath, so that package " +
            "becomes the explicit glue when none is configured. A class with an option that cannot be carried over, such as " +
            "one that refers to a constant, is left unchanged.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(CUCUMBER, false), new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);
                Cursor cursor = updateCursor(cd);
                J.Annotation runWith = findAnnotation(cursor, RUN_WITH_CUCUMBER);
                // A superclass may contribute `@CucumberOptions` the suite would lose
                if (runWith == null || cd.getExtends() != null) {
                    return cd;
                }
                J.Annotation options = findAnnotation(cursor, CUCUMBER_OPTIONS_MATCHER);
                String packageName = cd.getType() == null ? "" : cd.getType().getPackageName();
                SuiteAnnotations suite = SuiteAnnotations.from(options, packageName);
                if (suite == null) {
                    return cd;
                }

                maybeRemoveImport("org.junit.runner.RunWith");
                maybeRemoveImport(CUCUMBER);
                maybeRemoveImport(CUCUMBER_OPTIONS);
                maybeRemoveImport(CUCUMBER_OPTIONS + ".SnippetType");
                for (String type : suite.imports()) {
                    maybeAddImport(type);
                }
                for (String constant : suite.configurationParameters.keySet()) {
                    maybeAddImport(CONSTANTS, constant, false);
                }

                cd = cd.withLeadingAnnotations(ListUtils.map(cd.getLeadingAnnotations(), a -> a == runWith || a == options ? null : a));
                return JavaTemplate.builder(suite.template())
                        .javaParser(JavaParser.fromJavaVersion().classpathFromResources(ctx, "junit-platform-suite-api-1", "cucumber-junit-platform-engine-7"))
                        .imports(suite.imports().toArray(new String[0]))
                        .staticImports(suite.staticImports().toArray(new String[0]))
                        .build()
                        .apply(updateCursor(cd), cd.getCoordinates().addAnnotation((a, b) -> 0));
            }

            private J.@Nullable Annotation findAnnotation(Cursor cursor, AnnotationMatcher matcher) {
                for (J.Annotation annotation : service(AnnotationService.class).getAllAnnotations(cursor)) {
                    if (matcher.matches(annotation)) {
                        return annotation;
                    }
                }
                return null;
            }
        });
    }

    private static class SuiteAnnotations {
        final List<String> classpathResources = new ArrayList<>();
        final Map<String, String> configurationParameters = new LinkedHashMap<>();

        static @Nullable SuiteAnnotations from(J.@Nullable Annotation options, String packageName) {
            SuiteAnnotations suite = new SuiteAnnotations();
            List<String> features = null;
            List<String> glue = null;
            List<String> extraGlue = new ArrayList<>();
            if (options != null && options.getArguments() != null) {
                for (Expression argument : options.getArguments()) {
                    if (argument instanceof J.Empty) {
                        continue;
                    }
                    if (!(argument instanceof J.Assignment) || !(((J.Assignment) argument).getVariable() instanceof J.Identifier)) {
                        return null;
                    }
                    String option = ((J.Identifier) ((J.Assignment) argument).getVariable()).getSimpleName();
                    Expression value = ((J.Assignment) argument).getAssignment();
                    if (DROPPED_OPTIONS.contains(option)) {
                        continue;
                    }
                    switch (option) {
                        case "features":
                            features = strings(value);
                            if (features == null) {
                                return null;
                            }
                            break;
                        case "glue":
                            glue = strings(value);
                            if (glue == null) {
                                return null;
                            }
                            break;
                        case "extraGlue":
                            List<String> extra = strings(value);
                            if (extra == null) {
                                return null;
                            }
                            extraGlue.addAll(extra);
                            break;
                        case "plugin":
                            if (!suite.addStrings("PLUGIN_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        case "tags":
                            List<String> tags = strings(value);
                            if (tags == null) {
                                return null;
                            }
                            if (tags.size() == 1) {
                                suite.add("FILTER_TAGS_PROPERTY_NAME", tags.get(0));
                            } else if (!tags.isEmpty()) {
                                StringJoiner expression = new StringJoiner(" and ");
                                for (String tag : tags) {
                                    expression.add("(" + tag + ")");
                                }
                                suite.add("FILTER_TAGS_PROPERTY_NAME", expression.toString());
                            }
                            break;
                        case "name":
                            List<String> names = strings(value);
                            if (names == null || names.size() > 1) {
                                return null;
                            }
                            if (!names.isEmpty()) {
                                suite.add("FILTER_NAME_PROPERTY_NAME", names.get(0));
                            }
                            break;
                        case "monochrome":
                            if (!suite.addFlag("ANSI_COLORS_DISABLED_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        case "dryRun":
                            if (!suite.addFlag("EXECUTION_DRY_RUN_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        case "publish":
                            if (!suite.addFlag("PLUGIN_PUBLISH_ENABLED_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        case "snippets":
                            String snippetType = value instanceof J.FieldAccess ? ((J.FieldAccess) value).getSimpleName() :
                                    value instanceof J.Identifier ? ((J.Identifier) value).getSimpleName() : null;
                            if (!"UNDERSCORE".equals(snippetType) && !"CAMELCASE".equals(snippetType)) {
                                return null;
                            }
                            suite.add("SNIPPET_TYPE_PROPERTY_NAME", snippetType.toLowerCase(Locale.ROOT));
                            break;
                        case "objectFactory":
                            if (!suite.addClass("OBJECT_FACTORY_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        case "uuidGenerator":
                            if (!suite.addClass("UUID_GENERATOR_PROPERTY_NAME", value)) {
                                return null;
                            }
                            break;
                        default:
                            return null;
                    }
                }
            }

            // An empty `features` or `glue` is the annotation default, under which the runner falls back to the package
            if (features == null || features.isEmpty()) {
                if (packageName.isEmpty()) {
                    return null;
                }
                suite.classpathResources.add(packageName.replace('.', '/'));
            } else {
                List<String> resources = classpathResources(features);
                if (resources != null) {
                    suite.classpathResources.addAll(resources);
                } else {
                    suite.add("FEATURES_PROPERTY_NAME", String.join(", ", features));
                }
            }

            List<String> allGlue = new ArrayList<>();
            if (glue != null && !glue.isEmpty()) {
                allGlue.addAll(glue);
            } else if (!packageName.isEmpty()) {
                allGlue.add(packageName);
            }
            allGlue.addAll(extraGlue);
            if (!allGlue.isEmpty()) {
                suite.add("GLUE_PROPERTY_NAME", String.join(", ", allGlue));
            }
            return suite;
        }

        private static @Nullable List<String> classpathResources(List<String> features) {
            List<String> resources = new ArrayList<>();
            for (String feature : features) {
                String path = feature.startsWith(CLASSPATH_PREFIX) ? feature.substring(CLASSPATH_PREFIX.length()) :
                        feature.startsWith(TEST_RESOURCES_PREFIX) ? feature.substring(TEST_RESOURCES_PREFIX.length()) :
                                null;
                if (path == null || path.contains(":")) {
                    return null;
                }
                path = path.replaceAll("^/+|/+$", "");
                if (path.isEmpty()) {
                    return null;
                }
                resources.add(path);
            }
            return resources;
        }

        private static @Nullable List<String> strings(Expression value) {
            if (value instanceof J.NewArray) {
                List<String> strings = new ArrayList<>();
                List<Expression> initializer = ((J.NewArray) value).getInitializer();
                if (initializer != null) {
                    for (Expression element : initializer) {
                        if (element instanceof J.Empty) {
                            continue;
                        }
                        Object string = literal(element);
                        if (!(string instanceof String)) {
                            return null;
                        }
                        strings.add((String) string);
                    }
                }
                return strings;
            }
            Object string = literal(value);
            return string instanceof String ? singletonList((String) string) : null;
        }

        private static @Nullable Object literal(Expression expression) {
            return expression instanceof J.Literal ? ((J.Literal) expression).getValue() : null;
        }

        private boolean addStrings(String constant, Expression value) {
            List<String> strings = strings(value);
            if (strings == null) {
                return false;
            }
            if (!strings.isEmpty()) {
                add(constant, String.join(", ", strings));
            }
            return true;
        }

        private boolean addFlag(String constant, Expression value) {
            Object flag = literal(value);
            if (!(flag instanceof Boolean)) {
                return false;
            }
            if ((Boolean) flag) {
                add(constant, "true");
            }
            return true;
        }

        private boolean addClass(String constant, Expression value) {
            if (!(value instanceof J.FieldAccess) || !"class".equals(((J.FieldAccess) value).getSimpleName())) {
                return false;
            }
            JavaType.FullyQualified type = TypeUtils.asFullyQualified(((J.FieldAccess) value).getTarget().getType());
            if (type == null) {
                return false;
            }
            add(constant, type.getFullyQualifiedName());
            return true;
        }

        private void add(String constant, String value) {
            configurationParameters.put(constant, value);
        }

        List<String> imports() {
            List<String> imports = new ArrayList<>(asList(SUITE_API + "Suite", SUITE_API + "IncludeEngines"));
            if (!classpathResources.isEmpty()) {
                imports.add(SUITE_API + "SelectClasspathResource");
            }
            if (!configurationParameters.isEmpty()) {
                imports.add(SUITE_API + "ConfigurationParameter");
            }
            return imports;
        }

        List<String> staticImports() {
            List<String> staticImports = new ArrayList<>();
            for (String constant : configurationParameters.keySet()) {
                staticImports.add(CONSTANTS + "." + constant);
            }
            return staticImports;
        }

        String template() {
            StringJoiner template = new StringJoiner("\n");
            template.add("@Suite");
            template.add("@IncludeEngines(\"cucumber\")");
            for (String resource : classpathResources) {
                template.add("@SelectClasspathResource(" + quote(resource) + ")");
            }
            for (Map.Entry<String, String> parameter : configurationParameters.entrySet()) {
                template.add("@ConfigurationParameter(key = " + parameter.getKey() + ", value = " + quote(parameter.getValue()) + ")");
            }
            return template.toString();
        }

        private static String quote(String value) {
            return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
    }
}
