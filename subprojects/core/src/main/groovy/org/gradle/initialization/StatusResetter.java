/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.BuildAdapter;
import org.gradle.BuildResult;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.invocation.Gradle;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class StatusResetter extends BuildAdapter implements TaskExecutionGraphListener {

    private Set<AbstractTask> tasks;

    public void buildStarted(Gradle gradle) {
        tasks = null;
    }

    public void buildFinished(BuildResult result) {
        if (tasks != null) {
            for (AbstractTask task : tasks) {
                task.getState().reset();
            }
        }
        tasks = null;
    }

    public void graphPopulated(TaskExecutionGraph graph) {
        tasks = new HashSet<AbstractTask>((List) graph.getAllTasks());
    }
}
