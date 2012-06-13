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

package org.gradle.api.internal.tasks.scala.incremental;

import org.apache.commons.io.FileUtils;
import sbt.ClasspathOptions;
import sbt.ScalaInstance;
import sbt.compiler.AggressiveCompile;
import sbt.compiler.AnalyzingCompiler;
import sbt.compiler.IC;
import scala.Option;
import scala.collection.Seq;
import xsbti.compile.JavaCompiler;

import java.io.File;
import java.net.URLClassLoader;

public class SbtCompilers implements xsbti.compile.Compilers<AnalyzingCompiler> {

    public static final File USER_HOME = new File(System.getProperty("user.home"));
    public static final File SBT_DIR = new File(USER_HOME, ".sbt");
    public static final File SBT_INC_DIR = new File(SBT_DIR, "inc");
    public static final String COMPILER_INTERFACE_ID = "compiler-interface";
    public static final String JAVA_VERSION = System.getProperty("java.class.version");

    private AnalyzingCompiler scalac;
    private JavaCompiler javac;

    public SbtCompilers(String scalaVersion, File libraryJar, File compilerJar, String sbtVersion, File xsbtiJar, File interfaceSrcJar, xsbti.Logger logger) throws Exception {
        ClassLoader scalaLoader = scalaLoader(libraryJar, compilerJar);
        ScalaInstance scalaInstance = new ScalaInstance(scalaVersion, scalaLoader, libraryJar, compilerJar, this.<File>emptySeq(), Option.<String>empty());
        File interfaceJar = compilerInterface(sbtVersion, interfaceSrcJar, xsbtiJar, scalaInstance, logger);
        this.scalac = IC.newScalaCompiler(scalaInstance, interfaceJar, ClasspathOptions.boot(), logger);
        this.javac = AggressiveCompile.directOrFork(scalaInstance, ClasspathOptions.javac(false), Option.<File>empty());
    }

    public AnalyzingCompiler scalac() {
        return scalac;
    }

    public JavaCompiler javac() {
        return javac;
    }

    public <A> Seq<A> emptySeq() {
        return (Seq) scala.collection.Seq$.MODULE$.<A>empty();
    }

    public ClassLoader scalaLoader(File libraryJar, File compilerJar) throws Exception {
        File[] allJars = { libraryJar, compilerJar };
        return new URLClassLoader(FileUtils.toURLs(allJars), getClass().getClassLoader().getParent());
    }

    public File compilerInterface(String sbtVersion, File interfaceSrcJar, File xsbtiJar, sbt.ScalaInstance scalaInstance, xsbti.Logger log) {
        File componentDir = new File(SBT_INC_DIR, sbtVersion);
        String id = COMPILER_INTERFACE_ID + "-" + scalaInstance.actualVersion() + "-" + JAVA_VERSION;
        File interfaceDir = new File(componentDir, id);
        interfaceDir.mkdirs();
        File interfaceJar = new File(interfaceDir, COMPILER_INTERFACE_ID + ".jar");
        if (!interfaceJar.exists()) {
            IC.compileInterfaceJar(COMPILER_INTERFACE_ID, interfaceSrcJar, interfaceJar, xsbtiJar, scalaInstance, log);
        }
        return interfaceJar;
    }
}
