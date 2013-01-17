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
import org.gradle.reporting.*;

abstract class PageRenderer<T extends CompositeTestResults> extends TabbedPageRenderer<T> {
    private T results;
    private final TabsRenderer<T> tabsRenderer = new TabsRenderer<T>();

    protected T getResults() {
        return results;
    }

    protected abstract void renderBreadcrumbs(HtmlBuilder parent);

    protected abstract void registerTabs();

    protected void addTab(String title, final Action<HtmlBuilder> contentRenderer) {
        tabsRenderer.add(title, new AbstractHtmlReportRenderer<T>() {
            @Override
            public void render(T model, HtmlBuilder parent) {
                contentRenderer.execute(parent);
            }
        });
    }

    protected void renderTabs(HtmlBuilder html) {
        tabsRenderer.render(getModel(), html);
    }

    protected void addFailuresTab() {
        if (!results.getFailures().isEmpty()) {
            addTab("Failed tests", new Action<HtmlBuilder>() {
                public void execute(HtmlBuilder element) {
                    renderFailures(element);
                }
            });
        }
    }

    protected void renderFailures(HtmlBuilder html) {
        html.ul().classAttr("linkList");
        for (TestResult test : results.getFailures()) {
            html.li();
                html.a().href(String.format("%s.html", test.getClassResults().getName())).text(test.getClassResults().getSimpleName()).end();
                html.text(".");
                html.a().href(String.format("%s.html#%s", test.getClassResults().getName(), test.getName())).text(test.getName()).end();
            html.end();
        }
        html.end();
    }

    protected <T extends TestResultModel> AbstractHtmlReportRenderer<T> withStatus(final AbstractHtmlReportRenderer<T> renderer) {
        return new AbstractHtmlReportRenderer<T>() {
            @Override
            public void render(T model, HtmlBuilder parent) {
                parent.classAttr(model.getStatusClass());
                renderer.render(model, parent);
            }
        };
    }

    @Override
    protected String getTitle() {
        return getModel().getTitle();
    }

    @Override
    protected String getPageTitle() {
        return String.format("Test results - %s", getModel().getTitle());
    }

    @Override
    protected AbstractHtmlReportRenderer<T> getHeaderRenderer() {
        return new AbstractHtmlReportRenderer<T>() {
            @Override
            public void render(T model, HtmlBuilder content) {
                PageRenderer.this.results = model;
                renderBreadcrumbs(content);

                // summary
                content.div().attr("id", "summary");
                        content.table();
                                content.tr();
                                    content.td();
                                            content.div().classAttr("summaryGroup");
                                                content.table();
                                                    content.tr();
                                                        content.td();
                                                            content.div().classAttr("infoBox").attr("id", "tests");
                                                                content.div().classAttr("counter").text(Integer.toString(results.getTestCount())).end();
                                                                content.p().text("tests").end();
                                                            content.end();
                                                        content.end();
                                                        content.td();
                                                            content.div().classAttr("infoBox").attr("id", "failures");
                                                                content.div().classAttr("counter").text(Integer.toString(results.getFailureCount())).end();
                                                                content.p().text("failures").end();
                                                            content.end();
                                                        content.end();
                                                        content.td();
                                                            content.div().classAttr("infoBox").attr("id", "duration");
                                                                content.div().classAttr("counter").text(results.getFormattedDuration()).end();
                                                                content.p().text("duration").end();
                                                            content.end();
                                                        content.end();
                                                    content.end();
                                                content.end();
                                            content.end();
                                    content.end();
                                    content.td();
                                            content.div().classAttr(String.format("infoBox %s", results.getStatusClass())).attr("id", "successRate");
                                                content.div().classAttr("percent").text(results.getFormattedSuccessRate()).end();
                                                content.p().text("successful").end();
                                            content.end();
                                    content.end();
                                content.end();
                        content.end();
                content.end();
            }
        };
    }

    @Override
    protected AbstractHtmlReportRenderer<T> getContentRenderer() {
        return new AbstractHtmlReportRenderer<T>() {
            @Override
            public void render(T model, HtmlBuilder content) {
                PageRenderer.this.results = model;
                tabsRenderer.clear();
                registerTabs();
                renderTabs(content);
            }
        };
    }
}
