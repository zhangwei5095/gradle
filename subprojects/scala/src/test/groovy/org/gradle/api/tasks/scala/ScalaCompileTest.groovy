/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.file.FileTreeInternal
import org.gradle.api.internal.tasks.scala.ScalaJavaJointCompileSpec
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.AbstractCompileTest
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.language.scala.tasks.BaseScalaCompileOptions
import org.gradle.util.GFileUtils

public class ScalaCompileTest extends AbstractCompileTest {
    private ScalaCompile scalaCompile

    private scalaCompiler = Mock(Compiler)
    private scalaClasspath = Mock(FileTreeInternal)

    @Override
    public AbstractCompile getCompile() {
        return scalaCompile
    }

    @Override
    public ConventionTask getTask() {
        return scalaCompile
    }

    def setup() {
        scalaCompile = createTask(ScalaCompile)
        scalaCompile.setCompiler(scalaCompiler)

        GFileUtils.touch(new File(srcDir, "incl/file.scala"))
        GFileUtils.touch(new File(srcDir, "incl/file.java"))
    }

    def "execute doing work"() {
        given:
        setUpMocksAndAttributes(scalaCompile)
        scalaClasspath.isEmpty() >> false

        when:
        scalaCompile.execute()

        then:
        1 * scalaCompiler.execute(_ as ScalaJavaJointCompileSpec)
    }

    def "moans if scalaClasspath is empty"() {
        given:
        setUpMocksAndAttributes(scalaCompile)
        scalaClasspath.isEmpty() >> true

        when:
        scalaCompile.execute()

        then:
        TaskExecutionException e = thrown()
        e.cause instanceof InvalidUserDataException
        e.cause.message.contains("'testTask.scalaClasspath' must not be empty")
    }

    protected void setUpMocksAndAttributes(final ScalaCompile compile) {
        super.setUpMocksAndAttributes(compile)
        compile.setScalaClasspath(scalaClasspath)
        compile.setZincClasspath(compile.getClasspath())
        BaseScalaCompileOptions options = compile.getScalaCompileOptions()
        options.getIncrementalOptions().setAnalysisFile(new File("analysisFile"))
    }
}
