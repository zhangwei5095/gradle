/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests.samples

import com.google.common.collect.ArrayListMultimap
import groovy.io.PlatformLineWriter
import org.apache.tools.ant.taskdefs.Delete
import org.gradle.api.JavaVersion
import org.gradle.api.Transformer
import org.gradle.api.reporting.components.JvmComponentReportOutputFormatter
import org.gradle.api.reporting.components.NativeComponentReportOutputFormatter
import org.gradle.api.reporting.components.PlayComponentReportOutputFormatter
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.internal.SystemProperties
import org.gradle.internal.os.OperatingSystem
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.util.AntUtil
import org.gradle.util.TextUtil
import org.junit.Assert
import org.junit.runner.Description
import org.junit.runner.Runner
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunNotifier

import java.util.regex.Pattern

class UserGuideSamplesRunner extends Runner {
    private static final String NL = SystemProperties.instance.lineSeparator

    private Class<?> testClass
    private Description description
    private Map<Description, SampleRun> samples
    private TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    private GradleDistribution dist = new UnderDevelopmentGradleDistribution()
    private IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private GradleExecuter executer = new GradleContextualExecuter(dist, temporaryFolder)
    private Pattern dirFilter
    private List excludes
    private TestFile baseExecutionDir = temporaryFolder.testDirectory

    UserGuideSamplesRunner(Class<?> testClass) {
        this.testClass = testClass
        this.description = Description.createSuiteDescription(testClass)
        this.dirFilter = initDirFilterPattern()
        this.excludes = initExcludes()
        samples = new LinkedHashMap()
        for (sample in getScriptsForSamples(buildContext.userGuideInfoDir)) {
            if (shouldInclude(sample)) {
                Description childDescription = Description.createTestDescription(testClass, sample.id)
                description.addChild(childDescription)
                samples.put(childDescription, sample)

                println "Sample $sample.id dir: $sample.subDir"
                sample.runs.each { println "    args: $it.args expect: $it.outputFile" }
            }
        }

        // have to copy everything upfront because build scripts of some samples refer to files of other samples
        buildContext.samplesDir.copyTo(baseExecutionDir)
    }

    private Pattern initDirFilterPattern() {
        String filter = System.properties["org.gradle.userguide.samples.filter"]
        filter ? Pattern.compile(filter) : null
    }

    private List initExcludes() {
        List excludes = []
        String excludesString = System.properties["org.gradle.userguide.samples.exclude"] ?: "";
        excludesString.split(',').each {
            excludes.add it
        }
        return excludes
    }

    Description getDescription() {
        description
    }

    private boolean shouldInclude(SampleRun run) {
        if (excludes.contains(run.id)) {
            return false
        }
        dirFilter ? run.subDir ==~ dirFilter : true
    }

    void run(RunNotifier notifier) {
        for (childDescription in description.children) {
            notifier.fireTestStarted(childDescription)
            def sampleRun = samples.get(childDescription)
            try {
                cleanup(sampleRun)
                for (run in sampleRun.runs) {
                    if (run.brokenForParallel && GradleContextualExecuter.parallel) {
                        continue
                    }
                    runSample(run)
                }
            } catch (Throwable t) {
                notifier.fireTestFailure(new Failure(childDescription, t))
            }
            notifier.fireTestFinished(childDescription)
        }
        try {
            temporaryFolder.testDirectory.deleteDir()
        } catch (IOException e) {
            //ignore
        }
    }

    private void cleanup(SampleRun run) {
        run.runs.each { singleRun ->
            // Clean up previous runs in the same subdir
            File rootProjectDir = temporaryFolder.testDirectory.file(singleRun.subDir)
            if (rootProjectDir.exists()) {
                def delete = new Delete()
                delete.dir = rootProjectDir
                delete.includes = "**/.gradle/** **/build/**"
                AntUtil.execute(delete)
            }
        }
    }

