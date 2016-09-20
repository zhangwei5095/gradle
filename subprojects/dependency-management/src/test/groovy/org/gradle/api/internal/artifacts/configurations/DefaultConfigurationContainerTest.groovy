/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.artifacts.configurations

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.ClassGeneratorBackedInstantiator
import org.gradle.api.internal.DomainObjectContext
import org.gradle.api.internal.artifacts.ConfigurationResolver
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution.DependencySubstitutionRules
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder
import org.gradle.api.internal.file.TestFiles
import org.gradle.initialization.ProjectAccessListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import spock.lang.Specification

class DefaultConfigurationContainerTest extends Specification {

    private ConfigurationResolver resolver = Mock(ConfigurationResolver)
    private ListenerManager listenerManager = Stub(ListenerManager.class)
    private DependencyMetaDataProvider metaDataProvider = Mock(DependencyMetaDataProvider.class)
    private ProjectAccessListener projectAccessListener = Mock(ProjectAccessListener.class)
    private ProjectFinder projectFinder = Mock(ProjectFinder)
    private ConfigurationComponentMetaDataBuilder metaDataBuilder = Mock(ConfigurationComponentMetaDataBuilder)
    private ComponentIdentifierFactory componentIdentifierFactory = Mock(ComponentIdentifierFactory)
    private DependencySubstitutionRules globalSubstitutionRules = Mock(DependencySubstitutionRules)
    private Instantiator instantiator = new ClassGeneratorBackedInstantiator(new AsmBackedClassGenerator(), DirectInstantiator.INSTANCE)
    private DefaultConfigurationContainer configurationContainer = instantiator.newInstance(DefaultConfigurationContainer.class,
            resolver, instantiator, { name -> name } as DomainObjectContext,
            listenerManager, metaDataProvider, projectAccessListener, projectFinder, metaDataBuilder, TestFiles.fileCollectionFactory(),
            globalSubstitutionRules, componentIdentifierFactory)

    def addsNewConfigurationWhenConfiguringSelf() {
        when:
        configurationContainer.configure {
            newConf
        }

        then:
        configurationContainer.findByName('newConf') != null
        configurationContainer.newConf != null
    }

    def doesNotAddNewConfigurationWhenNotConfiguringSelf() {
        when:
        configurationContainer.getByName('unknown')

        then:
        thrown(UnknownConfigurationException)
    }

    def makesExistingConfigurationAvailableAsProperty() {
        when:
        Configuration configuration = configurationContainer.create('newConf')

        then:
        configuration != null
        configurationContainer.getByName("newConf").is(configuration)
        configurationContainer.newConf.is(configuration)
    }

    def addsNewConfigurationWithClosureWhenConfiguringSelf() {
        when:
        String someDesc = 'desc1'
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }

        then:
        configurationContainer.newConf.getDescription() == someDesc
    }

    def makesExistingConfigurationAvailableAsConfigureMethod() {
        when:
        String someDesc = 'desc1'
        configurationContainer.create('newConf')
        Configuration configuration = configurationContainer.newConf {
            description = someDesc
        }

        then:
        configuration.getDescription() == someDesc
    }

    def makesExistingConfigurationAvailableAsConfigureMethodWhenConfiguringSelf() {
        when:
        String someDesc = 'desc1'
        Configuration configuration = configurationContainer.create('newConf')
        configurationContainer.configure {
            newConf {
                description = someDesc
            }
        }

        then:
        configuration.getDescription() == someDesc
    }

    def newConfigurationWithNonClosureParametersShouldThrowMissingMethodEx() {
        when:
        configurationContainer.newConf('a', 'b')

        then:
        thrown MissingMethodException
    }
}
