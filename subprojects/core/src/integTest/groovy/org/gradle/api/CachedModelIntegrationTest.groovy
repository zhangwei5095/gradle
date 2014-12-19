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



package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.Ignore
import spock.lang.IgnoreIf

class CachedModelIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        executer.requireIsolatedDaemons()
        executer.withClassLoaderCaching(true)
        System.setProperty("cache.model", "")
    }

    def "java project"() {
        buildFile << """
            apply plugin: 'java'
            gradle.projectsLoaded { println 'loaded!!!' }
            repositories { jcenter() }

            task a << {
                def c = configurations.detachedConfiguration(dependencies.create('org.objenesis:objenesis:2.1'))
                println c.singleFile
            }
        """

        file("src/main/java/Foo.java") << "public class Foo {}"

        expect:
        run("a")
        run("a")
        run("a")
        run("a")
        run("a")
    }
}