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

package org.gradle.smoketests

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class KotlinPluginSmokeTest extends AbstractSmokeTest {
    private kotlinVersion = '1.0.3'
    private androidPluginVersion = AndroidPluginsSmokeTest.ANDROID_PLUGIN_VERSION

    def 'kotlin plugin'() {
        given:
        useSample("kotlin-example")
        replaceVariablesInBuildFile(kotlinVersion: this.kotlinVersion)

        when:
        def result = runner('build').build()

        then:
        result.task(':compileKotlin').outcome == SUCCESS
    }

    def 'kotlin android plugin'() {
        given:
        useSample("android-kotlin-example")
        replaceVariablesInBuildFile(
            kotlinVersion: kotlinVersion,
            androidPluginVersion: androidPluginVersion)

        when:
        def build = runner('clean', 'testDebugUnitTestCoverage').build()

        then:
        build.task(':testDebugUnitTestCoverage').outcome == SUCCESS
    }
}
