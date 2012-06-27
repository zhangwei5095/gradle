package org.gradle.api.internal.tasks.scala.incremental;

import xsbti.F0;
import xsbti.Logger;

public class SbtLogger implements Logger {

    org.gradle.api.logging.Logger log;

    public SbtLogger(org.gradle.api.logging.Logger log) {
        this.log = log;
    }

    public void error(F0<String> msg) {
            log.lifecycle(msg.apply());
    }

    public void warn(F0<String> msg) {
            log.lifecycle(msg.apply());
    }

    public void info(F0<String> msg) {
            log.lifecycle(msg.apply());
    }

    public void debug(F0<String> msg) {
            log.lifecycle(msg.apply());
    }

    public void trace(F0<Throwable> exception) {
            log.lifecycle(exception.apply().toString());
    }
}
