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
package org.gradle.api.internal.changedetection.state

import com.google.common.collect.Iterators
import com.google.common.hash.Hashing
import com.google.common.io.Files
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTreeElement
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.rules.ChangeType
import org.gradle.api.internal.changedetection.rules.FileChange
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ChangeListener
import org.junit.Rule
import spock.lang.Specification

import static TaskFilePropertySnapshotNormalizationStrategy.ABSOLUTE
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.*

public class DefaultFileCollectionSnapshotterTest extends Specification {
    def fileSnapshotter = Stub(FileSnapshotter)
    def cacheAccess = Stub(TaskArtifactStateCacheAccess)
    def stringInterner = new StringInterner()
    def snapshotter = new DefaultFileCollectionSnapshotter(fileSnapshotter, cacheAccess, stringInterner, TestFiles.fileSystem(), TestFiles.directoryFileTreeFactory())
    def listener = Mock(ChangeListener)
    @Rule
    public final TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()

    def setup() {
        fileSnapshotter.snapshot(_) >> { FileTreeElement fileTreeElement ->
            return Stub(FileSnapshot) {
                getHash() >> Files.asByteSource(fileTreeElement.file).hash(Hashing.md5())
            }
        }
        fileSnapshotter.snapshot(_) >> { File file ->
            return Stub(FileSnapshot) {
                getHash() >> Files.asByteSource(file).hash(Hashing.md5())
            }
        }
        cacheAccess.useCache(_, _) >> { String name, Runnable action ->
            action.run()
        }
    }

    def getFilesReturnsOnlyTheFilesWhichExisted() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('dir')
        TestFile file2 = dir.createFile('file2')
        TestFile noExist = tmpDir.file('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, dir, noExist), UNORDERED, ABSOLUTE)

        then:
        snapshot.files as List == [file, file2]
    }

    def "retains order of files in the snapshot"() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')
        TestFile file3 = tmpDir.createFile('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, file2, file3), ORDERED, ABSOLUTE)

        then:
        snapshot.files == [file, file2, file3]
    }

    def getElementsReturnsAllFilesRegardlessOfWhetherTheyExistedOrNot() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile noExist = tmpDir.file('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, noExist), UNORDERED, ABSOLUTE)

        then:
        snapshot.elements == [file, noExist]
    }

    def getElementsIncludesRootDirectories() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile dir = tmpDir.createDir('dir')
        TestFile dir2 = dir.createDir('dir2')
        TestFile file2 = dir2.createFile('file2')
        TestFile noExist = tmpDir.file('file3')

        when:
        def snapshot = snapshotter.snapshot(files(file, dir, noExist), UNORDERED, ABSOLUTE)

