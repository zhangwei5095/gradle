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

package org.gradle.api.tasks.compile

import org.gradle.integtests.fixtures.AbstractTaskRelocationIntegrationTest

import static org.gradle.util.JarUtils.jarWithContents

class JavaCompileRelocationIntegrationTest extends AbstractTaskRelocationIntegrationTest {

    @Override
    protected String getTaskName() {
        return ":compile"
    }

    @Override
    protected void setupProjectInOriginalLocation() {
        file("libs").createDir()
        file("libs/lib1.jar") << jarWithContents("data.txt": "data1")
        file("libs/lib2.jar") << jarWithContents("data.txt": "data2")
        file("src/main/java/sub-dir").createDir()
        file("src/main/java/Foo.java") << "public class Foo {}"

        buildFile << buildFileWithClasspath("libs")
    }

    private static String buildFileWithClasspath(String classpath) {
        """
            task compile(type: JavaCompile) {
                sourceCompatibility = JavaVersion.current()
                targetCompatibility = JavaVersion.current()
                destinationDir = file("build/classes")
                dependencyCacheDir = file("build/dependency-cache")
                source "src/main/java"
                classpath = files('$classpath')
            }
        """
    }

    @Override
    protected void moveFilesAround() {
        file("src/main/java/Foo.java").moveToDirectory(file("src/main/java/sub-dir"))
        file("libs").renameTo(file("lobs"))
        buildFile.text = buildFileWithClasspath("lobs")
    }

    @Override
    protected extractResults() {
        return file("build/classes/Foo.class").bytes
    }
}
