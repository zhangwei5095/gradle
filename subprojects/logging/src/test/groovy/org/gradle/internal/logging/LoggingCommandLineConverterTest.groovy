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
package org.gradle.internal.logging

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.logging.configuration.LoggingConfiguration
import org.gradle.api.logging.configuration.ShowStacktrace
import org.gradle.cli.CommandLineArgumentException
import spock.lang.Specification

class LoggingCommandLineConverterTest extends Specification {
    final LoggingCommandLineConverter converter = new LoggingCommandLineConverter()
    final LoggingConfiguration expectedConfig = new DefaultLoggingConfiguration()

    def convertsEmptyArgs() {
        expect:
        checkConversion([])
    }

    def convertsDebugLevel() {
        expectedConfig.logLevel = LogLevel.DEBUG

        expect:
        checkConversion(['-d'])
        checkConversion(['--debug'])
    }

    def convertsInfoLevel() {
        expectedConfig.logLevel = LogLevel.INFO

        expect:
        checkConversion(['-i'])
        checkConversion(['--info'])
    }

    def convertsQuietLevel() {
        expectedConfig.logLevel = LogLevel.QUIET

        expect:
        checkConversion(['-q'])
        checkConversion(['--quiet'])
    }

    def convertsConsole() {
        expectedConfig.consoleOutput = consoleOutput

        expect:
        checkConversion([arg])

        where:
        arg               | consoleOutput
        "--console=plain" | ConsoleOutput.Plain
        "--console=auto"  | ConsoleOutput.Auto
        "--console=AUTO"  | ConsoleOutput.Auto
        "--console=rich"  | ConsoleOutput.Rich
    }

    def reportsUnknownConsoleOption() {
        when:
        converter.convert(["--console", "unknown"], new DefaultLoggingConfiguration())

        then:
        CommandLineArgumentException e = thrown()
        e.message == /Unrecognized value 'unknown' for console./
    }

    def convertsShowStacktrace() {
        expectedConfig.showStacktrace = ShowStacktrace.ALWAYS

        expect:
        checkConversion(['-s'])
        checkConversion(['--stacktrace'])
    }

    def convertsShowFullStacktrace() {
        expectedConfig.showStacktrace = ShowStacktrace.ALWAYS_FULL

        expect:
        checkConversion(['-S'])
        checkConversion(['--full-stacktrace'])
    }

    def usesLastLogLevelAndStacktraceOption() {
        expectedConfig.showStacktrace = ShowStacktrace.ALWAYS_FULL
        expectedConfig.logLevel = LogLevel.QUIET

        expect:
        checkConversion(['-s', '--debug', '-q', '--full-stacktrace'])
    }

    def providesLogLevelOptions() {
        expect:
        converter.logLevelOptions == ["d", "q", "i"] as Set
        converter.logLevels == [LogLevel.DEBUG, LogLevel.INFO, LogLevel.LIFECYCLE, LogLevel.QUIET] as Set
    }

    void checkConversion(List<String> args) {
        def actual = converter.convert(args, new DefaultLoggingConfiguration())
        assert actual.logLevel == expectedConfig.logLevel
        assert actual.consoleOutput == expectedConfig.consoleOutput
        assert actual.showStacktrace == expectedConfig.showStacktrace
    }
}
