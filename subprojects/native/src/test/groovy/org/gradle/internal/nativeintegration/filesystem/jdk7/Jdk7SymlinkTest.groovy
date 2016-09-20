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

package org.gradle.internal.nativeintegration.filesystem.jdk7

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.Specification

class Jdk7SymlinkTest extends Specification {

    @Rule TestNameTestDirectoryProvider temporaryFolder

     @Requires(TestPrecondition.SYMLINKS)
    def 'on symlink supporting system, it will return true for supported symlink'() {
        expect:
        new Jdk7Symlink().isSymlinkSupported()
    }

     @Requires(TestPrecondition.NO_SYMLINKS)
    def 'on non symlink supporting system, it will return false for supported symlink'() {
        expect:
        !new WindowsJdk7Symlink().isSymlinkSupported()
    }

    @Requires(TestPrecondition.SYMLINKS)
    def 'can create and detect symlinks'() {
        def symlink = new Jdk7Symlink()
        def testDirectory = temporaryFolder.getTestDirectory().createDir()

        when:
        symlink.symlink(new File(testDirectory, 'testFile'), testDirectory.createFile('symFile'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testFile'))

        when:
        symlink.symlink(new File(testDirectory, 'testDir'), testDirectory.createDir('symDir'))

        then:
        symlink.isSymlink(new File(testDirectory, 'testDir'))
    }
}
