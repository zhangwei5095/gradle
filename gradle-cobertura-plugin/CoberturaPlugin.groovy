/*
* Copyright 2011 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.gradle.api.plugins.cobertura

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.plugins.ReportingBasePlugin

class CoberturaPlugin extends ReportingBasePlugin {
	@Override
	void apply(Project project) {
		def instrumentTasks = []

		def mergeTask = project.task("coberturaMerge", type: CoberturaMerge) {
			conventionMapping.dataFiles = { instrumentTasks.dataFile }
		}

		def coberturaTask = project.task("cobertura", type: CoberturaReport, dependsOn: mergeTask) {
			reportType = "html"
			onlyIf {
				true // TODO
			}
		}

		project.tasks.withType(Test) { testTask ->
			def instrumentTask = project.task("instrument$testTask.name") { // TODO: camelCase
				conventionMapping.dataFile = { "$project.reportsDir/cobertura/cobertura${testTask.name}.ser" as String} // TODO: camelCase
			}
			instrumentTasks << instrumentTask
			def runTask = project.tasks.add(testTask.clone()) // and adapt
			runTask.dependsOn(instrumentTask)
			mergeTask.dependsOn(runTask)
		}



	}

}
