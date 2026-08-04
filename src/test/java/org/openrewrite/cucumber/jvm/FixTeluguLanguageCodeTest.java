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

import static org.openrewrite.test.SourceSpecs.text;

@Issue("https://github.com/openrewrite/rewrite-cucumber-jvm/issues/39")
class FixTeluguLanguageCodeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipeFromResources("org.openrewrite.cucumber.jvm.FixTeluguLanguageCode");
    }

    @DocumentExample
    @Test
    void featureFileLanguageHeader() {
        rewriteRun(
                text(
                        """
                        # language: tl
                        గుణము: మొదటి గుణము
                        """,
                        """
                        # language: te
                        గుణము: మొదటి గుణము
                        """,
                        spec -> spec.path("src/test/resources/features/telugu.feature")));
    }

    @Test
    void leaveOtherLanguagesAlone() {
        rewriteRun(
                text(
                        """
                        # language: nl
                        Functionaliteit: eerste functionaliteit
                        """,
                        spec -> spec.path("src/test/resources/features/dutch.feature")));
    }

    @Test
    void leaveNonFeatureFilesAlone() {
        rewriteRun(
                text(
                        """
                        # language: tl
                        """,
                        spec -> spec.path("src/test/resources/notes.txt")));
    }
}
