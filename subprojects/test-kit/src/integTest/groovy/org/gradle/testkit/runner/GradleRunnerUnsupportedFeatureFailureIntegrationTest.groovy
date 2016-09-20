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

package org.gradle.testkit.runner

import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.testkit.runner.fixtures.Debug
import org.gradle.testkit.runner.fixtures.NonCrossVersion
import org.gradle.testkit.runner.fixtures.PluginUnderTest
import org.gradle.testkit.runner.internal.feature.TestKitFeature
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

@NonCrossVersion
@Requires(TestPrecondition.FIX_TO_WORK_ON_JAVA9)
class GradleRunnerUnsupportedFeatureFailureIntegrationTest extends BaseGradleRunnerIntegrationTest {

    private static final ReleasedVersionDistributions RELEASED_VERSION_DISTRIBUTIONS = new ReleasedVersionDistributions()
    private final PluginUnderTest plugin = new PluginUnderTest(file("pluginDir"))

    def "fails informatively when trying to inspect executed tasks with unsupported gradle version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.CAPTURE_BUILD_RESULT_TASKS)
        def minSupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_TASKS.since.version

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        and:
        result.tasks

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."

        when:
        result.task(":foo")

        then:
        e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."

        when:
        result.tasks(SUCCESS)

        then:
        e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture executed tasks with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    @Debug
    def "fails informatively when trying to inspect build output in debug mode with unsupported gradle version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG)
        def minSupportedVersion = TestKitFeature.CAPTURE_BUILD_RESULT_OUTPUT_IN_DEBUG.since.version

        given:
        buildFile << helloWorldTask()

        when:
        def result = runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .build()

        and:
        result.output

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not capture build output in debug mode with the GradleRunner. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    def "fails informatively when trying to inject plugin classpath with unsupported gradle version"() {
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION)
        def minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since.version

        given:
        buildScript plugin.useDeclaration

        when:
        runner('helloWorld')
            .withGradleVersion(maxUnsupportedVersion)
            .withPluginClasspath([file("foo")])
            .build()

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support plugin classpath injection. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    def "fails informatively if trying to use conventional plugin classpath on version that does not support injection"() {
        given:
        def maxUnsupportedVersion = getMaxUnsupportedVersion(TestKitFeature.PLUGIN_CLASSPATH_INJECTION)
        def minSupportedVersion = TestKitFeature.PLUGIN_CLASSPATH_INJECTION.since.version

        buildScript plugin.useDeclaration

        when:
        plugin.build().exposeMetadata {
            runner('helloWorld')
                .withGradleVersion(maxUnsupportedVersion)
                .withPluginClasspath()
                .buildAndFail()
        }

        then:
        def e = thrown UnsupportedFeatureException
        e.message == "The version of Gradle you are using ($maxUnsupportedVersion) does not support plugin classpath injection. Support for this is available in Gradle $minSupportedVersion and all later versions."
    }

    static String getMaxUnsupportedVersion(TestKitFeature feature) {
        RELEASED_VERSION_DISTRIBUTIONS.getPrevious(feature.since).version.version
    }

}
