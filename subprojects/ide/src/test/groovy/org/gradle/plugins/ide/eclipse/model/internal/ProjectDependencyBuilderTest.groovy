/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model.internal

import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentRegistry
import org.gradle.api.internal.composite.CompositeBuildContext
import org.gradle.api.internal.composite.CompositeBuildIdeProjectResolver
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.plugins.ide.internal.resolver.model.IdeProjectDependency
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

import static org.gradle.internal.component.local.model.TestComponentIdentifiers.newProjectId

class ProjectDependencyBuilderTest extends AbstractProjectBuilderSpec {
    def ProjectComponentIdentifier projectId = newProjectId("anything")
    def localComponentRegistry = Mock(LocalComponentRegistry)
    def serviceRegistry = new DefaultServiceRegistry()
        .add(LocalComponentRegistry, localComponentRegistry)
        .add(CompositeBuildContext, Stub(CompositeBuildContext))
    def ProjectDependencyBuilder builder = new ProjectDependencyBuilder(new CompositeBuildIdeProjectResolver(serviceRegistry))
    def IdeProjectDependency ideProjectDependency = new IdeProjectDependency(projectId, "test")

    def "should create dependency using project name"() {
        when:
        def dependency = builder.build(ideProjectDependency)

        then:
        dependency.path == "/test"

        and:
        localComponentRegistry.getAdditionalArtifacts(_) >> []
    }

    def "should create dependency using eclipse projectName"() {
        given:
        def projectArtifact = Stub(LocalComponentArtifactMetadata) {
            getName() >> new DefaultIvyArtifactName("foo", "eclipse.project", "project", null)
        }
        localComponentRegistry.getAdditionalArtifacts(_) >> [projectArtifact]

        when:
        def dependency = builder.build(ideProjectDependency)

        then:
        dependency.path == '/foo'
    }
}
