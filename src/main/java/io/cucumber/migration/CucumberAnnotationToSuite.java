package io.cucumber.migration;

import lombok.SneakyThrows;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.AnnotationMatcher;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.J.ClassDeclaration;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.text.RuleBasedCollator;
import java.time.Duration;
import java.util.Comparator;

public class CucumberAnnotationToSuite extends Recipe {

    private static final String IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER = "io.cucumber.junit.platform.engine.Cucumber";

    private static final String SUITE = "org.junit.platform.suite.api.Suite";
    private static final String SELECT_CLASSPATH_RESOURCE = "org.junit.platform.suite.api.SelectClasspathResource";

    @Override
    public String getDisplayName() {
        return "Replace @Cucumber with @Suite";
    }

    @Override
    public String getDescription() {
        return "Replace @Cucumber with @Suite and @SelectClasspathResource(\"cucumber/annotated/class/package\").";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(2);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesType<>(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER, null),
                new ExecutionContextJavaIsoVisitor());
    }

    class ExecutionContextJavaIsoVisitor extends JavaIsoVisitor<ExecutionContext> {
        private final AnnotationMatcher cucumberAnnoMatcher = new AnnotationMatcher(
                "@" + IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);

        @SneakyThrows
        @Override
        public ClassDeclaration visitClassDeclaration(ClassDeclaration cd, ExecutionContext ctx) {
            ClassDeclaration classDecl = super.visitClassDeclaration(cd, ctx);
            if (classDecl.getAllAnnotations().stream().noneMatch(cucumberAnnoMatcher::matches)) {
                return classDecl;
            }

            JavaParser.Builder javaParserSupplier = JavaParser.fromJavaVersion().classpath("junit-platform-suite-api");

            JavaType.FullyQualified classFqn = TypeUtils.asFullyQualified(classDecl.getType());
            if (classFqn != null) {
                maybeRemoveImport(IO_CUCUMBER_JUNIT_PLATFORM_ENGINE_CUCUMBER);
                maybeAddImport(SUITE);
                maybeAddImport(SELECT_CLASSPATH_RESOURCE);

                final String classDeclPath = classFqn.getPackageName().replace('.', '/');
                classDecl = classDecl
                        .withLeadingAnnotations(ListUtils.map(classDecl.getLeadingAnnotations(), ann -> {
                            if (cucumberAnnoMatcher.matches(ann)) {
                                String code = "@SelectClasspathResource(\"#{}\")";
                                return JavaTemplate.builder(code)
                                        .contextSensitive()
                                        .javaParser(javaParserSupplier)
                                        .imports(SELECT_CLASSPATH_RESOURCE)
                                        .build()
                                        .apply(getCursor(), ann.getCoordinates().replace(), classDeclPath);
                            }
                            return ann;
                        }));
                JavaTemplate template = JavaTemplate.builder("@Suite")
                        .contextSensitive()
                        .javaParser(javaParserSupplier)
                        .imports(SUITE)
                        .build();
                classDecl = template.apply(getCursor(),
                        classDecl.getCoordinates().addAnnotation(Comparator.comparing(
                                J.Annotation::getSimpleName,
                                new RuleBasedCollator("< SelectClasspathResource"))));
            }
            return classDecl;
        }
    }
}