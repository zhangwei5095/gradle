/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.api.internal.artifacts.publish

import org.gradle.api.internal.file.copy.CopyAction
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

public class ArchivePublishArtifactTest extends Specification {
    @Rule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()

    def TestUtil testUtil = TestUtil.create(temporaryFolder)

    def "provides sensible default values for quite empty archive tasks"() {
        def quiteEmptyJar = testUtil.task(DummyJar)

        when:
        def a = new ArchivePublishArtifact(quiteEmptyJar)

        then:
        a.archiveTask == quiteEmptyJar
        a.classifier == ""
        a.date.time == quiteEmptyJar.archivePath.lastModified()
        a.extension == "jar"
        a.file == quiteEmptyJar.archivePath
        a.type == "jar"
    }

    def "configures name correctly"() {
        def noName = testUtil.task(DummyJar)
        def withArchiveName = testUtil.task(DummyJar, [archiveName: "hey"])
        def withBaseName = testUtil.task(DummyJar, [baseName: "foo"])
        def withAppendix = testUtil.task(DummyJar, [baseName: "foo", appendix: "javadoc"])
        def withAppendixOnly = testUtil.task(DummyJar, [appendix: "javadoc"])

        expect:
        new ArchivePublishArtifact(noName).name == null
        new ArchivePublishArtifact(withArchiveName).name == null
        new ArchivePublishArtifact(withBaseName).name == "foo"
        new ArchivePublishArtifact(withBaseName).setName("haha").name == "haha"
        new ArchivePublishArtifact(withAppendix).name == "foo-javadoc"
        new ArchivePublishArtifact(withAppendixOnly).name == "javadoc"
    }

    static class DummyJar extends AbstractArchiveTask {
        DummyJar() {
            extension = "jar"
        }

        protected CopyAction createCopyAction() {
            return null
        }
    }
}
