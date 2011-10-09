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
package org.gradle.execution

import org.gradle.api.Action
import org.gradle.api.CircularReferenceException
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionGraphListener
import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.tasks.TaskStateInternal
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskDependency
import org.gradle.listener.ListenerBroadcast
import org.gradle.listener.ListenerManager
import spock.lang.Specification

class DefaultTaskGraphExecuterTest extends Specification {
    final ListenerManager listenerManager = Mock()
    final TaskFailureHandler failureHandler = Mock()
    final List<Task> executedTasks = []
    DefaultTaskGraphExecuter taskExecuter;

    def setup() {
        1 * listenerManager.createAnonymousBroadcaster(TaskExecutionGraphListener.class) >> new ListenerBroadcast<TaskExecutionGraphListener>(TaskExecutionGraphListener.class)
        1 * listenerManager.createAnonymousBroadcaster(TaskExecutionListener.class) >> new ListenerBroadcast<TaskExecutionListener>(TaskExecutionListener.class)

        taskExecuter = new DefaultTaskGraphExecuter(listenerManager);
    }

    def executesTasksInDependencyOrder() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        when:
        taskExecuter.addTasks([d])
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [a, b, c, d]
    }

    def executesDependenciesInNameOrder() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d", b, a, c)

        when:
        taskExecuter.addTasks([d])
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [a, b, c, d]
    }

    def executesTasksAddedInASingleBatchInNameOrder() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")

        when:
        taskExecuter.addTasks([b, c, a])
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [a, b, c]
    }

    def executesBatchesInOrderAdded() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c")
        Task d = task("d")

        when:
        taskExecuter.addTasks([c, b])
        taskExecuter.addTasks([d, a])
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [b, c, a, d]
    }

    def executesSharedDependenciesOfBatchesOnceOnly() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", a, b)
        Task d = task("d")
        Task e = task("e", b, d)

        when:
        taskExecuter.addTasks([c])
        taskExecuter.addTasks([e])
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [a, b, c, d, e]
    }

    def canGetAllTasksInGraphInExecutionOrder() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        given:
        taskExecuter.addTasks([d])

        expect:
        taskExecuter.allTasks == [a, b, c, d]
    }

    def canQueryTheContentsOfTheGraph() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c", b, a)
        Task d = task("d", c)

        given:
        taskExecuter.addTasks([d])

        expect:
        taskExecuter.hasTask(":a")
        taskExecuter.hasTask(a)
        taskExecuter.hasTask(":b")
        taskExecuter.hasTask(b)
        taskExecuter.hasTask(":c")
        taskExecuter.hasTask(c)
        taskExecuter.hasTask(":d")
        taskExecuter.hasTask(d)
    }

    def cannotUseGetterMethodsWhenGraphHasNotBeenCalculated() {
        when:
        taskExecuter.hasTask(":a")

        then:
        IllegalStateException e = thrown()
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskExecuter.hasTask(task("a"))

        then:
        e = thrown()
        e.message == "Task information is not available, as this task execution graph has not been populated."

        when:
        taskExecuter.getAllTasks()

        then:
        e = thrown()
        e.message == "Task information is not available, as this task execution graph has not been populated."
    }

    def discardsTasksAfterExecute() {
        Task a = task("a")
        Task b = task("b", a)

        given:
        taskExecuter.addTasks([b])
        taskExecuter.execute(failureHandler)

        expect:
        !taskExecuter.hasTask(":a")
        !taskExecuter.hasTask(a)
        taskExecuter.allTasks.isEmpty()
    }

    def canExecuteMultipleTimes() {
        Task a = task("a")
        Task b = task("b", a)
        Task c = task("c")

        given:
        taskExecuter.addTasks([b])
        taskExecuter.execute(failureHandler)
        executedTasks.clear()

        when:
        taskExecuter.addTasks([c])

        then:
        taskExecuter.allTasks == [c]

        when:
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [c]
    }

    def cannotAddTaskWithCircularReference() {
        Task a = createTask("a")
        Task b = task("b", a)
        Task c = task("c", b)
        dependsOn(a, c)

        when:
        taskExecuter.addTasks([c])

        then:
        CircularReferenceException e = thrown()
        e.message == 'Circular dependency between tasks. Cycle includes [task a, task c].'
    }

    def notifiesGraphListenerBeforeExecute() {
        TaskExecutionGraphListener listener = Mock()
        Task a = task("a")

        given:
        taskExecuter.addTaskExecutionGraphListener(listener)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * listener.graphPopulated(taskExecuter)
    }

    def executesWhenReadyClosureBeforeExecute() {
        Closure cl = Mock()
        Task a = task("a")

        given:
        taskExecuter.whenReady(cl)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * cl.call([taskExecuter] as Object[])
        1 * cl.maximumNumberOfParameters >> 1
    }

    def notifiesTaskListenerAsTasksAreExecuted() {
        TaskExecutionListener listener = Mock()
        Task a = task("a")
        Task b = task("b")

        given:
        taskExecuter.addTaskExecutionListener(listener);
        taskExecuter.addTasks([a, b]);

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * listener.beforeExecute(a)

        and:
        1 * listener.afterExecute(a, !null)

        and:
        1 * listener.beforeExecute(b)

        and:
        1 * listener.afterExecute(b, !null)
    }

    def notifiesTaskListenerWhenTaskFails() {
        TaskExecutionListener listener = Mock()
        RuntimeException failure = new RuntimeException()
        Task a = brokenTask("a", failure)

        given:
        taskExecuter.addTaskExecutionListener(listener)
        taskExecuter.addTasks([a])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * listener.beforeExecute(a)

        and:
        1 * listener.afterExecute(a, !null)
    }

    def notifiesBeforeTaskClosureAsTasksAreExecuted() {
        Closure cl = Mock()
        Task a = task("a")
        Task b = task("b")

        given:
        _ * cl.maximumNumberOfParameters >> 1
        taskExecuter.beforeTask(cl)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * cl.call(a)

        and:
        1 * cl.call(b)
    }

    def notifiesAfterTaskClosureAsTasksAreExecuted() {
        Closure cl = Mock()
        Task a = task("a")
        Task b = task("b")

        given:
        _ * cl.maximumNumberOfParameters >> 1
        taskExecuter.afterTask(cl)
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * cl.call(a)

        and:
        1 * cl.call(b)
    }

    def stopsExecutionOnFailureWhenFailureHandlerIndicatesThatExecutionShouldStop() {
        RuntimeException failure = new RuntimeException()
        Task a = brokenTask("a", failure)
        Task b = task("b")

        given:
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * failureHandler.onTaskFailure(a) >> false

        and:
        executedTasks == [a]
    }

    def continuesExecutionOnFailureWhenFailureHandlerIndicatesThatExecutionShouldContinue() {
        RuntimeException failure = new RuntimeException()
        Task a = brokenTask("a", failure)
        Task b = task("b")

        given:
        taskExecuter.addTasks([a, b])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * failureHandler.onTaskFailure(a) >> true

        and:
        executedTasks == [a, b]
    }

    def doesNotAttemptToExecuteTasksWhoseDependenciesFailedToExecute() {
        RuntimeException failure = new RuntimeException()
        Task a = brokenTask("a", failure)
        Task b = task("b", a)
        Task c = task("c")

        given:
        taskExecuter.addTasks([b, c])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * failureHandler.onTaskFailure(a) >> true

        and:
        executedTasks == [a, c]
    }

    def doesNotExecuteFilteredTasks() {
        Task a = task("a")
        Task b = task("b")
        Spec<Task> spec = {it != a } as Spec

        given:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([a, b]);

        when:
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [b]
    }

    def doesNotExecuteDependenciesOfFilteredTasks() {
        Task a = task("a", task("dep-a"))
        Task b = task("b")
        Spec<Task> spec = {it != a } as Spec

        given:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([a, b]);

        when:
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [b]
    }

    def doesNotExecuteFilteredDependencies() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", a, b)
        Spec<Task> spec = {it != a } as Spec

        given:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([c])

        when:
        taskExecuter.execute(failureHandler)

        then:
        executedTasks == [b, c]
    }

    def willExecuteATaskWhoseDependenciesHaveAllBeenFilteredWhenContinuingAfterAFailure() {
        RuntimeException failure = new RuntimeException()
        Task a = brokenTask("a", failure)
        Task b = task("b")
        Task c = task("c", b)
        Spec<Task> spec = {it != b } as Spec

        given:
        taskExecuter.useFilter(spec)
        taskExecuter.addTasks([a, c])

        when:
        taskExecuter.execute(failureHandler)

        then:
        1 * failureHandler.onTaskFailure(a) >> true

        and:
        executedTasks == [a, c]
    }

    def executesActionWhenTaskIsAddedToGraph() {
        Task a = task("a")
        Task b = task("b", a)
        Action<TaskGraphNode> action = Mock()

        given:
        taskExecuter.whenTaskAdded(action);

        when:
        taskExecuter.addTasks([b])

        then:
        1 * action.execute({it.task == b})

        and:
        1 * action.execute({it.task == a})
    }

    def actionCanAddDependencyForTask() {
        Task a = task("a")
        Task b = task("b")
        Action<TaskGraphNode> action = Mock()

        given:
        taskExecuter.whenTaskAdded(action)

        when:
        taskExecuter.addTasks([b])

        then:
        1 * action.execute({it.task == b}) >> {
            def node = it[0]
            node.addDependency(a)
        }

        and:
        taskExecuter.allTasks == [a, b]
    }

    def actionCanAddDependeeForTask() {
        Task a = task("a")
        Task b = task("b")
        Action<TaskGraphNode> action = Mock()

        given:
        taskExecuter.whenTaskAdded(action)

        when:
        taskExecuter.addTasks([b])

        then:
        1 * action.execute({it.task == b}) >> {
            def node = it[0]
            node.addDependee(a)
        }

        and:
        taskExecuter.allTasks == [b, a]
    }

    def actionCanAddDependeeWhichDependsOnCurrentTask() {
        Task a = task("a")
        Task b = task("b")
        Task c = task("c", a, b)
        Action<TaskGraphNode> action = Mock()

        given:
        taskExecuter.whenTaskAdded(action);

        when:
        taskExecuter.addTasks([c])

        then:
        1 * action.execute({it.task == a}) >> {
            def node = it[0]
            node.addDependee(c)
        }

        and:
        taskExecuter.allTasks == [a, b, c]
    }

    private Task brokenTask(String name, RuntimeException failure, Task... dependsOnTasks) {
        TaskInternal task = createTask(name)
        dependsOn(task, dependsOnTasks)
        (0..1) * task.executeWithoutThrowingTaskFailure() >> { executedTasks << task }
        _ * task.state.failure >> failure
        return task;
    }

    private Task task(String name, Task... dependsOnTasks) {
        final TaskInternal task = createTask(name)
        dependsOn(task, dependsOnTasks)
        (0..1) * task.executeWithoutThrowingTaskFailure() >> {
            executedTasks << task
        }
        _ * task.state.failure >> null
        return task;
    }

    private void dependsOn(Task task, Task... dependsOn) {
        TaskDependency taskDependency = Mock()
        _ * task.taskDependencies >> taskDependency
        _ * taskDependency.getDependencies(task) >> (dependsOn as Set)
    }

    private TaskInternal createTask(String name) {
        TaskInternal task = Mock()
        TaskStateInternal state = Mock()

        _ * task.toString() >> "task $name"
        _ * task.name >> name
        _ * task.path >> ":$name"
        _ * task.state >> state
        _ * task.compareTo(!null) >> {
            name.compareTo(it[0].name)
        }

        return task;
    }
}
