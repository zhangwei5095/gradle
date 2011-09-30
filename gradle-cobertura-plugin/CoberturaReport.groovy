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

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.SourceTask
import org.gradle.api.file.FileCollection.AntType

/**
 * Creates a Cobertura code coverage report.
 */
class CoberturaReport extends SourceTask {
	/**
	 * The type of report to be generated, one of "xml" and "html".
	 */
	@Input
	String format

	/**
	 * The Cobertura data file containing the results of the code coverage analysis.
	 */
	@Input
	File dataFile

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
	 * The encoding of the source files passed to this task.
	 */
	@Input
	@Optional
	String sourceEncoding

	/**
	 * Class path containing the Cobertura library to be used.
	 */
	@Input
	FileCollection coberturaClassPath

	/**
	 * The directory where the generated report will be stored.
	 */
	@OutputDirectory
	File reportDir

	@TaskAction
	void generateReport() {
		project.ant { builder ->
			taskdef(name: "coberturaReport", classname: "net.sourceforge.cobertura.ant.ReportTask",
					classpath: coberturaClassPath.asPath)
			coberturaReport(format: format, datafile: dataFile, destdir: reportDir, maxmemory: maxMemory, encoding: sourceEncoding) {
				source.addToAntBuilder(builder, "fileset", AntType.FileSet)
			}
		}
	}
}
