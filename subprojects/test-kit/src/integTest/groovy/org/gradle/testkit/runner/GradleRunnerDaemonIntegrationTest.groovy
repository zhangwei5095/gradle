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

import org.gradle.integtests.fixtures.executer.DaemonGradleExecuter
import org.gradle.test.fixtures.ConcurrentTestUtil
import org.gradle.testkit.runner.fixtures.CustomDaemonDirectory
import org.gradle.testkit.runner.fixtures.NoDebug
import org.junit.Rule
import spock.lang.Ignore

@Ignore("LH: Ignore until problem is fixed")
@NoDebug
class GradleRunnerDaemonIntegrationTest extends BaseGradleRunnerIntegrationTest {

    def setup() {
        requireIsolatedTestKitDir = true
    }

    @Rule
    final ConcurrentTestUtil concurrent = new ConcurrentTestUtil(20000)

    def "daemon process dedicated to test execution uses short idle timeout"() {
        when:
        runner().build()

        then:
        testKitDaemons().daemon.context.idleTimeout == 120000
    }

    def "daemon process dedicated to test execution is reused if one already exists"() {
        when:
        runner().build()

        then:
        def pid = testKitDaemons().daemon.with {
            assertIdle()
            context.pid
        }

        when:
        runner().build()

        then:
        testKitDaemons().daemon.context.pid == pid
    }

    @CustomDaemonDirectory
    def "user daemon process does not reuse existing daemon process intended for test execution even when using same gradle user home"() {
        given:
        def defaultDaemonDir = testKitDir.file("daemon")
        def nonTestKitDaemons = daemons(defaultDaemonDir, gradleVersion)

        when:
        runner().build()

        then:
        def testKitDaemon = testKitDaemons().daemon
        testKitDaemon.assertIdle()
        nonTestKitDaemons.visible.empty

        when:
        new DaemonGradleExecuter(buildContext.distribution(gradleVersion.version), testDirectoryProvider)
            .usingProjectDirectory(testDirectory)
            .withGradleUserHomeDir(testKitDir)
            .withDaemonBaseDir(defaultDaemonDir) // simulate default, our fixtures deviate from the default
            .run()

        then:
        def userDaemon = nonTestKitDaemons.daemon
        userDaemon.assertIdle()
        userDaemon.context.pid != testKitDaemon.context.pid

        cleanup:
        userDaemon?.kill()
    }

    def "executing a build with a -g option does not affect daemon mechanics"() {
        when:
        runner("-g", file("custom-gradle-user-home").absolutePath).build()

        then:
        testKitDaemons().daemon.assertIdle()
    }

    def "runners executed concurrently can share the same Gradle user home directory"() {
        when:
        3.times {
            concurrent.start {
                runner().build()
            }
        }

        then:
        concurrent.finished()
    }

}