    private void runSample(GradleRun run) {
        try {
            println("Test Id: $run.id, dir: $run.subDir, execution dir: $run.executionDir args: $run.args")
            def m2 = new M2Installation(temporaryFolder)
            m2.execute(executer)
            executer.noExtraLogging()
                .inDirectory(run.executionDir)
                .withArguments(run.args as String[])
                .withEnvironmentVars(run.envs)

            if (!GradleContextualExecuter.longLivingProcess) {
                //suppress daemon usage suggestions
                executer.withArgument("--no-daemon")
            }

            if (run.allowDeprecation) {
                executer.expectDeprecationWarning()
            }

            def result = run.expectFailure ? executer.runWithFailure() : executer.run()
            if (run.outputFile) {
                def expectedResult = buildContext.userGuideOutputDir.file(run.outputFile).text
                if (run.outputFormatter) {
                    expectedResult = run.outputFormatter.transform(expectedResult)
                }
                expectedResult = replaceWithPlatformNewLines(expectedResult)
                expectedResult = replaceWithRealSamplesDir(expectedResult)
                try {
                    result.assertOutputEquals(expectedResult, run.ignoreExtraLines, run.ignoreLineOrder)
                } catch (AssertionError e) {
                    println 'Expected Result:'
                    println expectedResult
                    println 'Actual Result:'
                    println result.output
                    println '---'
                    throw e
                }
            }

            run.files.each { path ->
                println "  checking file '$path' exists"
                def file = new File(run.executionDir, path).canonicalFile
                Assert.assertTrue("Expected file '$file' does not exist.", file.exists())
                Assert.assertTrue("Expected file '$file' is not a file.", file.isFile())
            }
            run.dirs.each { path ->
                println "  checking directory '$path' exists"
                def file = new File(run.executionDir, path).canonicalFile
                Assert.assertTrue("Expected directory '$file' does not exist.", file.exists())
                Assert.assertTrue("Expected directory '$file' is not a directory.", file.isDirectory())
            }
        } catch (Throwable e) {
            throw new AssertionError("Integration test for sample '$run.id' in dir '$run.subDir' with args $run.args failed:${NL}$e.message").initCause(e)
        }
    }

    private String replaceWithPlatformNewLines(String text) {
        def stringWriter = new StringWriter()
        new PlatformLineWriter(stringWriter).withWriter { it.write(text) }
        stringWriter.toString()
    }

    private String replaceWithRealSamplesDir(String text) {
        def normalisedSamplesDir = TextUtil.normaliseFileSeparators(baseExecutionDir.absolutePath)
        return text.replaceAll(Pattern.quote('/home/user/gradle/samples'), normalisedSamplesDir)
    }

