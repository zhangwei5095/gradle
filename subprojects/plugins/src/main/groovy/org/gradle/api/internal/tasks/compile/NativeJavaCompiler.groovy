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

package org.gradle.api.internal.tasks.compile

import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.WorkResult
import org.gradle.api.internal.file.collections.SimpleFileCollection
import javax.tools.ToolProvider
import java.nio.charset.Charset

class NativeJavaCompiler implements JavaCompiler {
    FileCollection source
    Iterable<File> classpath
    String sourceCompatibility
    String targetCompatibility
    File destinationDir
    CompileOptions compileOptions
    
    void setDependencyCacheDir(File dir) {
        // do nothing
    }

    WorkResult execute() {
        def options = []
        if (destinationDir) { options.addAll("-d", destinationDir.toString()) }
        if (classpath) { options.addAll("-cp", toFileCollection(classpath).asPath) }
        if (sourceCompatibility) { options.addAll("-source", sourceCompatibility) }
        if (targetCompatibility) { options.addAll("-target", targetCompatibility) }
        if (compileOptions.verbose) { options << "-verbose" }
        if (!compileOptions.warnings) { options << "-nowarn" }
        if (!compileOptions.debug) { options << "g:none" }
        if (compileOptions.encoding) { options.addAll("-encoding", compileOptions.encoding) }
        options.addAll(compileOptions.compilerArgs)

        def compiler = ToolProvider.getSystemJavaCompiler()
        def fileManager = compiler.getStandardFileManager(null, null, compileOptions.encoding ? Charset.forName(compileOptions.encoding) : null)
        def compilationUnits = fileManager.getJavaFileObjectsFromFiles(source)
        def task = compiler.getTask(null, null, null, options, null, compilationUnits)
        def success = task.call()
        if (!success) {
            throw new CompilationFailedException()
        }
        return new WorkResult() {
            boolean getDidWork() {
                true
            }
        }
    }
    
    private toFileCollection(Iterable<File> classpath) {
        if (classpath instanceof FileCollection) return classpath
        return new SimpleFileCollection(classpath)
    }
}
