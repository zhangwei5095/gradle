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
package org.gradle.reporting;

import java.util.ArrayList;
import java.util.List;

public class TabsRenderer<T> extends AbstractHtmlReportRenderer<T> {
    private final List<TabDefinition> tabs = new ArrayList<TabDefinition>();

    public void add(String title, AbstractHtmlReportRenderer<T> contentRenderer) {
        tabs.add(new TabDefinition(title, contentRenderer));
    }

    public void clear() {
        tabs.clear();
    }

    @Override
    public void render(T model, HtmlBuilder parent) {
        parent.div().attr("id", "tabs");
            parent.ul().classAttr("tabLinks");
                for (int i = 0; i < this.tabs.size(); i++) {
                    TabDefinition tab = this.tabs.get(i);
                    String tabId = String.format("tab%s", i);
                    parent.li();
                        parent.a().href("#" + tabId).text(tab.title).end();
                    parent.end();
                }
            parent.end();

            for (int i = 0; i < this.tabs.size(); i++) {
                TabDefinition tab = this.tabs.get(i);
                String tabId = String.format("tab%s", i);
                parent.div().attr("id", tabId).classAttr("tab");
                    parent.h2().text(tab.title).end();
                    tab.renderer.render(model, parent);
                parent.end();
            }
        parent.end();
    }

    private class TabDefinition {
        final String title;
        final AbstractHtmlReportRenderer<T> renderer;

        private TabDefinition(String title, AbstractHtmlReportRenderer<T> renderer) {
            this.title = title;
            this.renderer = renderer;
        }
    }
}
