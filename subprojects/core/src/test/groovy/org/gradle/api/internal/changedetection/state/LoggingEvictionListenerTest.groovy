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

package org.gradle.api.internal.changedetection.state

import com.google.common.cache.CacheBuilder
import org.gradle.api.logging.Logger
import spock.lang.Specification

class LoggingEvictionListenerTest extends Specification {

    def "test logging eviction listener"() {
        given:
        InMemoryTaskArtifactCache.LoggingEvictionListener evictionListener = new InMemoryTaskArtifactCache.LoggingEvictionListener("cacheId", 1000)
        def logger = Mock(Logger)
        if (replaceLogger) {
            evictionListener.logger = logger
        }
        def cache = CacheBuilder.newBuilder().maximumSize(1000).removalListener(evictionListener).build()
        evictionListener.setCache(cache)

        when:
        2001.times { cache.put(it, it) }

        then:
        if (replaceLogger) {
            11 * logger.log(_, _, _)
        }
        noExceptionThrown()

        where:
        replaceLogger << [true, false]
    }
}
