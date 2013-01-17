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
import org.gradle.reporting.HtmlBuilder;

class PackagePageRenderer extends PageRenderer<PackageTestResults> {

    @Override protected void renderBreadcrumbs(HtmlBuilder parent) {
        parent.div().classAttr("breadcrumbs");
            parent.a().href("index.html").text("all").end();
            parent.text(String.format(" > %s", getResults().getName()));
        parent.end();
    }

    private void renderClasses(HtmlBuilder parent) {
        parent.table();
        parent.thead();
            parent.tr();
                parent.th().text("Class").end();
                parent.th().text("Tests").end();
                parent.th().text("Failures").end();
                parent.th().text("Duration").end();
                parent.th().text("Success rate").end();
            parent.end();
        parent.end();

        for (ClassTestResults testClass : getResults().getClasses()) {
            parent.tr();
                parent.td().classAttr(testClass.getStatusClass());
                    parent.a().href(String.format("%s.html", testClass.getName())).text(testClass.getSimpleName()).end();
                parent.end();
                parent.td().text(Integer.toString(testClass.getTestCount())).end();
                parent.td().text(Integer.toString(testClass.getFailureCount())).end();
                parent.td().text(testClass.getFormattedDuration()).end();
                parent.td().classAttr(testClass.getStatusClass()).text(testClass.getFormattedSuccessRate()).end();
            parent.end();
        }
        parent.end();
    }

    @Override protected void registerTabs() {
        addFailuresTab();
        addTab("Classes", new Action<HtmlBuilder>() {
            public void execute(HtmlBuilder html) {
                renderClasses(html);
            }
        });
    }
}
