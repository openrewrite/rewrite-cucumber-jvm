package io.cucumber.migration;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.ChangeType;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.RemoveImport;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.TypeUtils;

import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.stream.Stream;

public class DropSummaryPrinter extends Recipe {

    private static final String IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER = "io.cucumber.plugin.SummaryPrinter";
    private static final String IO_CUCUMBER_PLUGIN_PLUGIN = "io.cucumber.plugin.Plugin";

    @Override
    public String getDisplayName() {
        return "Drop SummaryPrinter";
    }

    @Override
    public String getDescription() {
        return "Replace SummaryPrinter with Plugin, if not already present.";
    }

    @Override
    public @Nullable Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(1);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER, null), new DropSummaryPrinterVisitor());
    }

    static final class DropSummaryPrinterVisitor extends JavaIsoVisitor<ExecutionContext> {
        @Override
        public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext p) {
            J.ClassDeclaration classDeclaration = super.visitClassDeclaration(cd, p);
            boolean implementsSummaryPrinter = Stream.of(classDeclaration.getImplements())
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .anyMatch(t -> TypeUtils.isOfClassType(t.getType(), IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER));
            boolean alreadyImplementsPlugin = Stream.of(classDeclaration.getImplements())
                    .filter(Objects::nonNull)
                    .flatMap(Collection::stream)
                    .anyMatch(t -> TypeUtils.isOfClassType(t.getType(), IO_CUCUMBER_PLUGIN_PLUGIN));
            if (!implementsSummaryPrinter) {
                return classDeclaration;
            }
            doAfterVisit(new ChangeType(
                    IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER,
                    IO_CUCUMBER_PLUGIN_PLUGIN,
                    true).getVisitor());
            doAfterVisit(new RemoveImport<>(IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER));
            return classDeclaration.withImplements(ListUtils.map(classDeclaration.getImplements(), i -> {
                // Remove duplicate implements
                if (TypeUtils.isOfClassType(i.getType(), IO_CUCUMBER_PLUGIN_SUMMARY_PRINTER)
                        && alreadyImplementsPlugin) {
                    return null;
                }
                return i;
            }));
        }
    }

}
