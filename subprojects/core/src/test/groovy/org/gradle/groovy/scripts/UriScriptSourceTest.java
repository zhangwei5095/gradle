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

package org.gradle.groovy.scripts;

import org.gradle.internal.resource.UriTextResource;
import org.gradle.test.fixtures.file.TestFile;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;

import static org.gradle.util.Matchers.matchesRegexp;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class UriScriptSourceTest {
    private TestFile testDir;
    private File scriptFile;
    private URI scriptFileUri;
    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider();

    @Before
    public void setUp() throws URISyntaxException {
        testDir = tmpDir.createDir("scripts");
        scriptFile = new File(testDir, "build.script");
        scriptFileUri = scriptFile.toURI();
        createJar();
    }

    private URI createJar() throws URISyntaxException {
        TestFile jarFile = tmpDir.getTestDirectory().file("test.jar");
        testDir.file("ignoreme").write("content");
        testDir.zipTo(jarFile);
        return new URI(String.format("jar:%s!/build.script", jarFile.toURI()));
    }

    @Test
    public void canConstructSourceFromFile() throws IOException {
        scriptFile.createNewFile();
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertThat(source.getResource().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
    }

    @Test
    public void convenienceMethodScriptForFileThatHasContent() {
        new TestFile(scriptFile).write("content");
        ScriptSource source = UriScriptSource.file("<file-type>", scriptFile);
        assertThat(source, instanceOf(UriScriptSource.class));
        assertThat(source.getResource().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getCharset(), equalTo(Charset.forName("utf-8")));
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
        assertThat(source.getResource().getText(), equalTo("content"));
        assertFalse(source.getResource().isContentCached());
        assertFalse(source.getResource().getHasEmptyContent());
        assertTrue(source.getResource().getExists());
    }

    @Test
    public void convenienceMethodReplacesFileThatDoesNotExistWithEmptyScript() {
        ScriptSource source = UriScriptSource.file("<file-type>", scriptFile);
        assertThat(source, instanceOf(NonExistentFileScriptSource.class));
        assertNull(source.getResource().getFile());
        assertNull(source.getResource().getCharset());
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
        assertThat(source.getResource().getText(), equalTo(""));
        assertTrue(source.getResource().isContentCached());
        assertTrue(source.getResource().getHasEmptyContent());
        assertTrue(source.getResource().getExists()); // exists == has content
    }

    @Test
    public void canConstructSourceFromFileURI() throws IOException {
        scriptFile.createNewFile();
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertThat(source.getResource().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getCharset(), equalTo(Charset.forName("utf-8")));
        assertThat(source.getResource().getLocation().getFile(), equalTo(scriptFile));
        assertThat(source.getResource().getLocation().getURI(), equalTo(scriptFileUri));
    }

    @Test
    public void canConstructSourceFromJarURI() throws URISyntaxException {
        URI uri = createJar();
        UriScriptSource source = new UriScriptSource("<file-type>", uri);
        assertThat(source.getResource(), instanceOf(UriTextResource.class));
        assertNull(source.getResource().getFile());
        assertNull(source.getResource().getCharset());
        assertNull(source.getResource().getLocation().getFile());
        assertThat(source.getResource().getLocation().getURI(), equalTo(uri));
    }

    @Test
    public void usesScriptFileNameToBuildDescription() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingFileUri() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> '%s'", scriptFile.getAbsolutePath())));
    }

    @Test
    public void usesScriptFileNameToBuildDescriptionWhenUsingHttpUri() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getDisplayName(), equalTo(String.format("<file-type> 'http://www.gradle.org/unknown.txt'")));
    }

    @Test
    public void usesScriptFilePathForFileNameUsingFile() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getFileName(), equalTo(scriptFile.getAbsolutePath()));
    }

    @Test
    public void usesScriptFilePathForFileNameUsingFileUri() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFileUri);
        assertThat(source.getFileName(), equalTo(scriptFile.getAbsolutePath()));
    }

    @Test
    public void usesScriptUriForFileNameUsingHttpUri() throws URISyntaxException {
        UriScriptSource source = new UriScriptSource("<file-type>", new URI("http://www.gradle.org/unknown.txt"));
        assertThat(source.getFileName(), equalTo("http://www.gradle.org/unknown.txt"));
    }
    
    @Test
    public void generatesClassNameFromFileNameByRemovingExtensionAndAddingHashOfFileURL() {
        UriScriptSource source = new UriScriptSource("<file-type>", scriptFile);
        assertThat(source.getClassName(), matchesRegexp("build_[0-9a-z]+"));
    }

    @Test
    public void generatesClassNameFromFileNameByRemovingExtensionAndAddingHashOfJarURL() throws Exception {
        UriScriptSource source = new UriScriptSource("<file-type>", createJar());
        assertThat(source.getClassName(), matchesRegexp("build_[0-9a-z]+"));
    }

    @Test
    public void truncatesClassNameAt30Characters() {
        UriScriptSource source = new UriScriptSource("<file-type>", new File(testDir, "a-long-file-name-12345678901234567890.gradle"));
        assertThat(source.getClassName(), matchesRegexp("a_long_file_name_1234567890123_[0-9a-z]+"));
    }

    @Test
    public void encodesReservedCharactersInClassName() {
        UriScriptSource source = new UriScriptSource("<file-type>", new File(testDir, "name-+.chars.gradle"));
        assertThat(source.getClassName(), matchesRegexp("name___chars_[0-9a-z]+"));
    }

    @Test
    public void prefixesClassNameWhenFirstCharacterIsNotValidIdentifierStartChar() {
        UriScriptSource source = new UriScriptSource("<file-type>", new File(testDir, "123"));
        assertThat(source.getClassName(), matchesRegexp("_123_[0-9a-z]+"));

        source = new UriScriptSource("<file-type>", new File(testDir, "-"));
        assertThat(source.getClassName(), matchesRegexp("__[0-9a-z]+"));
    }

    @Test
    public void filesWithSameNameAndDifferentPathHaveDifferentClassName() {
        ScriptSource source1 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        ScriptSource source2 = new UriScriptSource("<file-type>", new File(testDir, "subdir/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }
    
    @Test
    public void filesWithSameNameAndUriHaveDifferentClassName() throws URISyntaxException {
        ScriptSource source1 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        ScriptSource source2 = new UriScriptSource("<file-type>", new URI("http://localhost/build.gradle"));
        assertThat(source1.getClassName(), not(equalTo(source2.getClassName())));

        ScriptSource source3 = new UriScriptSource("<file-type>", new File(testDir, "build.gradle"));
        assertThat(source1.getClassName(), equalTo(source3.getClassName()));
    }
}
