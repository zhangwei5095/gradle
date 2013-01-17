/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.xml;

import java.io.IOException;
import java.io.Writer;

public abstract class AbstractEscapingWriter extends Writer {

    protected abstract void writeRaw(String message) throws IOException;

    protected abstract void writeRaw(char c) throws IOException;

    protected void writeXmlEncoded(char ch) throws IOException {
        if (ch == '<') {
            writeRaw("&lt;");
        } else if (ch == '>') {
            writeRaw("&gt;");
        } else if (ch == '&') {
            writeRaw("&amp;");
        } else if (ch == '"') {
            writeRaw("&quot;");
        } else if (!isLegalCharacter(ch)) {
            writeRaw('?');
        } else if (isRestrictedCharacter(ch)) {
            writeRaw("&#x");
            writeRaw(Integer.toHexString(ch));
            writeRaw(";");
        } else {
            writeRaw(ch);
        }
    }

    protected boolean isLegalCharacter(final char c) {
        if (c == 0) {
            return false;
        } else if (c <= 0xD7FF) {
            return true;
        } else if (c < 0xE000) {
            return false;
        } else if (c <= 0xFFFD) {
            return true;
        }
        return false;
    }

    protected void writeXmlEncoded(char[] message, int offset, int count) throws IOException {
        int end = offset + count;
        for (int i = offset; i < end; i++) {
            writeXmlEncoded(message[i]);
        }
    }

    protected boolean isRestrictedCharacter(char c) {
        if (c == 0x9 || c == 0xA || c == 0xD || c == 0x85) {
            return false;
        } else if (c <= 0x1F) {
            return true;
        } else if (c < 0x7F) {
            return false;
        } else if (c <= 0x9F) {
            return true;
        }
        return false;
    }

}
