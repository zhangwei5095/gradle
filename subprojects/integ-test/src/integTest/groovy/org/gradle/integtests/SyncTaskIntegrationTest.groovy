/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.integtests

import groovy.transform.NotYetImplemented
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class SyncTaskIntegrationTest extends AbstractIntegrationSpec {

    def 'copies files and removes extra files from destDir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            file 'extra.txt'
            extraDir { file 'extra.txt' }
            dir1 {
                file 'extra.txt'
                extraDir { file 'extra.txt' }
            }
            someOtherEmptyDir {}
        }

        buildScript '''
            task sync(type: Sync) {
                into 'dest'
                from 'source'
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').assertHasDescendants(
                'dir1/file1.txt',
                'dir2/subdir/file2.txt',
                'dir2/file3.txt'
        )
        !file('dest/someOtherEmptyDir').exists()
        file('dest/emptyDir').exists()
    }

    def 'preserve keeps specified files in destDir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            file 'extra.txt'
            extraDir { file 'extra.txt' }
            dir1 {
                file 'extra.txt'
                extraDir { file 'extra.txt' }
            }
        }

        buildScript '''
            task sync(type: Sync) {
                into 'dest'
                from 'source'
                preserve {
                  include 'extraDir/**'
                  include 'dir1/**'
                  exclude 'dir1/extra.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').assertHasDescendants(
            'dir1/file1.txt',
            'dir2/subdir/file2.txt',
            'dir2/file3.txt',
            'extraDir/extra.txt',
            'dir1/extraDir/extra.txt',
        )
    }

    def 'only excluding non-preserved files works as expected'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            somePreservedDir {
                file 'preserved.txt'
                file 'also-not-preserved.txt'
            }
            someOtherDir {
                file 'preserved.txt'
                file 'not-preserved.txt'
            }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    exclude 'someOtherDir/not-preserved.txt'
                    exclude 'somePreservedDir/also-not-preserved.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').allDescendants() == [
            'dir1/file1.txt',
            'dir2/subdir/file2.txt',
            'dir2/file3.txt',
            'someOtherDir/preserved.txt',
            'somePreservedDir/preserved.txt'
        ] as Set
    }

    def 'sync is up to date when only changing preserved files'() {
        given:
        file('source').create {
            file 'not-preserved.txt'
        }

        file('dest').create {
            file 'preserved.txt'
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preserved.txt'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest').allDescendants() == ['not-preserved.txt', 'preserved.txt'] as Set
        skippedTasks.empty

        when:
        file('dest/preserved.txt').text = 'Changed!'
        run 'sync'

        then:
        skippedTasks == [':sync'] as Set

        when:
        file('dest/not-preserved.txt').text = 'Changed!'
        run 'sync'

        then:
        skippedTasks.empty
    }

    @NotYetImplemented
    def 'sync is not up to date when files are added to the destination dir'() {
        given:
        defaultSourceFileTree()
        file('dest').create {}

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        skippedTasks.empty

        when:
        file('dest/new-file.txt').text = 'Created!'
        run 'sync'

        then:
        skippedTasks.empty
    }

    @NotYetImplemented
    def 'sync is not up to date when the preserve filter is changed'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            preserved { file('some-preserved-file.txt') }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preserved'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        skippedTasks.empty
        file('dest/preserved').exists()

        when:
        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
            }
        '''.stripIndent()
        run 'sync'

        then:
        skippedTasks.empty
        !file('dest/preserved').exists()
    }

    def 'default excludes are removed with non-preserved directories'(String preserved) {
        given:
        defaultSourceFileTree()
        file('dest').create {
            some {
                '.git' { }
            }
            out {
                '.git' {
                    file 'config'
                }
                file 'some.txt'
            }
        }

        buildScript """
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                  ${preserved}
                }
            }
        """.stripIndent()

        when:
        run 'sync'

        then:
        file('dest/out/.git').isDirectory()
        !file('dest/some').exists()

        where:
        preserved << ["include 'out/some.txt'", "exclude 'some'"]
    }

    def 'empty directories can be preserved and synced'() {
        given:
        defaultSourceFileTree()
        file('dest').create {
            preservedDir { }
            nonPreservedDir { }
        }

        buildScript '''
            task sync(type: Sync) {
                from 'source'
                into 'dest'
                preserve {
                    include 'preservedDir'
                }
            }
        '''.stripIndent()

        when:
        run 'sync'

        then:
        file('dest/preservedDir').isDirectory()
        file('dest/emptyDir').isDirectory()
        !file('dest/nonPreservedDir').isDirectory()
    }

    def defaultSourceFileTree() {
        file('source').create {
            dir1 { file 'file1.txt' }
            dir2 {
                subdir { file 'file2.txt' }
                file 'file3.txt'
            }
            emptyDir {}
        }
    }
}
