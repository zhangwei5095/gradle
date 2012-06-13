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

package org.gradle.api.internal.tasks.scala.incremental;

import org.gradle.api.internal.tasks.compile.CompilationFailedException;
import org.gradle.api.internal.tasks.compile.SimpleWorkResult;
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.WorkResult;
import org.gradle.api.tasks.scala.IncrementalCompileOptions;
import org.gradle.internal.UncheckedException;

import java.util.Collections;
import java.util.List;

public class SbtScalaCompiler implements org.gradle.api.internal.tasks.compile.Compiler<ScalaCompileSpec> {
    private static final Logger logger = Logging.getLogger(SbtScalaCompiler.class);

    public WorkResult execute(ScalaCompileSpec spec) {
        IncrementalCompileOptions options = spec.getIncrementalCompileOptions();

        // TODO: should reuse compiler if possible
        SbtIncrementalCompiler incremental;

        try {
            incremental = new SbtIncrementalCompiler(options.getScalaVersion(), options.getLibraryJar(),
                    options.getCompilerJar(), options.getSbtVersion(), options.getSbtInterfaceJar(),
                    options.getCompilerInterfaceSourceJar(), options.getResidentCompilerLimit(), logger);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }

        List<String> scalacOptions = Collections.emptyList(); // TODO
        List<String> javacOptions = Collections.emptyList(); // TODO

        try {
            incremental.compile(spec.getScalaClasspath(), spec.getSource(), spec.getDestinationDir(),
                    scalacOptions, javacOptions, options.getCacheFile(), options.getAllCacheFiles());
        } catch (xsbti.CompileFailed e) {
            throw new CompilationFailedException(e.getMessage());
        }

        return new SimpleWorkResult(true); // TODO
    }
}
