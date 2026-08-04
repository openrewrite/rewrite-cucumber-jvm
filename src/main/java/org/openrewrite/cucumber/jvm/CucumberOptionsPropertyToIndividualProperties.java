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

import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.openrewrite.*;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.marker.Markers;
import org.openrewrite.maven.MavenIsoVisitor;
import org.openrewrite.properties.PropertiesVisitor;
import org.openrewrite.properties.tree.Properties;
import org.openrewrite.trait.Comments;
import org.openrewrite.xml.tree.Content;
import org.openrewrite.xml.tree.Xml;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.openrewrite.Tree.randomId;

public class CucumberOptionsPropertyToIndividualProperties extends Recipe {

    private static final String CUCUMBER_OPTIONS = "cucumber.options";
    private static final String SYSTEM_PROPERTY_VARIABLES = "systemPropertyVariables";

    private static final String ANSI_COLORS_DISABLED = "cucumber.ansi-colors.disabled";
    private static final String EXECUTION_DRY_RUN = "cucumber.execution.dry-run";
    private static final String EXECUTION_LIMIT = "cucumber.execution.limit";
    private static final String EXECUTION_ORDER = "cucumber.execution.order";
    private static final String EXECUTION_STRICT = "cucumber.execution.strict";
    private static final String EXECUTION_WIP = "cucumber.execution.wip";
    private static final String FEATURES = "cucumber.features";
    private static final String FILTER_NAME = "cucumber.filter.name";
    private static final String FILTER_TAGS = "cucumber.filter.tags";
    private static final String GLUE = "cucumber.glue";
    private static final String OBJECT_FACTORY = "cucumber.object-factory";
    private static final String PLUGIN = "cucumber.plugin";
    private static final String PUBLISH_ENABLED = "cucumber.publish.enabled";
    private static final String SNIPPET_TYPE = "cucumber.snippet-type";

    /**
     * The tokenizer Cucumber-JVM itself applied to `cucumber.options`, in `io.cucumber.core.options.ShellWords`.
     */
    private static final Pattern SHELL_WORDS = Pattern.compile("[^\\s'\"]+|'([^']*)'|\"([^\"]*)\"");

    private static final String MANUAL_MIGRATION = "TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; " +
            "migrate to the individual cucumber.* properties by hand";

    @Getter
    final String displayName = "Migrate the `cucumber.options` property";

