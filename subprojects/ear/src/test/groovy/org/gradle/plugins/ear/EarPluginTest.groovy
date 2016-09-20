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

package org.gradle.plugins.ear

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

import static org.gradle.api.tasks.TaskDependencyMatchers.dependsOn
import static org.hamcrest.Matchers.hasItems

class EarPluginTest extends AbstractProjectBuilderSpec {
    private static final String TEST_APP_XML = """<?xml version="1.0" encoding="UTF-8"?>
<application xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns="http://java.sun.com/xml/ns/javaee" xmlns:application="http://java.sun.com/xml/ns/javaee/application_5.xsd" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_5.xsd" version="5">
  <display-name>Application</display-name>
  <module>
    <web>
      <web-uri>Web.war</web-uri>
      <context-root>/</context-root>
    </web>
  </module>
  <module>
    <ejb>jrules-bres-session-wl100-6.7.3.jar</ejb>
  </module>
</application>
"""

    def appliesBasePluginAndAddsConvention() {
        when:
        project.pluginManager.apply(EarPlugin)

        then:
        project.getPlugins().hasPlugin(BasePlugin)
        project.convention.plugins.ear instanceof EarPluginConvention
    }

    def createsConfigurations() {
        when:
        project.pluginManager.apply(EarPlugin)

        and:
        def configuration = project.configurations.getByName(EarPlugin.DEPLOY_CONFIGURATION_NAME)

        then:
        !configuration.visible
        !configuration.transitive

        when:
        configuration = project.configurations.getByName(EarPlugin.EARLIB_CONFIGURATION_NAME)

        then:
        !configuration.visible
        configuration.transitive
    }

    def addsTasks() {
        when:
        project.pluginManager.apply(EarPlugin)

        and:
        def task = project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        task instanceof Ear
        task.destinationDir == project.libsDir

        when:
        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]

