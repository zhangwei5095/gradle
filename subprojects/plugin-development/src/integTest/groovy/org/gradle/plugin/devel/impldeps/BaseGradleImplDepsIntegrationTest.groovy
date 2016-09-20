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

package org.gradle.plugin.devel.impldeps

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class BaseGradleImplDepsIntegrationTest extends AbstractIntegrationSpec {

    public static final String API_JAR_GENERATION_OUTPUT_REGEX = "Generating JAR file 'gradle-api-(.*)\\.jar"
    public static final String TESTKIT_GENERATION_OUTPUT_REGEX = "Generating JAR file 'gradle-test-kit-(.*)\\.jar"

    def setup() {
        executer.requireGradleDistribution()
    }

    static String applyJavaPlugin() {
        """
            plugins {
                id 'java'
            }
        """
    }

    static String applyGroovyPlugin() {
        """
            plugins {
                id 'groovy'
            }
        """
    }

    static String jcenterRepository() {
        """
            repositories {
                jcenter()
            }
        """
    }

    static String gradleApiDependency() {
        """
            dependencies {
                compile gradleApi()
            }
        """
    }

    static String testKitDependency() {
        """
            dependencies {
                testCompile gradleTestKit()
            }
        """
    }

    static String junitDependency() {
        """
            dependencies {
                testCompile 'junit:junit:4.12'
            }
        """
    }

    static String spockDependency() {
        """
            dependencies {
                testCompile('org.spockframework:spock-core:1.0-groovy-2.4') {
                    exclude module: 'groovy-all'
                }
            }
        """
    }

    static String customGroovyPlugin() {
        """
            import org.gradle.api.Plugin
            import org.gradle.api.Project

            class MyPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    println 'Plugin applied!'
                }
            }
        """
    }

    static String testableGroovyProject() {
        StringBuilder buildFile = new StringBuilder()
        buildFile <<= applyGroovyPlugin()
        buildFile <<= jcenterRepository()
        buildFile <<= gradleApiDependency()
        buildFile <<= testKitDependency()
        buildFile <<= junitDependency()
        buildFile.toString()
    }

    static void assertSingleGenerationOutput(String output, String regex) {
        def pattern = /\b${regex}\b/
        def matcher = output =~ pattern
        assert matcher.count == 1
    }

    static void assertNoGenerationOutput(String output, String regex) {
        def pattern = /\b${regex}\b/
        def matcher = output =~ pattern
        assert matcher.count == 0
    }
}
