/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.build

import org.gradle.api.Project

class BuildTypes {

    private final Project project

    BuildTypes(Project project) {
        this.project = project
    }

    def methodMissing(String name, args) {
        args = args.toList()
        def properties = [:]
        if (args.first() instanceof Map) {
            properties.putAll(args.remove(0))
        }
        def tasks = args*.toString()

        register(name, tasks, properties)
    }

    private register(name, tasks, projectProperties) {
        project.task(name) {
            group = "Build Type"

            project.gradle.projectsEvaluated {
                dependsOn tasks.inject([]) { acc, val ->
                    def taskObjects = val.startsWith(":") ? [project.tasks.getByPath(val)] : project.getTasksByName(val, true).toList()
                    taskObjects*.shouldRunAfter(acc)
                    acc.addAll(taskObjects)
                    acc
                }
            }

            def abbreviation = name[0] + name[1..-1].replaceAll("[a-z]", "")
            def taskNames = project.gradle.startParameter.taskNames
            def usedName = taskNames.find { it in [name, abbreviation] }
            if (usedName) {
                projectProperties.each { k, v ->
                    if (!project.hasProperty(k)) {
                        project.ext."$k" = null
                    }
                    project."$k" = v
                }
            }
         }
    }

}