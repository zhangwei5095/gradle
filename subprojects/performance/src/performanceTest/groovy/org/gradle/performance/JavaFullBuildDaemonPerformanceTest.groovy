/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.categories.JavaPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([JavaPerformanceTest])
class JavaFullBuildDaemonPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("full build Java build - #testProject")
    def "full build Java build"() {
        given:
        runner.testId = "full build Java build $testProject (daemon)"
        runner.previousTestIds = ["daemon clean build $testProject"]
        runner.testProject = testProject
        runner.useDaemon = true
        runner.tasksToRun = ['clean', 'build']
        runner.targetVersions = targetVersions
        runner.gradleOpts = ["-Xms1g", "-Xmx1g"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject | targetVersions
        "small"     | ['2.11', 'last']
        // TODO: Restore 'last' when sufficient performance gains are made.
        "multi"     | ['3.1-20160818000032+0000']
    }
}
