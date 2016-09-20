/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.integtests.fixtures.executer.ExecutionFailure
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleVersions
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionFailure
import org.gradle.integtests.fixtures.executer.OutputScrapingExecutionResult
import org.gradle.integtests.fixtures.executer.UnexpectedBuildFailure
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.junit.Rule
import spock.lang.Timeout

@Timeout(180)
@TargetGradleVersion(GradleVersions.SUPPORTS_CONTINUOUS)
@ToolingApiVersion(ToolingApiVersions.SUPPORTS_CANCELLATION)
abstract class ContinuousBuildToolingApiSpecification extends ToolingApiSpecification {

    public static final String WAITING_MESSAGE = "Waiting for changes to input files of tasks..."
    private static final boolean OS_IS_WINDOWS = OperatingSystem.current().isWindows()

    TestOutputStream stderr = new TestOutputStream()
    TestOutputStream stdout = new TestOutputStream()

    ExecutionResult result
    ExecutionFailure failure

    int buildTimeout = 20

    @Rule
    GradleBuildCancellation cancellationTokenSource
    TestResultHandler buildResult
    TestFile sourceDir

    ProjectConnection projectConnection


    void setup() {
        buildFile.text = "apply plugin: 'java'\n"
        sourceDir = file("src/main/java")
    }

    @Override
    <T> T withConnection(@DelegatesTo(ProjectConnection) @ClosureParams(value = SimpleType, options = ["org.gradle.tooling.ProjectConnection"]) Closure<T> cl) {
        super.withConnection {
            projectConnection = it
            try {
                it.with(cl)
            } finally {
                projectConnection = null
            }
        }
    }

    public <T> T runBuild(List<String> tasks = ["build"], Closure<T> underBuild) {
        if (projectConnection) {
            buildResult = new TestResultHandler()

            cancellationTokenSource.withCancellation { CancellationToken token ->
                // this is here to ensure that the lastModified() timestamps actually change in between builds.
                // if the build is very fast, the timestamp of the file will not change and the JDK file watch service won't see the change.
                def initScript = file("init.gradle")
                initScript.text = """
                    |import java.lang.management.ManagementFactory

                    |gradle.rootProject {
                    |    try {
                    |        gradle.rootProject.buildDir.mkdir()
                    |        new File(gradle.rootProject.buildDir, "build.pid").text = ManagementFactory.getRuntimeMXBean().getName().split("@")[0]
                    |    } catch (Throwable t) {
                    |    }
                    |}

                    |def startAt = System.nanoTime()
                    |gradle.buildFinished {
                    |    long sinceStart = (System.nanoTime() - startAt) / 1000000L
                    |    if (sinceStart > 0 && sinceStart < 2000) {
                    |      sleep(2000 - sinceStart)
                    |    }
                    |}
                """.stripMargin()

                BuildLauncher launcher = projectConnection.newBuild()
                    .withArguments("--continuous", "-I", initScript.absolutePath)
                    .forTasks(tasks as String[])
                    .withCancellationToken(token)

                if (toolingApi.isEmbedded()) {
                    launcher
                        .setStandardOutput(stdout)
                        .setStandardError(stderr)
                } else {
                    launcher
                        .setStandardOutput(new TeeOutputStream(stdout, System.out))
                        .setStandardError(new TeeOutputStream(stderr, System.err))
                }

                customizeLauncher(launcher)

                launcher.run(buildResult)
                T t = underBuild.call()
                cancellationTokenSource.cancel()
                buildResult.finished(buildTimeout)
                t
            }
        } else {
            withConnection { runBuild(tasks, underBuild) }
        }
    }

    void customizeLauncher(BuildLauncher launcher) {

    }

    ExecutionResult succeeds() {
        waitForBuild()
        if (result instanceof ExecutionFailure) {
            throw new UnexpectedBuildFailure("build was expected to succeed but failed")
        }
        failure = null
        result
    }

    ExecutionFailure fails() {
        waitForBuild()
        if (!(result instanceof ExecutionFailure)) {
            throw new UnexpectedBuildFailure("build was expected to fail but succeeded")
        }
        failure = result as ExecutionFailure
        failure
    }

    private void waitForBuild() {
        boolean success
        long pollingStartMillis = System.currentTimeMillis()
        long pollingStartNanos = System.nanoTime()
        try {
            ConcurrentTestUtil.poll(buildTimeout, 0.5) {
                def out = stdout.toString()
                assert out.contains(WAITING_MESSAGE)
            }
            success = true
        } finally {
            if (!success) {
                println "Polling lasted ${System.currentTimeMillis() - pollingStartMillis} ms measured with walltime clock"
                println "Polling lasted ${(long) ((System.nanoTime() - pollingStartNanos) / 1000000L)} ms measured with monotonic clock"
                requestJstackForBuildProcess()
            }
        }

        def out = stdout.toString()
        stdout.reset()
        def err = stderr.toString()
        stderr.reset()

        result = out.contains("BUILD SUCCESSFUL") ? new OutputScrapingExecutionResult(out, err) : new OutputScrapingExecutionFailure(out, err)
    }

    def requestJstackForBuildProcess() {
        def pidFile = file("build/build.pid")
        if (pidFile.exists()) {
            def pid = pidFile.text
            def jdkBinDir = new File(System.getProperty("java.home"), "../bin").canonicalFile
            if (jdkBinDir.isDirectory()) {
                println "--------------------------------------------------"
                def jstackOutput = ["${jdkBinDir}/jstack", pid].execute().text
                println jstackOutput
                println "--------------------------------------------------"
            }
        }
    }

    protected List<String> getExecutedTasks() {
        assertHasResult()
        result.executedTasks
    }

    private assertHasResult() {
        assert result != null: "result is null, you haven't run succeeds()"
    }

    protected Set<String> getSkippedTasks() {
        assertHasResult()
        result.skippedTasks
    }

    protected List<String> getNonSkippedTasks() {
        executedTasks - skippedTasks
    }

    protected void executedAndNotSkipped(String... tasks) {
        tasks.each {
            assert it in executedTasks
            assert !skippedTasks.contains(it)
        }
    }

    boolean cancel() {
        cancellationTokenSource.cancel()
        true
    }

    void waitBeforeModification(File file) {
        long waitMillis = 100L
        if(OS_IS_WINDOWS && file.exists()) {
            // ensure that file modification time changes on windows
            long fileAge = System.currentTimeMillis() - file.lastModified()
            if (fileAge > 0L && fileAge < 900L) {
                waitMillis = 1000L - fileAge
            }
        }
        sleep(waitMillis)
    }

}
