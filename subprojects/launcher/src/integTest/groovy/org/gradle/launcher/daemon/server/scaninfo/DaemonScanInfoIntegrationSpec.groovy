/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.launcher.daemon.server.scaninfo

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import spock.lang.Unroll

class DaemonScanInfoIntegrationSpec extends DaemonIntegrationSpec {

    def "should capture basic data via the service registry"() {
        given:
        buildFile << """
        ${imports()}
        ${captureAndAssert()}
        """

        expect:
        buildSucceeds()

    }

    def "should capture basic data via when the daemon is running in continuous mode"() {
        given:
        buildFile << """
        ${imports()}
        ${captureAndAssert()}
        """

        expect:
        executer.withArguments('help', '--continuous', '-i').run().getExecutedTasks().contains(':help')
    }

    def "should capture basic data when a foreground daemon runs multiple builds"() {
        given:
        buildFile << """
        ${imports()}

        ${captureTask("capture1", 1, 1)}
        ${captureTask("capture2", 2, 1)}
        """

        when:
        def daemon = startAForegroundDaemon()

        List<ExecutionResult> captureResults = []
        captureResults << executer.withTasks('capture1').run()
        captureResults << executer.withTasks('capture2').run()

        then:
        captureResults[0].getExecutedTasks().contains(':capture1')
        captureResults[1].getExecutedTasks().contains(':capture2')

        cleanup:
        daemon?.abort()
    }

    @Unroll
    def "a daemon expiration listener receives expiration reasons continuous:#continuous"() {
        given:
        buildFile << """
           ${imports()}
           ${registerTestExpirationStrategy(50)}
           ${registerExpirationListener()}
           ${delayTask(200)}
        """

        when:
        result = executer.withArguments(continuous ? ['delay', '--continuous'] : ['delay']).run()

        then:
        output.findAll('onExpirationEvent fired with: expiring daemon with TestExpirationStrategy').size() == 1

        where:
        continuous << [true, false]
    }

    def "daemon expiration listener is implicitly for the current build only"() {
        given:
        buildFile << """
           ${imports()}
               ${registerTestExpirationStrategy(5_000)}
           ${registerExpirationListener()}
           task foo
        """

        when:
        def delayResult = executer.withArguments('foo').run()

        then:
        !delayResult.output.contains('onExpirationEvent fired with: expiring daemon with TestExpirationStrategy')

        when:
        buildFile.text = """
           ${imports()}
           ${delayTask(7_000)}
        """
        delayResult = executer.withArguments('delay').run()

        then:
        !delayResult.output.contains('onExpirationEvent fired with: expiring daemon with TestExpirationStrategy')
    }

    def "a daemon expiration listener receives expiration reasons when daemons run in the foreground"() {
        given:
        buildFile << """
           ${imports()}
           ${registerTestExpirationStrategy(50)}
           ${registerExpirationListener()}
           ${delayTask(200)}
        """

        when:
        startAForegroundDaemon()
        def delayResult = executer.withTasks('delay').run()

        then:
        delayResult.assertOutputContains("onExpirationEvent fired with: expiring daemon with TestExpirationStrategy")

    }

    static String captureTask(String name, int buildCount, int daemonCount) {
        """
    task $name {
        doLast {
            DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
            ${assertInfo(buildCount, daemonCount)}
        }
    }
    """
    }

    static String captureAndAssert() {
        return """
           DaemonScanInfo info = project.getServices().get(DaemonScanInfo)
           ${assertInfo(1, 1)}
           """
    }

    static String assertInfo(int numberOfBuilds, int numDaemons) {
        return """
           assert info.getNumberOfBuilds() == ${numberOfBuilds}
           assert info.getNumberOfRunningDaemons() == ${numDaemons}
           assert info.getIdleTimeout() == 120000
           assert info.getStartedAt() <= System.currentTimeMillis()
        """
    }

    static String delayTask(int sleep) {
        """
        task delay {
            doFirst{
             sleep(${sleep})
            }
        }
        """
    }

    static String registerExpirationListener() {
        """
        def daemonScanInfo = project.getServices().get(DaemonScanInfo)

        daemonScanInfo.notifyOnUnhealthy(new Action<String>() {
            @Override
            public void execute(String s) {
                  println "onExpirationEvent fired with: \${s}"
            }
        })
        """
    }

    static String registerTestExpirationStrategy(int frequency) {
        """
        class TestExpirationStrategy implements DaemonExpirationStrategy {
            Project project

            public TestExpirationStrategy(Project project){
                this.project = project
            }

            @Override
            public DaemonExpirationResult checkExpiration() {
                DaemonContext dc = null
                try {
                    dc = project.getServices().get(DaemonContext)
                } catch (Exception e) {
                    // ignore
                }
                return new DaemonExpirationResult(DaemonExpirationStatus.GRACEFUL_EXPIRE, "expiring daemon with TestExpirationStrategy uuid: \${dc?.getUid()}")
            }
        }

        def daemon =  project.getServices().get(Daemon)
        daemon.scheduleExpirationChecks(new AllDaemonExpirationStrategy([new TestExpirationStrategy(project)]), $frequency)
        """
    }

    static String imports() {
        """
        import org.gradle.launcher.daemon.server.scaninfo.DaemonScanInfo
        import org.gradle.launcher.daemon.context.*
        import org.gradle.launcher.daemon.server.*
        import org.gradle.launcher.daemon.server.expiry.*
        """
    }

}