    private Collection<SampleRun> getScriptsForSamples(File userguideInfoDir) {
        def samplesXml = new File(userguideInfoDir, 'samples.xml')
        assertSamplesGenerated(samplesXml.exists())
        def samples = new XmlParser().parse(samplesXml)
        def samplesByDir = ArrayListMultimap.<String, GradleRun> create()

        def children = samples.children()
        assertSamplesGenerated(!children.isEmpty())

        children.eachWithIndex { Node sample, int index ->
            def id = sample.'@id'
            def dir = sample.'@dir'
            def args = sample.'@args'
            def outputFile = sample.'@outputFile'
            boolean ignoreExtraLines = Boolean.valueOf(sample.'@ignoreExtraLines')
            boolean ignoreLineOrder = Boolean.valueOf(sample.'@ignoreLineOrder')
            boolean expectFailure = Boolean.valueOf(sample.'@expectFailure')

            def run = new GradleRun(id: id)
            run.subDir = dir
            run.args = args ? args.split('\\s+') as List : []
            run.outputFile = outputFile
            run.ignoreExtraLines = ignoreExtraLines
            run.ignoreLineOrder = ignoreLineOrder
            run.expectFailure = expectFailure
            run.index = index

            sample.file.each { file -> run.files << file.'@path' }
            sample.dir.each { file -> run.dirs << file.'@path' }

            samplesByDir.put(dir, run)
        }

        // Some custom values
        samplesByDir.get('userguide/tutorial/properties').each { it.envs['ORG_GRADLE_PROJECT_envProjectProp'] = 'envPropertyValue' }
        samplesByDir.get('userguide/buildlifecycle/taskExecutionEvents')*.expectFailure = true
        samplesByDir.get('userguide/buildlifecycle/buildProjectEvaluateEvents')*.expectFailure = true
        samplesByDir.get('userguide/tasks/finalizersWithFailure')*.expectFailure = true
        samplesByDir.get('userguide/multiproject/dependencies/firstMessages/messages')*.brokenForParallel = true
        samplesByDir.get('userguide/multiproject/dependencies/messagesHack/messages')*.brokenForParallel = true
        samplesByDir.get('userguide/tutorial/helloShortcut')*.allowDeprecation = true
        samplesByDir.get('webApplication/customized')*.allowDeprecation = true
        samplesByDir.get('webApplication/quickstart')*.allowDeprecation = true

        def java6CrossCompilation = ['java', 'groovy', 'scala'].collectMany {
            samplesByDir.get(it + '/crossCompilation')
        }

        def java6jdk = AvailableJavaHomes.getJdk(JavaVersion.VERSION_1_6)
        if (!java6jdk || OperatingSystem.current().isWindows()) {
            java6CrossCompilation*.expectFailure = true
        } else {
            java6CrossCompilation*.args = ['build', "-Pjava6Home=${java6jdk.javaHome.absolutePath}"]
        }

        Map<String, SampleRun> samplesById = new TreeMap<String, SampleRun>()

        // Remove duplicates for a given directory.
        samplesByDir.asMap().values().collect { List<GradleRun> dirSamples ->
            def runs = dirSamples.findAll { it.mustRun }
            if (!runs) {
                // No samples in this dir have any args, so just run gradle tasks in the dir
                def sample = dirSamples[0]
                sample.args = ['tasks']
                sample
            } else {
                return runs
            }
        }.flatten().each { GradleRun run ->
            // Collect up into sample runs
            def sampleRun = samplesById[run.id]
            if (!sampleRun) {
                sampleRun = new SampleRun(id: run.id, subDir: run.subDir)
                samplesById[run.id] = sampleRun
            }
            sampleRun.runs << run
        }

        samplesById.nativeComponentReport.runs.each { it.outputFormatter = new NativeComponentReportOutputFormatter() }
        samplesById.playComponentReport.runs.each { it.outputFormatter = new PlayComponentReportOutputFormatter() }
        samplesById.javaLibraryComponentReport.runs.each { it.outputFormatter = new JvmComponentReportOutputFormatter() }

        if ("true".equals(System.getProperty("org.gradle.integtest.unknownos"))) {
            // Ignore for now
            samplesById.remove('completeCUnitExample')
        }

        samplesById.each { id, sample ->
            sample.runs = sample.runs.sort { it.index }
        }
        return samplesById.values()
    }

    private void assertSamplesGenerated(boolean assertion) {
        assert assertion: """Couldn't find any samples. Most likely, samples.xml was not generated.
Please run 'gradle docs:userguideDocbook' first"""
    }

    private class GradleRun {
        String id
        List args = []
        String subDir
        Map envs = [:]
        String outputFile
        Transformer<String, String> outputFormatter
        boolean expectFailure
        boolean ignoreExtraLines
        boolean ignoreLineOrder
        boolean brokenForParallel
        boolean allowDeprecation
        List files = []
        List dirs = []
        int index

        boolean getMustRun() {
            return args || files || dirs
        }

        File getExecutionDir() {
            new File(baseExecutionDir, subDir)
        }
    }

    private class SampleRun {
        String id
        String subDir
        List<GradleRun> runs = []
    }
}


