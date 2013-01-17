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

package org.gradle.reporting;

import org.gradle.api.internal.xml.AbstractEscapingWriter;

import java.io.IOException;
import java.io.Writer;

public class HtmlBuilder extends com.googlecode.jatl.HtmlBuilder<HtmlBuilder> {

    private final Writer writer;

    /**
     * See {@link com.googlecode.jatl.MarkupBuilder#MarkupBuilder(java.io.Writer)}
     *
     * @param writer never <code>null</code>.
     */
    public HtmlBuilder(Writer writer) {
        super(writer);
        this.writer = writer;
    }

    public Writer getWriter() {
        return new HtmlEscapingWriter(writer);
    }

    @Override
    protected HtmlBuilder getSelf() {
        return this;
    }


    private class HtmlEscapingWriter extends AbstractEscapingWriter {
        private final Writer delegate;

        public HtmlEscapingWriter(Writer writer) {
            this.delegate = writer;
        }

        @Override
        protected void writeRaw(String message) throws IOException {
            delegate.write(message);
        }

        @Override
        protected void writeRaw(char c) throws IOException {
            delegate.write(c);
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            writeXmlEncoded(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            // do nothing here
        }
    }
}
