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

package org.gradle.performance

import org.gradle.performance.categories.BasicPerformanceTest
import org.junit.experimental.categories.Category
import spock.lang.Unroll

@Category(BasicPerformanceTest)
class DependencyReportPerformanceTest extends AbstractCrossVersionPerformanceTest {
    @Unroll("Project '#testProject' dependency report")
    def "dependency report"() {
        given:
        runner.testId = "dependencyReport $testProject"
        runner.testProject = testProject
        runner.tasksToRun = ['dependencyReport']
        runner.targetVersions = targetVersions

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject       | targetVersions
        "small"           | ['2.2.1', '2.8', 'last']
        "multi"           | ['2.8', 'last']
        "lotDependencies" | ['2.8', 'last']
    }
}
