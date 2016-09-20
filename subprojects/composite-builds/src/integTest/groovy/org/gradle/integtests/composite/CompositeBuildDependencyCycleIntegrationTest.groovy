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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.build.BuildTestFile
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.MavenModule
/**
 * Tests for resolving dependency cycles in a composite build.
 */
class CompositeBuildDependencyCycleIntegrationTest extends AbstractCompositeBuildIntegrationTest {
    BuildTestFile buildB
    BuildTestFile buildC
    ResolveTestFixture resolve
    MavenModule publishedModuleB
    List arguments = []

    def setup() {
        publishedModuleB = mavenRepo.module("org.test", "buildB", "1.0").publish()
        resolve = new ResolveTestFixture(buildA.buildFile)

        buildA.buildFile << """
            task resolveArtifacts(type: Copy) {
                from configurations.compile
                into 'libs'
            }
"""

        buildB = multiProjectBuild("buildB", ['b1', 'b2']) {
            buildFile << """
                allprojects {
                    apply plugin: 'java'
                }
"""
        }
        includedBuilds << buildB

        buildC = singleProjectBuild("buildC") {
            buildFile << """
                apply plugin: 'java'
"""
        }
        includedBuilds << buildC
    }

    def "direct dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        dependency buildC, "org.test:buildB:1.0"

        when:
        resolve.withoutBuildingArtifacts()
        resolveSucceeds(":checkDeps")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:buildC:1.0", "project :buildC:", "org.test:buildC:1.0") {
                    compositeSubstitute()
                    edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                        compositeSubstitute()
                    }
                }
            }
        }

        when:
        resolveFails(":resolveArtifacts")

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Failed to build artifacts for build 'buildC'")
            .assertHasCause("Could not download buildB.jar (project :buildB:)")
            .assertHasCause("Included build dependency cycle: build 'buildB' -> build 'buildC' -> build 'buildB'")
    }

    def "indirect dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        dependency buildC, "org.test:buildD:1.0"

        def buildD = singleProjectBuild("buildD") {
            buildFile << """
                apply plugin: 'java'
                dependencies {
                    compile "org.test:buildB:1.0"
                }
"""
        }
        includedBuilds << buildD

        when:
        resolve.withoutBuildingArtifacts()
        resolveSucceeds(":checkDeps")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:buildC:1.0", "project :buildC:", "org.test:buildC:1.0") {
                    compositeSubstitute()
                    edge("org.test:buildD:1.0", "project :buildD:", "org.test:buildD:1.0") {
                        compositeSubstitute()
                        edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                            compositeSubstitute()
                        }
                    }
                }
            }
        }

        when:
        resolveFails(":resolveArtifacts")

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Failed to build artifacts for build 'buildC'")
            .assertHasCause("Failed to build artifacts for build 'buildD'")
            .assertHasCause("Could not download buildB.jar (project :buildB:)")
            .assertHasCause("Included build dependency cycle: build 'buildB' -> build 'buildC' -> build 'buildD' -> build 'buildB'")
    }

    def "compile-only dependency cycle between included builds"() {
        given:
        dependency "org.test:buildB:1.0"
        dependency buildB, "org.test:buildC:1.0"
        buildC.buildFile << """
            apply plugin: 'java'
            dependencies {
                compileOnly "org.test:buildB:1.0"
            }
"""

        when:
        resolve.withoutBuildingArtifacts()
        resolveSucceeds(":checkDeps")

        then: // No cycle when building dependency graph
        checkGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:buildC:1.0", "project :buildC:", "org.test:buildC:1.0") {
                    compositeSubstitute()
                }
            }
        }

        when:
        resolveFails(":resolveArtifacts")

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Failed to build artifacts for build 'buildC'")
            .assertHasCause("Could not download buildB.jar (project :buildB:)")
            .assertHasCause("Included build dependency cycle: build 'buildB' -> build 'buildC' -> build 'buildB'")
    }

    def "dependency cycle between subprojects in an included multiproject build"() {
        given:
        dependency "org.test:buildB:1.0"

        buildB.buildFile << """
            dependencies {
                compile "org.test:b1:1.0"
            }
            project(':b1') {
                dependencies {
                    compile "org.test:b2:1.0"
                }
            }
            project(':b2') {
                dependencies {
                    compile "org.test:b1:1.0"
                }
            }
"""

        when:
        resolve.withoutBuildingArtifacts()
        resolveSucceeds(":checkDeps")

        then:
        checkGraph {
            edge("org.test:buildB:1.0", "project :buildB:", "org.test:buildB:1.0") {
                compositeSubstitute()
                edge("org.test:b1:1.0", "project :buildB:b1", "org.test:b1:1.0") {
                    compositeSubstitute()
                    edge("org.test:b2:1.0", "project :buildB:b2", "org.test:b2:1.0") {
                        compositeSubstitute()
                        edge("org.test:b1:1.0", "project :buildB:b1", "org.test:b1:1.0") {}
                    }
                }
            }
        }

        when:
        resolveFails(":resolveArtifacts")

        then:
        failure
            .assertHasDescription("Failed to build artifacts for build 'buildB'")
            .assertHasCause("Circular dependency between the following tasks:")
    }

    protected void resolveSucceeds  (String task) {
        resolve.prepare()
        super.execute(buildA, task, arguments)
    }

    protected void resolveFails(String task) {
        resolve.prepare()
        super.fails(buildA, task, arguments)
    }


    void checkGraph(@DelegatesTo(ResolveTestFixture.NodeBuilder) Closure closure) {
        resolve.expectGraph {
            root(":", "org.test:buildA:1.0", closure)
        }
    }
}
