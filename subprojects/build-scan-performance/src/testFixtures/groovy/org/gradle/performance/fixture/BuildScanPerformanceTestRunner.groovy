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

package org.gradle.performance.fixture

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.performance.results.CrossBuildPerformanceResults
import org.gradle.performance.results.DataReporter
import org.gradle.util.GradleVersion

class BuildScanPerformanceTestRunner extends CrossBuildPerformanceTestRunner {
    private final String pluginCommitSha

    public BuildScanPerformanceTestRunner(BuildExperimentRunner experimentRunner, DataReporter<CrossBuildPerformanceResults> dataReporter, String pluginCommitSha) {
        super(experimentRunner, dataReporter)
        this.pluginCommitSha = pluginCommitSha
    }

    @Override
    CrossBuildPerformanceResults newResult() {
        new CrossBuildPerformanceResults(
            testId: testId,
            testGroup: testGroup,
            jvm: Jvm.current().toString(),
            operatingSystem: OperatingSystem.current().toString(),
            versionUnderTest: GradleVersion.current().getVersion(),
            vcsBranch: Git.current().branchName,
            vcsCommits: [Git.current().commitId, pluginCommitSha],
            startTime: System.currentTimeMillis(),
            channel: determineChannel()
        )
    }

}
