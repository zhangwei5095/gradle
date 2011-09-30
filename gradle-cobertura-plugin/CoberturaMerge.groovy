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

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection.AntType
import org.gradle.api.tasks.Optional

/**
 * Merges Cobertura reports into an aggregate report.
 */
class CoberturaMerge extends DefaultTask {
	/**
	 * The Cobertura data files (typically named cobertura.ser) to be merged.
	 */
	@InputFiles
	FileCollection inputDataFiles

	@Input
	FileCollection coberturaClassPath

	/**
	 * The maximum amount of memory to allocate for this task. Only needs to be
	 * set if the task has previously failed with an out-of-memory exception.
	 * <p>Example:
	 * <pre>maxMemory = "256M"</pre>
	 */
	@Input
	@Optional
	String maxMemory

	/**
	 * The merged Cobertura data file.
	 */
	@OutputFile
	File outputDataFile

	@TaskAction
	void merge() {
		project.ant { builder ->
			taskdef(name: "coberturaMerge", classname: "net.sourceforge.cobertura.ant.MergeTask",
						classpath: coberturaClassPath.asPath)
			coberturaReport(datafile: outputDataFile, maxmemory: maxMemory) {
				inputDataFiles.addToAntBuilder(builder, "fileset", AntType.FileSet)
			}
		}
	}
}
