package org.gradle.api.internal.tasks.scala.incremental;

import xsbti.F0;
import xsbti.Logger;

public class SbtLogger implements Logger {

    org.slf4j.Logger log;

    public SbtLogger(org.slf4j.Logger log) {
        this.log = log;
    }

    public void error(F0<String> msg) {
        if (log.isErrorEnabled()) {
            log.error(msg.apply());
        }
    }

    public void warn(F0<String> msg) {
        if (log.isWarnEnabled()) {
            log.warn(msg.apply());
        }
    }

    public void info(F0<String> msg) {
        if (log.isInfoEnabled()) {
            log.info(msg.apply());
        }
    }

    public void debug(F0<String> msg) {
        if (log.isDebugEnabled()) {
            log.debug(msg.apply());
        }
    }

    public void trace(F0<Throwable> exception) {
        if (log.isDebugEnabled()) {
            log.debug(exception.apply().toString());
        }
    }
}