        then:
        snapshot.elements == [file, dir, dir2, file2, noExist]
    }

    // Documenting existing behaviour
    def "retains order of elements in the snapshot with missing files at the end"() {
        given:
        TestFile file = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.file('file2')
        TestFile file3 = tmpDir.file('file3')
        TestFile file4 = tmpDir.createFile('file4')

        when:
        def snapshot = snapshotter.snapshot(files(file, file2, file3, file4), ORDERED, ABSOLUTE)

        then:
        snapshot.elements == [file, file4, file2, file3]
    }

    def generatesEventWhenFileAdded() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        def snapshot = snapshotter.snapshot(files(file1), UNORDERED, ABSOLUTE)
        changes(snapshotter.snapshot(files(file1, file2), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.added(file2.path)
        0 * _
    }

    def doesNotGenerateEventWhenFileAddedAndAddEventsAreFiltered() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.file('file2')
        TestFile file3 = tmpDir.createFile('file3')
        TestFile file4 = tmpDir.createDir('file4')

        when:
        def snapshot = snapshotter.snapshot(files(file1, file2), UNORDERED, ABSOLUTE)
        file2.createFile()
        def target = snapshotter.snapshot(files(file1, file2, file3, file4), OUTPUT, ABSOLUTE)
        Iterators.size(target.iterateContentChangesSince(snapshot, "TYPE")) == 0

        then:
        0 * _
    }

    def generatesEventWhenFileRemoved() {
        given:
        TestFile file1 = tmpDir.createFile('file1')
        TestFile file2 = tmpDir.createFile('file2')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2), UNORDERED, ABSOLUTE)
        changes(snapshotter.snapshot(files(file1), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.removed(file2.path)
        0 * _
    }

    def doesNotGenerateEventForFileWhoseTypeAndMetaDataAndContentHaveNotChanged() {
        given:
        TestFile file = tmpDir.createFile('file')
        file.setLastModified(1234L)

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE)
        changes(snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE), snapshot, listener)
        file.setLastModified(45600L)
        changes(snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        0 * listener._
    }

    def generatesEventWhenFileBecomesADirectory() {
        given:
        TestFile root = tmpDir.createDir('root')
        TestFile file = root.createFile('file')
        def fileCollection = files(root)

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE)
        file.delete()
        file.createDir()
        changes(snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def generatesEventWhenFileContentChanges() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE)
        file.write('new content')
        changes(snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.changed(file.path)
        0 * _
    }

    def doesNotGenerateEventForDirectoryThatHasNotChanged() {
        TestFile dir = tmpDir.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(dir), UNORDERED, ABSOLUTE)

        changes(snapshotter.snapshot(files(dir), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        0 * _
    }

    def generatesEventForDirectoryThatBecomesAFile() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile dir = root.createDir('dir')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE)
        dir.deleteDir()
        dir.createFile()
        changes(snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.changed(dir.path)
        0 * listener._
    }

    def doesNotGenerateEventForMissingFileThatStillIsMissing() {
        TestFile file = tmpDir.file('unknown')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE)
        changes(snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        0 * _
    }

    def generatesEventWhenMissingFileIsCreated() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.file('newfile')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE)
        file.createFile()
        changes(snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.added(file.path)
    }

    def generatesEventWhenFileIsDeleted() {
        TestFile root = tmpDir.createDir('root')
        def fileCollection = files(root)
        TestFile file = root.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE)
        file.delete()
        changes(snapshotter.snapshot(fileCollection, UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        1 * listener.removed(file.path)
    }

    def ignoresDuplicatesInFileCollection() {
        TestFile file1 = tmpDir.createFile('file')
        TestFile file2 = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.snapshot(files(file1, file2), UNORDERED, ABSOLUTE)
        changes(snapshotter.snapshot(files(file1), UNORDERED, ABSOLUTE), snapshot, listener)

        then:
        0 * _
    }

    def canCreateEmptySnapshot() {
        TestFile file = tmpDir.createFile('file')

        when:
        FileCollectionSnapshot snapshot = snapshotter.emptySnapshot()
        FileCollectionSnapshot newSnapshot = snapshotter.snapshot(files(file), UNORDERED, ABSOLUTE)
        changes(newSnapshot, snapshot, listener)

        then:
        snapshot.files.empty
        1 * listener.added(file.path)
        0 * listener._
    }

    def "caches file type in memory until notified of potential changes"() {
        def dir = tmpDir.createDir('dir')
        def file = tmpDir.createFile('file')
        def missing = tmpDir.file('missing')

        given:
        def snapshot = snapshotter.snapshot(files(dir, file, missing), UNORDERED, ABSOLUTE)

        when:
        dir.deleteDir().createFile()
        missing.createFile()

        def snapshot2 = snapshotter.snapshot(files(dir, file, missing), UNORDERED, ABSOLUTE)
        changes(snapshot2, snapshot, listener)

        then:
        0 * listener._

        when:
        snapshotter.startTaskActions()

        def snapshot3 = snapshotter.snapshot(files(dir, file, missing), UNORDERED, ABSOLUTE)
        changes(snapshot3, snapshot, listener)

        then:
        1 * listener.changed(dir.absolutePath)
        1 * listener.changed(missing.absolutePath)
        0 * listener._
    }

    private static void changes(FileCollectionSnapshot newSnapshot, FileCollectionSnapshot oldSnapshot, ChangeListener<String> listener) {
        newSnapshot.iterateContentChangesSince(oldSnapshot, "TYPE").each { FileChange change ->
            switch (change.type) {
                case ChangeType.ADDED:
                    listener.added(change.path)
                    break;
                case ChangeType.MODIFIED:
                    listener.changed(change.path)
                    break;
                case ChangeType.REMOVED:
                    listener.removed(change.path)
                    break;
            }
        }
    }

    private static FileCollection files(File... files) {
        new SimpleFileCollection(files)
    }

}
