/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.file.LeaksFileHandles
import org.gradle.test.fixtures.ivy.IvyDescriptorDependencyExclusion
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.junit.Rule
import spock.lang.Issue

@LeaksFileHandles
public abstract class AbstractIvyRemoteLegacyPublishIntegrationTest extends AbstractIntegrationSpec {
    abstract RepositoryServer getServer()

    @Rule ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    private RemoteIvyModule module
    private RemoteIvyRepository ivyRepo

    def setup() {
        requireOwnGradleUserHomeDir()
        ivyRepo = server.remoteIvyRepo
        module = ivyRepo.module("org.gradle", "publish", "2")
    }

    @Issue("GRADLE-3440")
    public void "can publish using uploadArchives"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

dependencies {
    compile "commons-collections:commons-collections:3.2.1"
    compile ("commons-beanutils:commons-beanutils:1.8.3") {
        exclude group: 'commons-logging'
    }
    compile ("commons-dbcp:commons-dbcp:1.4") {
       transitive = false
    }
    compile ("org.apache.camel:camel-jackson:2.15.3") {
        exclude module: 'camel-core'
    }
    runtime ("com.fasterxml.jackson.core:jackson-databind:2.2.3") {
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-annotations'
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-core'
    }
    runtime "commons-io:commons-io:1.4"
}

uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUpload()
        // TODO - should not check on each upload to a particular directory
        module.jar.sha1.expectParentCheckdir()
        module.jar.sha1.expectUpload()
        module.ivy.expectParentCheckdir()
        module.ivy.expectUpload()
        module.ivy.sha1.expectParentCheckdir()
        module.ivy.sha1.expectUpload()

        when:
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.parsedIvy.expectArtifact("publish", "jar").hasAttributes("jar", "jar", ["archives", "runtime"], null)

        with (module.parsedIvy) {
            dependencies.size() == 6
            dependencies["commons-collections:commons-collections:3.2.1"].hasConf("compile->default")
            dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasConf("compile->default")
            dependencies["commons-dbcp:commons-dbcp:1.4"].hasConf("compile->default")
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasConf("runtime->default")
            dependencies["commons-io:commons-io:1.4"].hasConf("runtime->default")

            dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasExclude(new IvyDescriptorDependencyExclusion('commons-logging','*','*', '*', '*', 'compile', 'exact'))
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasExclude(new IvyDescriptorDependencyExclusion('com.fasterxml.jackson.core','jackson-annotations','*', '*', '*', 'runtime', 'exact'))
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasExclude(new IvyDescriptorDependencyExclusion('com.fasterxml.jackson.core','jackson-core','*', '*', '*', 'runtime', 'exact'))
            dependencies["org.apache.camel:camel-jackson:2.15.3"].hasExclude(new IvyDescriptorDependencyExclusion('*','camel-core','*', '*', '*', 'compile', 'exact'))

            dependencies["commons-beanutils:commons-beanutils:1.8.3"].transitiveEnabled()
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].transitiveEnabled()
            dependencies["org.apache.camel:camel-jackson:2.15.3"].transitiveEnabled()
            !dependencies["commons-dbcp:commons-dbcp:1.4"].transitiveEnabled()
        }

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)
        progressLogger.uploadProgressLogged(module.ivy.uri)
    }

    public void "does not upload meta-data file when artifact upload fails"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUploadBroken()

        when:
        fails 'uploadArchives'

        then:
        module.ivyFile.assertDoesNotExist()

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)
    }
}
