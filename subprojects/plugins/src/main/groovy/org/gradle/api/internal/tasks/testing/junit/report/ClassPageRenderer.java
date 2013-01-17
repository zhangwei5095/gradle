/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.tasks.testing.junit.report;

import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.reporting.CodePanelRenderer;
import org.gradle.reporting.HtmlBuilder;

import java.io.PrintWriter;
import java.io.StringWriter;

class ClassPageRenderer extends PageRenderer<ClassTestResults> {
    private final CodePanelRenderer codePanelRenderer = new CodePanelRenderer();
    private final TestResultsProvider resultsProvider;
    private final String className;

    public ClassPageRenderer(String className, TestResultsProvider provider) {
        this.className = className;
        this.resultsProvider = provider;
    }

    @Override
    protected void renderBreadcrumbs(HtmlBuilder parent) {
        parent.div().classAttr("breadcrumbs");
        parent.a().href("index.html").text("all").end();
        parent.text(" > ");
        parent.a().href(String.format("%s.html", getResults().getPackageResults().getName())).text(getResults().getPackageResults().getName()).end();
        parent.text(String.format(" > %s", getResults().getSimpleName()));
        parent.end();
    }

    private void renderTests(HtmlBuilder parent) {
        parent.table();
        parent.thead();
        parent.tr();
        parent.th().text("Test").end();
        parent.th().text("Duration").end();
        parent.th().text("Result").end();
        parent.end();
        parent.end();

        for (TestResult test : getResults().getTestResults()) {
            parent.tr();
            parent.td().classAttr(test.getStatusClass()).text(test.getName()).end();
            parent.td().text(test.getFormattedDuration()).end();
            parent.td().classAttr(test.getStatusClass()).text(test.getFormattedResultType()).end();
            parent.end();
        }
        parent.end();
    }

    @Override
    protected void renderFailures(HtmlBuilder html) {
        for (TestResult test : getResults().getFailures()) {
            html.div().classAttr("test");
            html.a().attr("name", test.getId().toString()).text("").end();
            html.h3().classAttr(test.getStatusClass()).text(test.getName()).end();
            for (TestFailure failure : test.getFailures()) {
                codePanelRenderer.render(failure.getStackTrace(), html);
            }
            html.end();
        }
    }

    @Override
    protected void registerTabs() {
        addFailuresTab();
        addTab("Tests", new Action<HtmlBuilder>() {
            public void execute(HtmlBuilder html) {
                renderTests(html);
            }
        });
        if (resultsProvider.hasOutput(className, TestOutputEvent.Destination.StdOut)) {
            addTab("Standard output", new Action<HtmlBuilder>() {
                public void execute(HtmlBuilder element) {
                    element.span().classAttr("code");
                    element.pre();
                    element.text("");
                    resultsProvider.writeOutputs(className, TestOutputEvent.Destination.StdOut, element.getWriter());
                    element.end();
                    element.end();
                }
            });
        }
        if (resultsProvider.hasOutput(className, TestOutputEvent.Destination.StdErr)) {
            addTab("Standard error", new Action<HtmlBuilder>() {
                public void execute(HtmlBuilder html) {
                    html.span().classAttr("code");
                    html.pre();
                    html.text("");
                    resultsProvider.writeOutputs(className, TestOutputEvent.Destination.StdErr, html.getWriter());
                    html.end();
                    html.end();
                }
            });
        }
    }

    /**
     * @TODO RG: This method can consume a lot of memory depending on the amount of output We'll when moving away from dom based report generation
     */
    private String getOutputString(TestOutputEvent.Destination destination) {
        final StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);
        resultsProvider.writeOutputs(className, destination, writer);
        writer.close();
        return stringWriter.toString();
    }
}