    @Getter
    final String description = "Cucumber-JVM 6.0.0 removed `cucumber.options`, which passed command line options " +
            "as a single string, in favour of an individual property per option. This recipe splits the property " +
            "into its replacements, both in `.properties` files and in Maven Surefire or Failsafe " +
            "`systemPropertyVariables`. Options without a property equivalent, such as `--threads`, have no " +
            "migration path; there the property is left untouched, with a `TODO` comment added above it.";

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return new TreeVisitor<Tree, ExecutionContext>() {
            @Override
            public boolean isAcceptable(SourceFile sourceFile, ExecutionContext ctx) {
                return sourceFile instanceof Properties.File || sourceFile instanceof Xml.Document;
            }

            @Override
            public @Nullable Tree visit(@Nullable Tree tree, ExecutionContext ctx) {
                if (tree instanceof Properties.File) {
                    return propertiesVisitor().visit(tree, ctx);
                }
                if (tree instanceof Xml.Document) {
                    return mavenVisitor().visit(tree, ctx);
                }
                return tree;
            }
        };
    }

    private static PropertiesVisitor<ExecutionContext> propertiesVisitor() {
        return new PropertiesVisitor<ExecutionContext>() {
            @Override
            public Properties visitFile(Properties.File file, ExecutionContext ctx) {
                Properties.File f = (Properties.File) super.visitFile(file, ctx);
                Properties.Entry options = f.getContent().stream()
                        .filter(Properties.Entry.class::isInstance)
                        .map(Properties.Entry.class::cast)
                        .filter(entry -> CUCUMBER_OPTIONS.equals(entry.getKey()))
                        .findFirst()
                        .orElse(null);
                if (options == null) {
                    return f;
                }
                Map<String, String> replacements = individualProperties(options.getValue().getText());
                if (replacements == null) {
                    return flagForManualMigration(getCursor(), f, options);
                }
                Set<String> present = f.getContent().stream()
                        .filter(Properties.Entry.class::isInstance)
                        .map(entry -> ((Properties.Entry) entry).getKey())
                        .collect(Collectors.toSet());
                if (present.stream().anyMatch(replacements::containsKey)) {
                    return flagForManualMigration(getCursor(), f, options);
                }
                String lineSeparator = lineSeparator(f);
                List<Properties.Content> content = ListUtils.flatMap(f.getContent(), c -> {
                    if (c != options) {
                        return c;
                    }
                    List<Properties.Content> entries = new ArrayList<>(replacements.size());
                    for (Map.Entry<String, String> replacement : replacements.entrySet()) {
                        entries.add(new Properties.Entry(
                                randomId(),
                                entries.isEmpty() ? options.getPrefix() : lineSeparator,
                                Markers.EMPTY,
                                replacement.getKey(),
                                options.getBeforeEquals(),
                                options.getDelimiter(),
                                options.getValue().withId(randomId()).withText(replacement.getValue())));
                    }
                    return entries;
                });
                if (replacements.isEmpty()) {
                    content = ListUtils.mapFirst(content, c -> (Properties.Content) c.withPrefix(""));
                }
                return f.withContent(content);
            }
        };
    }

    private static Properties.File flagForManualMigration(
            Cursor fileCursor, Properties.File file, Properties.Entry options) {
        return Comments.of(cursorFor(fileCursor, file, options)).comment(" " + MANUAL_MIGRATION);
    }

    private static String lineSeparator(Properties.File file) {
        return file.getEof().contains("\r\n") ||
                file.getContent().stream().anyMatch(content -> content.getPrefix().contains("\r\n")) ? "\r\n" : "\n";
    }

    private static MavenIsoVisitor<ExecutionContext> mavenVisitor() {
        return new MavenIsoVisitor<ExecutionContext>() {
            @Override
            public Xml.Tag visitTag(Xml.Tag tag, ExecutionContext ctx) {
                Xml.Tag t = super.visitTag(tag, ctx);
                if (!SYSTEM_PROPERTY_VARIABLES.equals(t.getName())) {
                    return t;
                }
                Optional<Xml.Tag> maybeOptions = t.getChild(CUCUMBER_OPTIONS);
                if (!maybeOptions.isPresent()) {
                    return t;
                }
                Xml.Tag options = maybeOptions.get();
                String value = optionsValue(options);
                if (value == null) {
                    return flagForManualMigration(getCursor(), t, options);
                }
                Map<String, String> replacements = individualProperties(value);
                if (replacements == null) {
                    return flagForManualMigration(getCursor(), t, options);
                }
                Set<String> present = t.getChildren().stream().map(Xml.Tag::getName).collect(Collectors.toSet());
                if (present.stream().anyMatch(replacements::containsKey)) {
                    return flagForManualMigration(getCursor(), t, options);
                }
                return t.withContent(ListUtils.flatMap(t.getContent(), c -> {
                    if (c != options) {
                        return c;
                    }
                    return replacements.entrySet().stream()
                            .map(replacement -> (Content) Xml.Tag
                                    .build(String.format("<%1$s>%2$s</%1$s>",
                                            replacement.getKey(), escape(replacement.getValue())))
                                    .withPrefix(options.getPrefix()))
                            .collect(Collectors.toList());
                }));
            }
        };
    }

    private static Xml.Tag flagForManualMigration(
            Cursor tagCursor, Xml.Tag systemPropertyVariables, Xml.Tag options) {
        return Comments.of(cursorFor(tagCursor, systemPropertyVariables, options))
                .comment(" " + MANUAL_MIGRATION + " ");
    }

    /**
     * The comment services locate the element to comment within its parent by reference identity, so
     * rebuild the cursor around the visited copy of the parent rather than the original under the cursor.
     */
    private static Cursor cursorFor(Cursor parentCursor, Tree parent, Tree element) {
        return new Cursor(new Cursor(parentCursor.getParent(), parent), element);
    }

    /**
     * @return the `cucumber.options` value, or `null` if it can not be written back unchanged, as
     * {@link Xml.Tag#getValue()} skips markup that is not character data, and drops the whitespace
     * that surrounds an escaped character.
     */
    private static @Nullable String optionsValue(Xml.Tag options) {
        List<? extends Content> content = options.getContent();
        if (content == null) {
            return "";
        }
        String value = options.getValue().orElse(null);
        if (value == null) {
            return null;
        }
        String source = content.stream()
                .filter(Xml.CharData.class::isInstance)
                .map(Xml.CharData.class::cast)
                .map(charData -> charData.getPrefix() + charData.getText() + charData.getAfterText())
                .collect(Collectors.joining());
        return escape(value).equals(source.trim()) ? value : null;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * @return the individual properties replacing the given `cucumber.options` value, in the order Cucumber-JVM
     * documents them, or `null` if the value contains options without a property equivalent.
     */
    private static @Nullable Map<String, String> individualProperties(String options) {
        List<String> glue = new ArrayList<>();
        List<String> plugins = new ArrayList<>();
        List<String> features = new ArrayList<>();
        List<String> tags = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Map<String, String> flags = new HashMap<>();

        List<String> args = shellWords(options);
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i).trim();
            switch (arg) {
                case "--glue":
                case "-g":
                    if (++i == args.size()) {
                        return null;
                    }
                    glue.add(args.get(i));
                    break;
                case "--plugin":
                case "--add-plugin":
                case "-p":
                    if (++i == args.size()) {
                        return null;
                    }
                    plugins.add(args.get(i));
                    break;
                case "--tags":
                case "-t":
                    if (++i == args.size()) {
                        return null;
                    }
                    tags.add(args.get(i));
                    break;
                case "--name":
                case "-n":
                    if (++i == args.size()) {
                        return null;
                    }
                    names.add(args.get(i));
                    break;
                case "--count":
                    if (++i == args.size()) {
                        return null;
                    }
                    flags.put(EXECUTION_LIMIT, args.get(i));
                    break;
                case "--order":
                    if (++i == args.size()) {
                        return null;
                    }
                    flags.put(EXECUTION_ORDER, args.get(i));
                    break;
                case "--object-factory":
                    if (++i == args.size()) {
                        return null;
                    }
                    flags.put(OBJECT_FACTORY, args.get(i));
                    break;
                case "--snippets":
                    if (++i == args.size()) {
                        return null;
                    }
                    flags.put(SNIPPET_TYPE, args.get(i));
                    break;
                case "--dry-run":
                case "-d":
                    flags.put(EXECUTION_DRY_RUN, "true");
                    break;
                case "--no-dry-run":
                    flags.put(EXECUTION_DRY_RUN, "false");
                    break;
                case "--monochrome":
                case "-m":
                    flags.put(ANSI_COLORS_DISABLED, "true");
                    break;
                case "--no-monochrome":
                    flags.put(ANSI_COLORS_DISABLED, "false");
                    break;
                case "--strict":
                case "-s":
                    flags.put(EXECUTION_STRICT, "true");
                    break;
                case "--no-strict":
                    flags.put(EXECUTION_STRICT, "false");
                    break;
                case "--wip":
                case "-w":
                    flags.put(EXECUTION_WIP, "true");
                    break;
                case "--publish":
                    flags.put(PUBLISH_ENABLED, "true");
                    break;
                default:
                    if (arg.startsWith("-")) {
                        return null;
                    }
                    if (!arg.isEmpty()) {
                        features.add(arg);
                    }
            }
        }

        // A single name filter maps onto a single regular expression; several are combined with an `or` that
        // `cucumber.filter.name` cannot express
        if (names.size() > 1) {
            return null;
        }
        // Cucumber-JVM 5.0.0 dropped the old style `~@tag` negation and `@a,@b` disjunction; `cucumber.filter.tags`
        // parses either as a tag literal that matches nothing, rather than reporting an error, so there is no
        // migration path
        if (tags.stream().anyMatch(tag -> tag.contains("~@") || tag.contains(","))) {
            return null;
        }
        String joinedFeatures = joinOnComma(features);
        String joinedGlue = joinOnComma(glue);
        String joinedPlugins = joinOnComma(plugins);
        if (joinedFeatures == null || joinedGlue == null || joinedPlugins == null) {
            return null;
        }

        Map<String, String> properties = new LinkedHashMap<>();
        putIfPresent(properties, ANSI_COLORS_DISABLED, flags);
        putIfPresent(properties, EXECUTION_DRY_RUN, flags);
        putIfPresent(properties, EXECUTION_LIMIT, flags);
        putIfPresent(properties, EXECUTION_ORDER, flags);
        putIfPresent(properties, EXECUTION_STRICT, flags);
        putIfPresent(properties, EXECUTION_WIP, flags);
        putIfNotEmpty(properties, FEATURES, joinedFeatures);
        putIfNotEmpty(properties, FILTER_NAME, names.isEmpty() ? "" : names.get(0));
        putIfNotEmpty(properties, FILTER_TAGS, tagExpression(tags));
        putIfNotEmpty(properties, GLUE, joinedGlue);
        putIfPresent(properties, OBJECT_FACTORY, flags);
        putIfNotEmpty(properties, PLUGIN, joinedPlugins);
        putIfPresent(properties, PUBLISH_ENABLED, flags);
        putIfPresent(properties, SNIPPET_TYPE, flags);
        return properties;
    }

    private static void putIfPresent(Map<String, String> properties, String key, Map<String, String> flags) {
        putIfNotEmpty(properties, key, flags.getOrDefault(key, ""));
    }

    private static void putIfNotEmpty(Map<String, String> properties, String key, String value) {
        if (!value.isEmpty()) {
            properties.put(key, value);
        }
    }

    /**
     * Several `--tags` arguments are and-ed together, matching the deprecation warning Cucumber-JVM logged.
     */
    private static String tagExpression(List<String> tags) {
        if (tags.size() < 2) {
            return tags.isEmpty() ? "" : tags.get(0);
        }
        return "(" + String.join(") and (", tags) + ")";
    }

    private static @Nullable String joinOnComma(List<String> values) {
        if (values.stream().anyMatch(value -> value.contains(","))) {
            return null;
        }
        return String.join(",", values);
    }

    private static List<String> shellWords(String options) {
        List<String> words = new ArrayList<>();
        Matcher matcher = SHELL_WORDS.matcher(options);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                words.add(matcher.group(1));
            } else if (matcher.group(2) != null) {
                words.add(matcher.group(2));
            } else {
                words.add(matcher.group());
            }
        }
        return words;
    }
}
