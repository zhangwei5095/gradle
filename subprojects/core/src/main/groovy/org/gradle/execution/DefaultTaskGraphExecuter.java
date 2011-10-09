/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.execution;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.CircularReferenceException;
import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.execution.TaskExecutionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.CachingTaskDependencyResolveContext;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.listener.ActionBroadcast;
import org.gradle.listener.ListenerBroadcast;
import org.gradle.listener.ListenerManager;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Hans Dockter
 */
public class DefaultTaskGraphExecuter implements TaskGraphExecuter {
    private final ListenerBroadcast<TaskExecutionGraphListener> graphListeners;
    private final ListenerBroadcast<TaskExecutionListener> taskListeners;
    private final ActionBroadcast<TaskGraphNode> nodeListeners;
    private final Map<Task, TaskInfo> executionPlan = new LinkedHashMap<Task, TaskInfo>();
    private boolean populated;
    private Spec<? super Task> filter = Specs.satisfyAll();
    private TaskFailureHandler failureHandler;

    public DefaultTaskGraphExecuter(ListenerManager listenerManager) {
        graphListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class);
        taskListeners = listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class);
        nodeListeners = new ActionBroadcast<TaskGraphNode>();
    }

    public void useFilter(Spec<? super Task> filter) {
        this.filter = filter;
    }

    public void addTasks(Iterable<? extends Task> tasks) {
        assert tasks != null;

        Set<Task> sortedTasks = new TreeSet<Task>();
        for (Task task : tasks) {
            sortedTasks.add(task);
        }
        fillDag(sortedTasks);
        populated = true;
    }

    public void execute(TaskFailureHandler handler) {
        failureHandler = handler;
        graphListeners.getSource().graphPopulated(this);
        try {
            doExecute(executionPlan.values());
        } finally {
            executionPlan.clear();
            failureHandler = null;
        }
    }

    private void fillDag(Collection<? extends Task> tasks) {
        Set<Task> visiting = new HashSet<Task>();
        List<Task> queue = new ArrayList<Task>();
        queue.addAll(tasks);
        CachingTaskDependencyResolveContext context = new CachingTaskDependencyResolveContext();

        while (!queue.isEmpty()) {
            Task task = queue.get(0);
            if (!filter.isSatisfiedBy(task)) {
                // Filtered - skip
                queue.remove(0);
                continue;
            }
            if (executionPlan.containsKey(task)) {
                // Already in plan - skip
                queue.remove(0);
                continue;
            }

            if (visiting.add(task)) {
                // Have not seen this task before - add its dependencies to the head of the queue and leave this
                // task in the queue
                TaskGraphNodeImpl node = new TaskGraphNodeImpl(task);
                nodeListeners.execute(node);
                node.dependencies.addAll(context.getDependencies(task));
                for (Task dependsOnTask : node.dependencies) {
                    if (visiting.contains(dependsOnTask)) {
                        throw new CircularReferenceException(String.format(
                                "Circular dependency between tasks. Cycle includes [%s, %s].", task, dependsOnTask));
                    }
                    queue.add(0, dependsOnTask);
                }
                for (Task dependee : node.dependees) {
                    if (!visiting.contains(dependee)) {
                        queue.add(1, dependee);
                    }
                    // Else, the dependee already depends on this task, so skip
                }
            } else {
                // Have visited this task's dependencies - add it to the end of the plan
                queue.remove(0);
                visiting.remove(task);
                Set<TaskInfo> dependencies = new HashSet<TaskInfo>();
                for (Task dependency : context.getDependencies(task)) {
                    TaskInfo dependencyInfo = executionPlan.get(dependency);
                    if (dependencyInfo != null) {
                        dependencies.add(dependencyInfo);
                    }
                    // else - the dependency has been filtered, so ignore it
                }
                TaskInfo node = new TaskInfo((TaskInternal) task, dependencies);
                executionPlan.put(task, node);
            }
        }
    }

    public void addTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.add(listener);
    }

    public void removeTaskExecutionGraphListener(TaskExecutionGraphListener listener) {
        graphListeners.remove(listener);
    }

    public void whenReady(final Closure closure) {
        graphListeners.add("graphPopulated", closure);
    }

    public void addTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.add(listener);
    }

    public void removeTaskExecutionListener(TaskExecutionListener listener) {
        taskListeners.remove(listener);
    }

    public void beforeTask(final Closure closure) {
        taskListeners.add("beforeExecute", closure);
    }

    public void afterTask(final Closure closure) {
        taskListeners.add("afterExecute", closure);
    }

    public void whenTaskAdded(Action<? super TaskGraphNode> action) {
        nodeListeners.add(action);
    }

    public void whenTaskAdded(Closure action) {
        nodeListeners.add(action);
    }

    private void doExecute(Iterable<? extends TaskInfo> tasks) {
        AtomicBoolean stop = new AtomicBoolean();
        for (TaskInfo node : tasks) {
            executeTask(node, stop);
            if (stop.get()) {
                break;
            }
        }
    }

    private void executeTask(TaskInfo node, AtomicBoolean stop) {
        TaskInternal task = node.task;
        for (TaskInfo dependency : node.dependencies) {
            if (!dependency.executed) {
                // Cannot execute this task, as some dependencies have not been executed
                return;
            }
        }
        
        taskListeners.getSource().beforeExecute(task);
        try {
            task.executeWithoutThrowingTaskFailure();
            if (task.getState().getFailure() != null) {
                boolean keepGoing = failureHandler.onTaskFailure(task);
                if (!keepGoing) {
                    stop.set(true);
                }
            } else {
                node.executed = true;
            }
        } finally {
            taskListeners.getSource().afterExecute(task, task.getState());
        }
    }

    public boolean hasTask(Task task) {
        assertPopulated();
        return executionPlan.containsKey(task);
    }

    public boolean hasTask(String path) {
        assertPopulated();
        assert path != null && path.length() > 0;
        for (Task task : getAllTasks()) {
            if (task.getPath().equals(path)) {
                return true;
            }
        }
        return false;
    }

    public List<Task> getAllTasks() {
        assertPopulated();
        return new ArrayList<Task>(executionPlan.keySet());
    }

    private void assertPopulated() {
        if (!populated) {
            throw new IllegalStateException(
                    "Task information is not available, as this task execution graph has not been populated.");
        }
    }
    
    private static class TaskInfo {
        private final TaskInternal task;
        private final Set<TaskInfo> dependencies;
        private boolean executed;

        private TaskInfo(TaskInternal task) {
            this.task = task;
            this.dependencies = new HashSet<TaskInfo>();
        }

        private TaskInfo(TaskInternal task, Set<TaskInfo> dependencies) {
            this.task = task;
            this.dependencies = dependencies;
        }

        public Task getTask() {
            return task;
        }
    }

    private static class TaskGraphNodeImpl implements TaskGraphNode {
        private final Task task;
        private final Set<Task> dependencies = new TreeSet<Task>(Collections.reverseOrder());
        private final Set<Task> dependees = new TreeSet<Task>();

        public TaskGraphNodeImpl(Task task) {
            this.task = task;
        }

        public Task getTask() {
            return task;
        }

        public void addDependency(Task task) {
            dependencies.add(task);
        }

        public void addDependee(Task task) {
            dependees.add(task);
        }
    }
}
