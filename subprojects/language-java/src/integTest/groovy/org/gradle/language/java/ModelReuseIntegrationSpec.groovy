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

package org.gradle.language.java

import org.gradle.integtests.fixtures.daemon.DaemonIntegrationSpec
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.fixtures.TestJavaComponent

class ModelReuseIntegrationSpec extends DaemonIntegrationSpec {

    TestJvmComponent testComponent = new TestJavaComponent()

    def "builds jar"() {
        given:
        def projects = 5
        def tasks = 1000
        def iterations = 5

        1.upto(projects) {
            def name = "p$it"
            settingsFile << "include '$name'\n"
        }

        buildFile << """
            subprojects {
                apply plugin: 'jvm-component'
                apply plugin: '${testComponent.languageName}-lang'

                model {
                    components {
                        ${tasks}.times {
                          delegate."m\$it"(JvmLibrarySpec)
                        }
                    }
                }
            }
        """

        when:
        iterations.times {
            executer.withGradleOpts(
                    "-Dorg.gradle.model.reuse=true", // turns on model reuse
                    "-Dorg.gradle.model.placeholders=true" // turns on avoiding creating some tasks
            )
            run ":p1:m1Jar"
        }

        then:
        true
    }

}
