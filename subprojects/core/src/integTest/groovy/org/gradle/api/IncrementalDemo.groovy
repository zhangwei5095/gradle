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

class IncrementalDemo extends AbstractIntegrationSpec {

    def "foo"() {
        buildFile << """
            task zip(type: Zip) {
                from 'in'
                baseName = 'hello'
            }

            task unzip(type: Copy) {
                dependsOn zip
                from zipTree(zip.archivePath)
                into 'out'
            }
        """

        file("in/foo.txt") << "blah"
        file("in/bar.txt") << "blah"

        expect:
        run "unzip"
        run "unzip"
    }
}
