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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.Issue;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.maven.Assertions.pomXml;
import static org.openrewrite.properties.Assertions.properties;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class CucumberOptionsPropertyToIndividualPropertiesTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new CucumberOptionsPropertyToIndividualProperties());
    }

    @DocumentExample
    @Test
    void cucumberProperties() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--glue com.example.app --plugin pretty --tags "@integration and not @wip"
                        """,
                        """
                        cucumber.filter.tags=@integration and not @wip
                        cucumber.glue=com.example.app
                        cucumber.plugin=pretty
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void everyOptionWithAPropertyEquivalent() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--monochrome --dry-run --count 3 --order random:42 --strict --wip \
                        classpath:features --name '^example.*' --tags @wip --glue com.example.app \
                        --object-factory com.example.app.CustomObjectFactory --plugin pretty --publish \
                        --snippets camelcase
                        """,
                        """
                        cucumber.ansi-colors.disabled=true
                        cucumber.execution.dry-run=true
                        cucumber.execution.limit=3
                        cucumber.execution.order=random:42
                        cucumber.execution.strict=true
                        cucumber.execution.wip=true
                        cucumber.features=classpath:features
                        cucumber.filter.name=^example.*
                        cucumber.filter.tags=@wip
                        cucumber.glue=com.example.app
                        cucumber.object-factory=com.example.app.CustomObjectFactory
                        cucumber.plugin=pretty
                        cucumber.publish.enabled=true
                        cucumber.snippet-type=camelcase
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void shortOptionsAndNegations() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=-g com.example.app -t @wip -p pretty --no-dry-run --no-monochrome
                        """,
                        """
                        cucumber.ansi-colors.disabled=false
                        cucumber.execution.dry-run=false
                        cucumber.filter.tags=@wip
                        cucumber.glue=com.example.app
                        cucumber.plugin=pretty
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void repeatedOptionsAreCombined() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=-g com.example.app -g com.example.other -p pretty --add-plugin html:target/report.html \
                        classpath:features/a.feature classpath:features/b.feature --tags @integration --tags "not @wip"
                        """,
                        """
                        cucumber.features=classpath:features/a.feature,classpath:features/b.feature
                        cucumber.filter.tags=(@integration) and (not @wip)
                        cucumber.glue=com.example.app,com.example.other
                        cucumber.plugin=pretty,html:target/report.html
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void retainSurroundingEntriesAndComments() {
        rewriteRun(
                properties(
                        """
                        # Cucumber configuration
                        cucumber.execution.parallel.enabled=true

                        cucumber.options=--glue com.example.app

                        cucumber.publish.quiet=true
                        """,
                        """
                        # Cucumber configuration
                        cucumber.execution.parallel.enabled=true

                        cucumber.glue=com.example.app

                        cucumber.publish.quiet=true
                        """,
                        spec -> spec.path("src/test/resources/junit-platform.properties")));
    }

    @Test
    void dropAnEmptyOptionsProperty() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=
                        cucumber.publish.quiet=true
                        """,
                        """
                        cucumber.publish.quiet=true
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void retainCarriageReturnsAndTheColonDelimiter() {
        rewriteRun(
                properties(
                        "cucumber.options : --glue com.example.app --tags @wip\r\ncucumber.publish.quiet=true\r\n",
                        "cucumber.filter.tags : @wip\r\ncucumber.glue : com.example.app\r\ncucumber.publish.quiet=true\r\n",
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveOldStyleTagDisjunctionsAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--tags @a,@b --tags "not @wip"
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--tags @a,@b --tags "not @wip"
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void retainCarriageReturnsWhenFlaggingForManualMigration() {
        rewriteRun(
                properties(
                        "cucumber.publish.quiet=true\r\ncucumber.options=--format pretty\r\n",
                        "cucumber.publish.quiet=true\r\n# TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand\r\ncucumber.options=--format pretty\r\n",
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveOptionsWithoutAPropertyEquivalentAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--glue com.example.app --threads 4
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--glue com.example.app --threads 4
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveUnrecognizedOptionsAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--format pretty
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--format pretty
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveSeveralNameFiltersAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--name '^first.*' --name '^second.*'
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--name '^first.*' --name '^second.*'
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveOldStyleTagNegationsAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--tags ~@wip
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--tags ~@wip
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveValuesContainingTheSeparatorAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.options=--glue 'com.example.app,com.example.other'
                        """,
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--glue 'com.example.app,com.example.other'
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveConflictingPropertiesAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.glue=com.example.other
                        cucumber.options=--glue com.example.app
                        """,
                        """
                        cucumber.glue=com.example.other
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--glue com.example.app
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void retainAnExistingManualMigrationComment() {
        rewriteRun(
                properties(
                        """
                        # TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand
                        cucumber.options=--format pretty
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void leaveOtherPropertiesAlone() {
        rewriteRun(
                properties(
                        """
                        cucumber.glue=com.example.app
                        """,
                        spec -> spec.path("src/test/resources/cucumber.properties")));
    }

    @Test
    void mavenSystemPropertyVariables() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.options>--glue com.example.app --tags @wip</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.filter.tags>@wip</cucumber.filter.tags>
                                                <cucumber.glue>com.example.app</cucumber.glue>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """));
    }

    @Test
    void retainMarkupAlreadyEscapedInThePom() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.options>--name '&amp;lt;example&amp;gt;'</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.filter.name>&amp;lt;example&amp;gt;</cucumber.filter.name>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """));
    }

    @Test
    void leaveWhitespaceAroundEscapedCharactersAlone() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.options>--name 'first &amp; second'</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <!-- TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand -->
                                                <cucumber.options>--name 'first &amp; second'</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """));
    }

    @Test
    void leaveMarkupOtherThanCharacterDataAlone() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <cucumber.options><!-- legacy -->--glue com.example.app</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """,
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <build>
                                <plugins>
                                    <plugin>
                                        <groupId>org.apache.maven.plugins</groupId>
                                        <artifactId>maven-surefire-plugin</artifactId>
                                        <configuration>
                                            <systemPropertyVariables>
                                                <!-- TODO Cucumber-JVM 6.0.0 no longer reads cucumber.options; migrate to the individual cucumber.* properties by hand -->
                                                <cucumber.options><!-- legacy -->--glue com.example.app</cucumber.options>
                                            </systemPropertyVariables>
                                        </configuration>
                                    </plugin>
                                </plugins>
                            </build>
                        </project>
                        """));
    }

    @Test
    void leaveMavenPropertiesAlone() {
        rewriteRun(
                pomXml(
                        """
                        <project>
                            <modelVersion>4.0.0</modelVersion>
                            <groupId>com.example</groupId>
                            <artifactId>app</artifactId>
                            <version>1.0.0</version>
                            <properties>
                                <cucumber.options>--glue com.example.app</cucumber.options>
                            </properties>
                        </project>
                        """));
    }
}
