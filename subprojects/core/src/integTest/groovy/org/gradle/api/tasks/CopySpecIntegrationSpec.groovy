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

package org.gradle.api.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Unroll

import java.nio.charset.Charset

class CopySpecIntegrationSpec extends AbstractIntegrationSpec {

    @Rule
    public final TestResources resources = new TestResources(testDirectoryProvider, "copyTestResources")

    @Unroll
    def "can #taskName files with #taskType task using #charsetDescription charset when filteringCharset is #isSetDescription"() {
        given:
        buildScript """
            task ($taskName, type:$taskType) {
                from 'src'
                into 'dest'
                expand(one: 1)
                ${filteringCharset ? "filteringCharset = '$filteringCharset'" : ''}
            }
        """.stripIndent()

        when:
        if(platformDefaultCharset) {
            executer.withDefaultCharacterEncoding(platformDefaultCharset)
        }
        run taskName

        then:
        file('dest/accents.c').readLines(readCharset)[0] == expected

        where:
        // UTF8 is the actual encoding of the file accents.c.
        // Any byte sequence of the file accents.c is a valid ISO-8859-1 character sequence,
        // so we can read and write it with that encoding as well.
        taskType | platformDefaultCharset | filteringCharset | expected
        // platform default charset is honored
        'Copy'   | 'UTF-8'                | null             | 'éàüî 1'
        'Copy'   | 'UTF-8'                | null             | 'éàüî 1'
        'Sync'   | 'UTF-8'                | null             | 'éàüî 1'
        'Sync'   | 'UTF-8'                | null             | 'éàüî 1'
        // filtering charset is honored
        'Copy'   | null                   | 'UTF-8'          | 'éàüî 1'
        'Copy'   | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Copy'   | null                   | 'UTF-8'          | 'éàüî 1'
        'Copy'   | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Sync'   | null                   | 'UTF-8'          | 'éàüî 1'
        'Sync'   | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'Sync'   | null                   | 'UTF-8'          | 'éàüî 1'
        'Sync'   | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        // derived data
        taskName = taskType.toLowerCase(Locale.US)
        charsetDescription = filteringCharset ?: "platform default ${platformDefaultCharset ?: Charset.defaultCharset().name()}"
        isSetDescription = filteringCharset ? 'set' : 'unset'
        readCharset = filteringCharset ?: platformDefaultCharset
    }

    @Unroll
    def "can #operation files with #operation file operation using #charsetDescription charset when filteringCharset is #isSetDescription"() {
        given:
        buildScript """
            task ($operation) {
                doLast {
                    project.$operation {
                        from 'src'
                        into 'dest'
                        expand(one: 1)
                        ${filteringCharset ? "filteringCharset = '$filteringCharset'" : ''}
                    }
                }
            }
        """.stripIndent()

        when:
        if(platformDefaultCharset) {
            executer.withDefaultCharacterEncoding(platformDefaultCharset)
        }
        run operation

        then:
        file('dest/accents.c').readLines(readCharset)[0] == expected

        where:
        // UTF8 is the actual encoding of the file accents.c.
        // Any byte sequence of the file accents.c is a valid ISO-8859-1 character sequence,
        // so we can read and write it with that encoding as well.
        operation | platformDefaultCharset | filteringCharset | expected
        // platform default charset is honored
        'copy'    | 'UTF-8'                | null             | 'éàüî 1'
        'copy'    | 'UTF-8'                | null             | 'éàüî 1'
        'sync'    | 'UTF-8'                | null             | 'éàüî 1'
        'sync'    | 'UTF-8'                | null             | 'éàüî 1'
        // filtering charset is honored
        'copy'    | null                   | 'UTF-8'          | 'éàüî 1'
        'copy'    | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'copy'    | null                   | 'UTF-8'          | 'éàüî 1'
        'copy'    | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'sync'    | null                   | 'UTF-8'          | 'éàüî 1'
        'sync'    | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        'sync'    | null                   | 'UTF-8'          | 'éàüî 1'
        'sync'    | null                   | 'ISO-8859-1'     | new String('éàüî 1'.getBytes('UTF-8'), 'ISO-8859-1')
        // derived data
        charsetDescription = filteringCharset ?: "platform default ${platformDefaultCharset ?: Charset.defaultCharset().name()}"
        isSetDescription = filteringCharset ? 'set' : 'unset'
        readCharset = filteringCharset ?: platformDefaultCharset
    }

    def "can use filesMatching with List"() {
        given:
        buildScript """
            task (copy, type: Copy) {
                from 'src'
                into 'dest'
                filesMatching(['**/ignore/**', '**/sub/**']) {
                    name = "matched\${name}"
                }
            }
        """.stripIndent()

        when:
        succeeds 'copy'

        then:
        file('dest/one/ignore/matchedbad.file').exists()
        file('dest/two/ignore/matchedbad.file').exists()
        !file('dest/one/matchedone.a').exists()
    }

    def "can use filesNotMatching with List"() {
        given:
        buildScript """
            task (copy, type: Copy) {
                from 'src'
                into 'dest'
                filesNotMatching(['**/ignore/**', '**/sub/**']) {
                    name = "matched\${name}"
                }
            }
        """.stripIndent()

        when:
        succeeds 'copy'

        then:
        !file('dest/one/ignore/matchedbad.file').exists()
        !file('dest/two/ignore/matchedbad.file').exists()
        file('dest/one/matchedone.a').exists()
    }
}
