/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.tasks.scala;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

public class IncrementalCompileOptions implements Serializable {
    private File compilerJar;
    private File libraryJar;
    private File sbtInterfaceJar;
    private File compilerInterfaceSourceJar;
    private File cacheFile;
    private Map<File, File> allCacheFiles;
    private String scalaVersion;
    private String sbtVersion;
    private int residentCompilerLimit;

    @InputFile
    public File getCompilerJar() {
        return compilerJar;
    }

    public void setCompilerJar(File jar) {
        compilerJar = jar;
    }

    @InputFile
    public File getLibraryJar() {
        return libraryJar;
    }

    public void setLibraryJar(File jar) {
        libraryJar = jar;
    }

    @InputFile
    public File getSbtInterfaceJar() {
        return sbtInterfaceJar;
    }

    public void setSbtInterfaceJar(File jar) {
        sbtInterfaceJar = jar;
    }

    @InputFile
    public File getCompilerInterfaceSourceJar() {
        return compilerInterfaceSourceJar;
    }

    public void setCompilerInterfaceSourceJar(File jar) {
        compilerInterfaceSourceJar = jar;
    }

    public File getCacheFile() {
        return cacheFile;
    }

    public void setCacheFile(File file) {
        cacheFile = file;
    }

    public Map<File, File> getAllCacheFiles() {
        return allCacheFiles;
    }

    public void setAllCacheFiles(Map<File, File> allCacheFiles) {
        this.allCacheFiles = allCacheFiles;
    }

    @Input
    public String getScalaVersion() {
        return scalaVersion;
    }

    public void setScalaVersion(String version) {
        scalaVersion = version;
    }

    @Input
    public String getSbtVersion() {
        return sbtVersion;
    }

    public void setSbtVersion(String version) {
        sbtVersion = version;
    }

    public int getResidentCompilerLimit() {
        return residentCompilerLimit;
    }

    public void setResidentCompilerLimit(int limit) {
        residentCompilerLimit = limit;
    }
}
