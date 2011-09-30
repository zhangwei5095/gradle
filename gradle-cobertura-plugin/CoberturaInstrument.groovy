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

import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileCollection.AntType

/**
 * Instruments class files in preparation for Cobertura code coverage analysis.
 */
class CoberturaInstrument extends SourceTask {
	/**
	 * Class path containing the Cobertura library to be used.
	 */
	@Input
	FileCollection coberturaClassPath

	@Input
	@Optional
	String ignoreMethodsPattern

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
	 * The directory where instrumented classes will be stored.
	 */
	@OutputDirectory
	File outputDir

	/**
	 * The file where metadata about instrumented classes is stored.
	 * This file will be updated when tests are run and serves as an input
	 * to the CoberturaReport task.
	 */
	@OutputFile
	File dataFile

	@TaskAction
	void instrument() {
		project.ant { builder ->
			taskdef(name: "coberturaInstrument", classname: "net.sourceforge.cobertura.ant.InstrumentTask",
					classpath: coberturaClassPath.asPath)
			coberturaInstrument(datafile: dataFile, maxmemory: maxMemory, todir: outputDir) {
				source.addToAntBuilder(builder, "fileset", AntType.FileSet)
				ignore(regex: ignoreMethodsPattern)
			}
		}
	}
}
