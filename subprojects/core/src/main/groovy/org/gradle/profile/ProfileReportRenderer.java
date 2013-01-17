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
package org.gradle.profile;

import org.gradle.reporting.*;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ProfileReportRenderer {
    public void writeTo(BuildProfile buildProfile, File file) {
        HtmlReportRenderer renderer = new HtmlReportRenderer();
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/base-style.css"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/report.js"));
        renderer.requireResource(getClass().getResource("/org/gradle/reporting/css3-pie-1.0beta3.htc"));
        renderer.requireResource(getClass().getResource("style.css"));
        renderer.renderer(new ProfilePageRenderer()).writeTo(buildProfile, file);
    }
    private static final DurationFormatter DURATION_FORMAT = new DurationFormatter();

    private static class ProfilePageRenderer extends TabbedPageRenderer<BuildProfile> {
        static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss");

        @Override
        protected String getTitle() {
            return "Profile report";
        }

        @Override
        protected AbstractHtmlReportRenderer<BuildProfile> getHeaderRenderer() {
            return new AbstractHtmlReportRenderer<BuildProfile>() {
                @Override
                public void render(BuildProfile model, HtmlBuilder parent) {
                    parent.div().attr("id", "header");
                    parent.p().text(String.format("Profiled with tasks: %s", model.getTaskDescription())).end();
                    parent.p().text(String.format("Run on: %s", DATE_FORMAT.format(model.getBuildStarted()))).end();
                    parent.end();
                }
            };
        }

        @Override
        protected AbstractHtmlReportRenderer<BuildProfile> getContentRenderer() {
            return new AbstractHtmlReportRenderer<BuildProfile>() {
                @Override
                public void render(BuildProfile model, HtmlBuilder parent) {
                    parent.body();
                        parent.div().attr("id", "tabs");
                            parent.ul().classAttr("tabLinks");
                                parent.li().a().href("#tab0").text("Summary").end().end();
                                parent.li().a().href("#tab1").text("Configuration").end().end();
                                parent.li().a().href("#tab2").text("Dependency Resolution").end().end();
                                parent.li().a().href("#tab3").text("Task Execution").end().end();
                            parent.end();
                            parent.div().classAttr("tab").attr("id", "tab0");
                                parent.h2().text("Summary").end();
                                parent.table();
                                    parent.thead();
                                        parent.tr();
                                            parent.th().text("Description").end();
                                            parent.th().classAttr("numeric").text("Duration").end();
                                        parent.end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Total Build Time").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedTotal())).end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Startup").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedStartup())).end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Settings and BuildSrc").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedSettings())).end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Loading Projects").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedProjectsLoading())).end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Configuring Projects").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedAfterProjectsEvaluated())).end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("Task Execution").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getElapsedTotalExecutionTime())).end();
                                    parent.end();
                                parent.end();
                            parent.end();
                            parent.div().classAttr("tab").attr("id", "tab1");
                                parent.h2().text("Configuration").end();
                                parent.table();
                                    parent.thead();
                                        parent.tr();
                                            parent.th().text("Project").end();
                                            parent.th().classAttr("numeric").text("Duration").end();
                                        parent.end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("All projects").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getProjectConfiguration().getElapsedTime())).end();
                                    parent.end();

                                    final List<Operation> operations = model.getProjectConfiguration().getOperations();
                                    //sort in reverse order
                                    Collections.sort(operations, new Comparator<Operation>() {
                                        public int compare(Operation o1, Operation o2) {
                                            return Long.valueOf(o2.getElapsedTime()).compareTo(Long.valueOf(o1.getElapsedTime()));
                                        }
                                    });
                                    for (Operation operation : operations) {
                                        ContinuousOperation continuousOperation = (ContinuousOperation)operation;
                                        parent.tr();
                                            parent.td().text(continuousOperation.toString()).end(); //TOODDDOOO
                                            parent.td().classAttr("numeric").text(DURATION_FORMAT.format(continuousOperation.getElapsedTime())).end(); //TOODDDOOO
                                        parent.end();
                                    }
                                parent.end();
                            parent.end();
                            parent.div().classAttr("tab").attr("id", "tab2");
                                parent.h2().text("Dependency Resolution").end();
                                parent.table();
                                    parent.thead();
                                        parent.tr();
                                            parent.th().text("Dependencies").end();
                                            parent.th().classAttr("numeric").text("Duration").end();
                                        parent.end();
                                    parent.end();
                                    parent.tr();
                                        parent.td().text("All dependencies").end();
                                        parent.td().classAttr("numeric").text(DURATION_FORMAT.format(model.getDependencySets().getElapsedTime())).end();
                                    parent.end();
                                    final List<DependencyResolveProfile> dependencyResolveProfiles = model.getDependencySets().getOperations();
                                    Collections.sort(dependencyResolveProfiles, new Comparator<DependencyResolveProfile>() {
                                        public int compare(DependencyResolveProfile p1, DependencyResolveProfile p2) {
                                            return Long.valueOf(p1.getElapsedTime()).compareTo(Long.valueOf(p2.getElapsedTime()));
                                        }
                                    });
                                    for (DependencyResolveProfile profile : dependencyResolveProfiles) {
                                        parent.tr();
                                            parent.td().text(profile.getPath()).end();
                                            parent.td().classAttr("numeric").text(DURATION_FORMAT.format(profile.getElapsedTime())).end();
                                        parent.end();
                                    }
                                parent.end();
                            parent.end();
                            parent.div().classAttr("tab").attr("id", "tab3");
                                    parent.h2().text("Task Execution").end();
                                    parent.table();
                                        parent.thead();
                                            parent.tr();
                                                parent.th().text("Task").end();
                                                parent.th().classAttr("numeric").text("Duration").end();
                                                parent.th().text("Result").end();
                                            parent.end();
                                        parent.end();
                                        final List<ProjectProfile> projects = model.getProjects();
                                        Collections.sort(projects, new Comparator<ProjectProfile>() {
                                            public int compare(ProjectProfile p1, ProjectProfile p2) {
                                                return Long.valueOf(p1.getTasks().getElapsedTime()).compareTo(Long.valueOf(p2.getTasks().getElapsedTime()));
                                            }
                                        });
                                        for (ProjectProfile project : projects) {
                                            parent.tr();
                                                parent.td().text(project.getPath()).end();
                                                parent.td().classAttr("numeric").text(DURATION_FORMAT.format(project.getTasks().getElapsedTime())).end();
                                                parent.td().text("(total)").end();
                                            parent.end();
                                            final List<TaskExecution> taskExecutions  = project.getTasks().getOperations();
                                            Collections.sort(taskExecutions, new Comparator<TaskExecution>() {
                                                public int compare(TaskExecution p1, TaskExecution p2) {
                                                    return Long.valueOf(p2.getElapsedTime()).compareTo(Long.valueOf(p1.getElapsedTime()));
                                                }
                                            });
                                            for (TaskExecution taskExecution : taskExecutions) {
                                                parent.tr();
                                                    parent.td().classAttr("indentPath").text(taskExecution.getPath()).end();
                                                    parent.td().classAttr("numeric").text(DURATION_FORMAT.format(taskExecution.getElapsedTime())).end();
                                                    parent.td().text(taskExecution.getState().getSkipped() ? taskExecution.getState().getSkipMessage() : (taskExecution.getState().getDidWork()) ? "" : "Did No Work").end();
                                                parent.end();
                                            }
                                        }
                                    parent.end();
                            parent.end();
                        parent.end();
                    parent.end();
                }
            };
        }
    }
}
