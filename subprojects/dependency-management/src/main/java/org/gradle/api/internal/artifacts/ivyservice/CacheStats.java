/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.gradle.internal.Factory;
import org.gradle.util.Clock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.gradle.util.Clock.prettyTime;

public class CacheStats {

    Map<String, Long> waitTime = new ConcurrentHashMap<String, Long>();

    public <T> Factory<? extends T> useCache(final Factory<? extends T> action) {
        final Clock clock = new Clock();
        return new Factory<T>() {
            public T create() {
                addWaitTime(clock.getTimeInMs());
                return action.create();
            }
        };
    }

    private void addWaitTime(long timeInMs) {
        String key = Thread.currentThread().getName();
        Long current = waitTime.get(key);
        if (current == null) {
            current = 0L;
        }
        waitTime.put(key, current + timeInMs);
    }

    public <T> Factory<? extends T> useCache(final Runnable action) {
        final Clock clock = new Clock();
        return new Factory<T>() {
            public T create() {
                addWaitTime(clock.getTimeInMs());
                action.run();
                return null;
            }
        };
    }

    public String printStats() {
        String out = "*** Cache stats: \n";
        for (String thread : waitTime.keySet()) {
            out += "  " + thread + " " + prettyTime(waitTime.get(thread));
        }
        return out;
    }
}