        then:
        task dependsOn(EarPlugin.EAR_TASK_NAME)
    }

    def addsTasksToJavaProject() {
        when:
        project.pluginManager.apply(JavaPlugin.class)
        project.pluginManager.apply(EarPlugin)

        and:
        def task = project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        task instanceof Ear
        task dependsOn(JavaPlugin.CLASSES_TASK_NAME)
        task.destinationDir == project.libsDir

        when:
        task = project.tasks[BasePlugin.ASSEMBLE_TASK_NAME]

        then:
        task dependsOn(EarPlugin.EAR_TASK_NAME)
    }

    def dependsOnEarlibConfig() {
        when:
        project.pluginManager.apply(EarPlugin)

        and:
        def childProject = TestUtil.createChildProject(project, 'child')
        def javaPlugin = new JavaPlugin()
        javaPlugin.apply(childProject)

        and:
        project.dependencies {
            earlib project(path: childProject.path, configuration: 'archives')
        }

        and:
        def task = project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        task.taskDependencies.getDependencies(task)*.path.contains(':child:jar')
    }

    def appliesMappingsToArchiveTasks() {
        when:
        project.pluginManager.apply(EarPlugin)

        and:
        def task = project.task(type: Ear, 'customEar')

        then:
        task.destinationDir == project.libsDir
    }

    def worksWithJavaBasePluginAppliedBeforeEarPlugin() {
        when:
        project.pluginManager.apply(JavaBasePlugin.class)
        project.pluginManager.apply(EarPlugin)

        and:
        def task = project.task(type: Ear, 'customEar')

        then:
        task.destinationDir == project.libsDir
    }

    def appliesMappingsToArchiveTasksForJavaProject() {
        when:
        project.pluginManager.apply(EarPlugin)
        project.pluginManager.apply(JavaPlugin.class)

        and:
        def task = project.task(type: Ear, 'customEar')

        then:
        task.destinationDir == project.libsDir
        task dependsOn(hasItems(JavaPlugin.CLASSES_TASK_NAME))
    }

    def addsEarAsPublication() {
        when:
        project.pluginManager.apply(EarPlugin)

        and:
        def archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)

        then:
        archiveConfiguration.getAllArtifacts().size() == 1
        archiveConfiguration.getAllArtifacts().iterator().next().getType() == "ear"
    }

    def replacesWarAsPublication() {
        when:
        project.pluginManager.apply(EarPlugin)
        project.pluginManager.apply(WarPlugin)

        and:
        def archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)

        then:
        archiveConfiguration.getAllArtifacts().size() == 1
        archiveConfiguration.getAllArtifacts().iterator().next().getType() == "ear"
    }

    def replacesJarAsPublication() {
        when:
        project.pluginManager.apply(EarPlugin)
        project.pluginManager.apply(JavaPlugin)

        and:
        def archiveConfiguration = project.getConfigurations().getByName(Dependency.ARCHIVES_CONFIGURATION)

        then:
        archiveConfiguration.getAllArtifacts().size() == 1
        archiveConfiguration.getAllArtifacts().iterator().next().getType() == "ear"
    }

    def supportsAppDir() {
        given:
        project.file("src/main/application/META-INF").mkdirs()
        project.file("src/main/application/META-INF/test.txt").createNewFile()
        project.file("src/main/application/test2.txt").createNewFile()

        when:
        project.pluginManager.apply(EarPlugin)

        and:
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar "test2.txt"
        inEar "META-INF/test.txt"
    }

    def supportsRenamedAppDir() {
        given:
        project.file("src/main/myapp").mkdirs()
        project.file("src/main/myapp/test.txt").createNewFile()

        when:
        project.pluginManager.apply(EarPlugin)
        project.convention.plugins.ear.appDirName = "src/main/myapp"

        and:
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar "test.txt"
    }

    def supportsRenamingLibDir() {
        given:
        def childProject = TestUtil.createChildProject(project, 'child')
        childProject.file("src/main/resources").mkdirs()
        childProject.file("src/main/resources/test.txt").createNewFile()
        def javaPlugin = new JavaPlugin()
        javaPlugin.apply(childProject)

        when:
        project.pluginManager.apply(EarPlugin)
        project.convention.plugins.ear.libDirName = "APP-INF/lib"
        project.dependencies {
            earlib project(path: childProject.path, configuration: 'archives')
        }

        and:
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar "APP-INF/lib/child.jar"
    }

    def supportsGeneratingDeploymentDescriptor() {
        when:
        project.pluginManager.apply(EarPlugin)
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar "META-INF/application.xml"
    }

    def avoidsOverwritingDeploymentDescriptor() {
        given:
        project.file("src/main/application/META-INF").mkdirs()
        project.file("src/main/application/META-INF/application.xml").text = TEST_APP_XML

        when:
        project.pluginManager.apply(EarPlugin)
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar("META-INF/application.xml").text == TEST_APP_XML
    }

    def supportsRenamingDeploymentDescriptor() {
        when:
        project.pluginManager.apply(EarPlugin)
        project.convention.plugins.ear.deploymentDescriptor {
            fileName = "myapp.xml"
        }
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar "META-INF/myapp.xml"
    }

    def avoidsOverwritingRenamedDeploymentDescriptor() {
        given:
        project.file("src/main/application/META-INF").mkdirs()
        project.file("src/main/application/META-INF/myapp.xml").text = TEST_APP_XML

        when:
        project.pluginManager.apply(EarPlugin)
        project.convention.plugins.ear.deploymentDescriptor {
            fileName = "myapp.xml"
        }
        execute project.tasks[EarPlugin.EAR_TASK_NAME]

        then:
        inEar("META-INF/myapp.xml").text == TEST_APP_XML
    }

    private static void execute(Task task) {
        for (Task dep : task.taskDependencies.getDependencies(task)) {
            for (Action action : dep.actions) {
                action.execute(dep)
            }
        }
        for (Action action : task.actions) {
            action.execute(task)
        }
    }

    File inEar(path) {
        def ear = project.zipTree("build/libs/${project.name}.ear")
        assert !ear.empty
        ear.matching { include path }.singleFile
    }
}
