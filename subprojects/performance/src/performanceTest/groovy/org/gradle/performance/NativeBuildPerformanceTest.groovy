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

package org.gradle.performance

import org.gradle.performance.categories.NativePerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category([NativePerformanceTest])
class NativeBuildPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll('Project #type native build')
    def "build" () {
        given:
        runner.testId = "native build ${type}"
        runner.testProject = "${type}Native"
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = [fastestVersion]
        runner.useDaemon = true

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        type           | fastestVersion
        // TODO: Restore 'last' when sufficent performance gains are made.
        "small"        | '3.1-20160818000032+0000'
        "medium"       | '2.14.1'
        "big"          | '2.14.1'
        "multi"        | '2.14.1'
    }

    def "Many projects native build" () {
        given:
        runner.testId = "native build many projects"
        runner.testProject = "manyProjectsNative"
        runner.tasksToRun = ["clean", "assemble"]
        runner.targetVersions = ['2.14.1']
        runner.useDaemon = true

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()
    }
}
