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

package org.gradle.internal.resource.transport.http

import org.gradle.internal.resource.local.LocalResource

class HttpResourceUploaderTest extends AbstractHttpClientTest {

    def 'uploader closes the request'() {
        given:
        HttpClientHelper client = Mock()
        LocalResource resource = Mock()
        MockedHttpResponse mockedHttpResponse = mockedHttpResponse()

        when:
        new HttpResourceUploader(client).upload(resource, new URI("http://somewhere.org/somehow"))

        then:
        interaction {
            1 * client.performHttpRequest(_) >> mockedHttpResponse.response
            assertIsClosedCorrectly(mockedHttpResponse)
        }
        IOException exception = thrown()
        exception.message.contains('Could not PUT')
    }
}
