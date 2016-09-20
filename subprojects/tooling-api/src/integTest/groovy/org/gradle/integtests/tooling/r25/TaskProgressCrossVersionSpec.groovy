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


package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.BuildException
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.task.TaskProgressEvent
import org.gradle.tooling.events.task.TaskSkippedResult
import org.gradle.tooling.model.gradle.BuildInvocations

class TaskProgressCrossVersionSpec extends ToolingApiSpecification {
    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=1.2 <2.5")
    def "ignores listeners when Gradle version does not generate task events"() {
        given:
        goodCode()

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({
                    throw new RuntimeException()
                }, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        noExceptionThrown()
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when requesting a model"() {
        given:
        goodCode()

        when: "asking for a model and specifying some task(s) to run first"
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.model(BuildInvocations).forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).get()
        }

        then: "task progress events must be forwarded to the attached listeners"
        !events.tasks.empty
        events.operations == events.tasks
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when launching a build"() {
        given:
        goodCode()

        when: "launching a build"
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then: "task progress events must be forwarded to the attached listeners"
        !events.tasks.empty
        events.operations == events.tasks
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive current task progress event even if one of multiple task listeners throws an exception"() {
        given:
        goodCode()

        when: "launching a build"
        List<TaskProgressEvent> resultsOfFirstListener = []
        List<TaskProgressEvent> resultsOfLastListener = []
        def stdout = new ByteArrayOutputStream()
        def failure = new IllegalStateException("Throwing an exception on purpose")
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener({ ProgressEvent event ->
                    resultsOfFirstListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    throw failure
                }, EnumSet.of(OperationType.TASK)).addProgressListener({ ProgressEvent event ->
                    resultsOfLastListener << (event as TaskProgressEvent)
                }, EnumSet.of(OperationType.TASK)).setStandardOutput(stdout).run()
        }

        then: "listener exception is wrapped"
        ListenerFailedException ex = thrown()
        ex.message.startsWith("Could not execute build using")
        ex.causes == [failure]

        and: "expected events received"
        resultsOfFirstListener.size() == 1
        resultsOfLastListener.size() == 1

        and: "build execution is successful"
        stdout.toString().contains("BUILD SUCCESSFUL")
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for successful tasks"() {
        given:
        goodCode()
        buildFile << """
            task disabled {
                enabled = false
            }
            classes.dependsOn disabled
        """

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('classes').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        // Some tasks; there may be others
        def compileJava = events.operation('Task :compileJava')
        compileJava.descriptor.name == ":compileJava"
        compileJava.descriptor.taskPath == ":compileJava"
        !compileJava.result.upToDate

        def processResources = events.operation('Task :processResources')
        processResources.descriptor.name == ":processResources"
        processResources.descriptor.taskPath == ":processResources"
        processResources.result.upToDate

        def disabled = events.operation('Task :disabled')
        disabled.descriptor.name == ":disabled"
        disabled.descriptor.taskPath == ":disabled"
        disabled.result instanceof TaskSkippedResult
        disabled.result.skipMessage == "SKIPPED"

        def classes = events.operation('Task :classes')
        classes.descriptor.name == ":classes"
        classes.descriptor.taskPath == ":classes"
        !classes.result.upToDate
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events for failed tasks"() {
        given:
        buildFile << """
            apply plugin: 'java'
            repositories { mavenCentral() }
            dependencies { testCompile 'junit:junit:4.12' }
            compileTestJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/test/java/MyTest.java") << """
            package example;
            public class MyTest {
                @org.junit.Test public void foo() throws Exception {
                     Thread.sleep(100);  // sleep for a moment to ensure test duration is > 0 (due to limited clock resolution)
                     throw new RuntimeException("broken", new RuntimeException("nope"));
                }
            }
        """

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('test').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        BuildException ex = thrown()
        ex.cause.cause.message =~ /Execution failed for task ':test'/

        def test = events.operation("Task :test")
        test.failed
        test.failures.size() == 1

        events.failed == [test]
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "receive task progress events when tasks are executed in parallel"() {
        given:
        if (!targetDist.toolingApiEventsInEmbeddedModeSupported) {
            toolingApi.requireDaemons()
        }
        buildFile << """
            @ParallelizableTask
            class ParTask extends DefaultTask {
                @TaskAction zzz() { Thread.sleep(1000) }
            }

            task para1(type:ParTask)
            task para2(type:ParTask)
            task parallelSleep(dependsOn:[para1,para2])
        """

        when:
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments("-Dorg.gradle.parallel.intra=true", '--parallel', '--max-workers=2').forTasks('parallelSleep').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then:
        events.tasks.size() == 3
        events.successful == events.tasks
    }

    @ToolingApiVersion(">=2.5")
    @TargetGradleVersion(">=2.5")
    def "task operations have a build operation as parent iff build listener is attached"() {
        given:
        goodCode()

        when: 'listening to task progress events and build operation listener is attached'
        def events = new ProgressEvents()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.GENERIC, OperationType.TASK)).run()
        }

        then: 'the parent of the task events is the root build operation'
        def runTasks = events.operation("Run tasks")
        events.tasks.every { it.descriptor.parent == runTasks.descriptor }

        when: 'listening to task progress events when no build operation listener is attached'
        events.clear()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().withArguments('--rerun-tasks').forTasks('assemble').addProgressListener(events, EnumSet.of(OperationType.TASK)).run()
        }

        then: 'the parent of the task events is null'
        events.tasks.every { it.descriptor.parent == null }
    }

    def goodCode() {
        buildFile << """
            apply plugin: 'java'
            compileJava.options.fork = true  // forked as 'Gradle Test Executor 1'
        """

        file("src/main/java/example/MyClass.java") << """
            package example;
            public class MyClass {
                public void foo() throws Exception {
                    Thread.sleep(100);
                }
            }
        """
    }
}
