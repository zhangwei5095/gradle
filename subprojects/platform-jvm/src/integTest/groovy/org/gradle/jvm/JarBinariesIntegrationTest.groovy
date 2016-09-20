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

package org.gradle.jvm

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.hamcrest.Matchers

class JarBinariesIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'jvm-component'
            }
        """
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "assemble task constructs all buildable binaries" () {
        buildFile << """
            model {
                components {
                    myJvmLib1(JvmLibrarySpec) {
                        targetPlatform "java9"
                    }
                    myJvmLib2(JvmLibrarySpec)
                }
            }
        """

        when:
        succeeds "assemble"

        then:
        executedAndNotSkipped(":myJvmLib2Jar")
        notExecuted(":myJvmLib1Jar")

        and:
        file("build/jars/myJvmLib2/jar/myJvmLib2.jar").assertExists()
        file("build/jars/myJvmLib1/jar/myJvmLib1.jar").assertDoesNotExist()
    }

    @Requires(TestPrecondition.JDK8_OR_EARLIER)
    def "assemble task produces sensible error when there are no buildable binaries" () {
        buildFile << """
            model {
                components {
                    myJvmLib1(JvmLibrarySpec) {
                        targetPlatform "java9"
                    }
                    myJvmLib2(JvmLibrarySpec) {
                        targetPlatform "java9"
                    }
                    myJvmLib3(JvmLibrarySpec) {
                        binaries.all { buildable = false }
                    }
                }
            }
        """

        when:
        fails "assemble"

        then:
        failureDescriptionContains("Execution failed for task ':assemble'.")
        failure.assertThatCause(Matchers.<String>allOf(
                Matchers.startsWith("No buildable binaries found:"),
                Matchers.containsString("Jar 'myJvmLib1:jar': Could not target platform: 'Java SE 9' using tool chain:"),
                Matchers.containsString("Jar 'myJvmLib2:jar': Could not target platform: 'Java SE 9' using tool chain:"),
                Matchers.containsString("Jar 'myJvmLib3:jar': Disabled by user")
        ))
    }

    def "model report should display configured components and binaries"() {
        given:
        buildFile << """
            plugins {
                id 'java-lang'
            }
            model {
                components {
                    jvmLibrary(JvmLibrarySpec) {
                        sources {
                            other(JavaSourceSet)
                        }
                        binaries {
                            jar {
                                sources {
                                    binarySources(JavaSourceSet)
                                }
                            }
                        }
                    }
                }
            }
"""
        when:
        succeeds "model"

        then:
        ModelReportOutput.from(output).hasNodeStructure {
            components {
                jvmLibrary {
                    binaries {
                        jar(type: "org.gradle.jvm.JarBinarySpec") {
                            sources {
                                binarySources(type: "org.gradle.language.java.JavaSourceSet")
                            }
                            tasks()
                        }
                    }
                    sources {
                        java(type: "org.gradle.language.java.JavaSourceSet")
                        other(type: "org.gradle.language.java.JavaSourceSet")
                        resources(type: "org.gradle.language.jvm.JvmResourceSet")
                    }
                }
            }
        }
    }

}
