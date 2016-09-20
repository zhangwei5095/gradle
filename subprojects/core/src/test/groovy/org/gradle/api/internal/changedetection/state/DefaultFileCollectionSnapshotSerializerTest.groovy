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

package org.gradle.api.internal.changedetection.state

import com.google.common.base.Charsets
import com.google.common.hash.Hashing
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.TaskFilePropertySnapshotNormalizationStrategy.DefaultNormalizedFileSnapshot
import org.gradle.internal.serialize.SerializerSpec

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.ORDERED
import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED

class DefaultFileCollectionSnapshotSerializerTest extends SerializerSpec {
    def stringInterner = new StringInterner()
    def serializer = new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner)

    def "reads and writes the snapshot"() {
        when:
        def hash = Hashing.md5().hashString("foo", Charsets.UTF_8)
        DefaultFileCollectionSnapshot out = serialize(new DefaultFileCollectionSnapshot([
            "/1": new DefaultNormalizedFileSnapshot("1", DirSnapshot.getInstance()),
            "/2": new DefaultNormalizedFileSnapshot("2", MissingFileSnapshot.getInstance()),
            "/3": new DefaultNormalizedFileSnapshot("3", new FileHashSnapshot(hash))
        ], UNORDERED, true), serializer)

        then:
        out.snapshots.size() == 3
        out.snapshots['/1'].normalizedPath == "1"
        out.snapshots['/1'].snapshot instanceof DirSnapshot
        out.snapshots['/2'].normalizedPath == "2"
        out.snapshots['/2'].snapshot instanceof MissingFileSnapshot
        out.snapshots['/3'].normalizedPath == "3"
        out.snapshots['/3'].snapshot instanceof FileHashSnapshot
        out.snapshots['/3'].snapshot.hash == hash
        out.compareStrategy == UNORDERED
        out.pathIsAbsolute
    }

    def "should retain order in serialization"() {
        when:
        def hash = Hashing.md5().hashString("foo", Charsets.UTF_8)
        DefaultFileCollectionSnapshot out = serialize(new DefaultFileCollectionSnapshot([
            "/3": new DefaultNormalizedFileSnapshot("3", new FileHashSnapshot(hash)),
            "/2": new DefaultNormalizedFileSnapshot("2", MissingFileSnapshot.getInstance()),
            "/1": new DefaultNormalizedFileSnapshot("1", DirSnapshot.getInstance())
        ], ORDERED, true), serializer)

        then:
        out.snapshots.keySet() as List == ['/3', '/2', '/1']
    }
}
