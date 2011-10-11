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

import spock.lang.Specification
import org.gradle.api.internal.GradleInternal
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.tasks.TaskDependency

class TaskDependenciesConfigurationActionTest extends Specification {
    final BuildExecutionContext context = Mock()
    final GradleInternal gradle = Mock()
    final TaskGraphExecuter taskGraph = Mock()
    final TaskGraphNode node = Mock()
    final TaskDependenciesConfigurationAction action = new TaskDependenciesConfigurationAction()

    def setup() {
        _ * context.gradle >> gradle
        _ * gradle.taskGraph >> taskGraph
    }
    
    def "attaches action which adds dependencies of each task to the task graph"() {
        Task dependency = Mock()
        Task task = Mock()
        TaskDependency dependencies = Mock()
        Action<TaskGraphNode> nodeAction

        given:
        _ * node.task >> task
        _ * task.taskDependencies >> dependencies
        _ * dependencies.getDependencies(task) >> ([dependency] as Set)

        when:
        action.configure(context)

        then:
        1 * taskGraph.whenTaskAdded(!null) >> { nodeAction = it[0] }

        when:
        nodeAction.execute(node)

        then:
        1 * node.addDependency(dependency)
    }
}
