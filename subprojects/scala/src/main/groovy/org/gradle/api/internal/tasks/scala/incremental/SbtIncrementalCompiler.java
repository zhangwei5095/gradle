package org.gradle.api.internal.tasks.scala.incremental;

import com.google.common.collect.Iterables;
import org.gradle.api.logging.Logger;
import sbt.compiler.AnalyzingCompiler;
import sbt.compiler.CompilerCache;
import sbt.compiler.CompilerCache$;
import sbt.compiler.IC;
import sbt.inc.Analysis;
import xsbti.Maybe;
import xsbti.compile.CompileOrder;
import xsbti.compile.DefinesClass;
import xsbti.compile.GlobalsCache;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SbtIncrementalCompiler {
    private xsbti.Logger logger;

    private SbtCompilers compilers;

    private GlobalsCache compilerCache;

    public SbtIncrementalCompiler(String scalaVersion, File libraryJar, File compilerJar, String sbtVersion, File xsbtiJar, File interfaceJar, int maxCompilers, Logger log) throws Exception {
        log.info("Using incremental recompilation");
        this.logger = new SbtLogger(log);
        this.compilers = new SbtCompilers(scalaVersion, libraryJar, compilerJar, sbtVersion, xsbtiJar, interfaceJar, logger);
        this.compilerCache = (maxCompilers <= 0) ? CompilerCache.fresh() : CompilerCache$.MODULE$.apply(maxCompilers);
    }

    public void compile(Iterable<File> classPath, Iterable<File> sources, File classesDirectory, List<String> scalacOptions, List<String> javacOptions, File cacheFile, Map<File, File> cacheMap) {
        File[] classPathArray = Iterables.toArray(classPath, File.class);
        File[] sourcesArray = Iterables.toArray(sources, File.class);
        String[] scalacOptionsArray = scalacOptions.toArray(new String[scalacOptions.size()]);
        String[] javaOptionsArray = javacOptions.toArray(new String[javacOptions.size()]);
        Options options = new Options(classPathArray, sourcesArray, classesDirectory, scalacOptionsArray, javaOptionsArray);
        cacheFile = (cacheFile != null) ? cacheFile : SbtAnalysis.fallbackCacheLocation(classesDirectory);
        Setup setup = new Setup(classPath, classesDirectory, cacheFile, cacheMap, compilerCache);
        Inputs inputs = new Inputs(compilers, options, setup);
        Analysis analysis = IC.compile(inputs, logger);
        SbtAnalysis.put(cacheFile, analysis);
    }

    public static class Options implements xsbti.compile.Options {

        private File[] classpath;
        private File[] sources;
        private File classesDirectory;
        private String[] scalacOptions;
        private String[] javacOptions;

        public Options(File[] classpath, File[] sources, File classesDirectory, String[] scalacOptions, String[] javacOptions) {
            this.classpath = classpath;
            this.sources = sources;
            this.classesDirectory = classesDirectory;
            this.scalacOptions = scalacOptions;
            this.javacOptions = javacOptions;
        }

        public File[] classpath() {
            return classpath;
        }

        public File[] sources() {
            return sources;
        }

        public File classesDirectory() {
            return classesDirectory;
        }

        public String[] options() {
            return scalacOptions;
        }

        public String[] javacOptions() {
            return javacOptions;
        }

        public int maxErrors() {
            return 100;
        }

        public CompileOrder order() {
            return CompileOrder.Mixed;
        }
    }

    public static class Setup implements xsbti.compile.Setup<Analysis> {

        private File cacheFile;
        private GlobalsCache compilerCache;
        private HashMap<File, Maybe<Analysis>> analysisCache;

        public Setup(Iterable<File> classPath, File classesDirectory, File cacheFile, Map<File, File> cacheMap, GlobalsCache compilerCache) {
            this.cacheFile = cacheFile;
            this.compilerCache = compilerCache;
            this.analysisCache = new HashMap<File, Maybe<Analysis>>();
            for (File file : classPath) {
                analysisCache.put(file, SbtAnalysis.getAnalysis(file, classesDirectory, cacheMap));
            }
        }

        public Maybe<Analysis> analysisMap(File file) {
            Maybe<Analysis> analysis = analysisCache.get(file);
            return (analysis == null) ? SbtAnalysis.JUST_EMPTY_ANALYSIS : analysis;
        }

        public DefinesClass definesClass(File file) {
            return SbtLocate.definesClass(file);
        }

        public boolean skip() {
            return false;
        }

        public File cacheFile() {
            return cacheFile;
        }

        public GlobalsCache cache() {
            return compilerCache;
        }
    }

    public static class Inputs implements xsbti.compile.Inputs<Analysis, AnalyzingCompiler> {

        private SbtCompilers compilers;
        private Options options;
        private Setup setup;

        public Inputs(SbtCompilers compilers, Options options, Setup setup) {
            this.compilers = compilers;
            this.options = options;
            this.setup = setup;
        }

        public xsbti.compile.Compilers<AnalyzingCompiler> compilers() {
            return compilers;
        }

        public xsbti.compile.Options options() {
            return options;
        }

        public xsbti.compile.Setup<Analysis> setup() {
            return setup;
        }
    }
}
