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

package org.gradle.tooling.internal.provider

import org.gradle.StartParameter
import org.gradle.api.execution.internal.TaskInputsListener
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.initialization.BuildRequestMetaData
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestContext
import org.gradle.initialization.NoOpBuildEventConsumer
import org.gradle.initialization.ReportedException
import org.gradle.internal.concurrent.DefaultExecutorFactory
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.event.DefaultListenerManager
import org.gradle.internal.filewatch.FileSystemChangeWaiter
import org.gradle.internal.filewatch.FileSystemChangeWaiterFactory
import org.gradle.internal.invocation.BuildAction
import org.gradle.internal.logging.text.TestStyledTextOutputFactory
import org.gradle.internal.os.OperatingSystem
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.scopes.PluginServiceRegistry
import org.gradle.launcher.exec.BuildActionExecuter
import org.gradle.launcher.exec.BuildActionParameters
import org.gradle.util.Clock
import org.gradle.util.RedirectStdIn
import org.junit.Rule
import spock.lang.AutoCleanup
import spock.lang.Specification

class ContinuousBuildActionExecuterTest extends Specification {

    @Rule
    RedirectStdIn redirectStdIn = new RedirectStdIn()

    def delegate = Mock(BuildActionExecuter)
    def action = Mock(BuildAction) {
        1 * getStartParameter() >> Stub(StartParameter)
    }
    def cancellationToken = new DefaultBuildCancellationToken()
    def clock = Mock(Clock)
    def requestMetadata = Stub(BuildRequestMetaData)
    def requestContext = new DefaultBuildRequestContext(requestMetadata, cancellationToken, new NoOpBuildEventConsumer())
    def actionParameters = Stub(BuildActionParameters)
    def waiterFactory = Mock(FileSystemChangeWaiterFactory)
    def waiter = Mock(FileSystemChangeWaiter)
    def listenerManager = new DefaultListenerManager()
    @AutoCleanup("stop")
    def executorFactory = new DefaultExecutorFactory()
    def globalServices = Stub(ServiceRegistry)
    def executer = executer()
    def sessionService = Mock(Stoppable)

    private File file = new File('file')

    def setup() {
        requestMetadata.getBuildTimeClock() >> clock
        globalServices.getAll(PluginServiceRegistry) >> [
                Stub(PluginServiceRegistry) {
                    registerBuildSessionServices(_) >> { ServiceRegistration registration ->
                        registration.add(Stoppable, sessionService)
                    }
                }
        ]
        waiterFactory.createChangeWaiter(_) >> waiter
        waiter.isWatching() >> true
    }

    def "uses underlying executer when continuous build is not enabled"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters, _)
        0 * waiterFactory._
    }

    def "allows exceptions to propagate for single builds"() {
        when:
        singleBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            throw new RuntimeException("!")
        }
        executeBuild()

        then:
        thrown(RuntimeException)
    }

    def "waits for waiter"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
        }
        executeBuild()

        then:
        1 * waiter.wait(_,_) >> {
            cancellationToken.cancel()
        }
    }

    def "exits if there are no file system inputs"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _)
        executeBuild()

        then:
        waiter.isWatching() >> false
        0 * waiter.wait(_,_)
    }

    def "throws exception if last build fails in continous mode"() {
        when:
        continuousBuild()
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
            throw new ReportedException(new Exception("!"))
        }
        executeBuild()

        then:
        1 * waiter.wait(_,_) >> {
            cancellationToken.cancel()
        }
        thrown(ReportedException)
    }

    def "keeps running after failures when continuous"() {
        when:
        continuousBuild()
        executeBuild()

        then:
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
        }

        and:
        1 * waiter.wait(_,_)

        and:
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
            throw new ReportedException(new Exception("!"))
        }

        and:
        1 * waiter.wait(_,_)

        and:
        1 * delegate.execute(action, requestContext, actionParameters, _) >> {
            declareInput(file)
        }

        and:
        1 * waiter.wait(_,_) >> {
            cancellationToken.cancel()
        }
    }

    def "closes build session after single build"() {
        when:
        singleBuild()
        executeBuild()

        then:
        1 * sessionService.stop()
    }

    def "closes build session after continuous build"() {
        when:
        continuousBuild()
        executeBuild()

        then:
        waiter.wait(_,_) >> {
            cancellationToken.cancel()
        }
        1 * sessionService.stop()
    }

    private void singleBuild() {
        actionParameters.continuous >> false
    }

    private void continuousBuild() {
        actionParameters.continuous >> true
    }

    private void executeBuild() {
        executer.execute(action, requestContext, actionParameters, globalServices)
    }

    private void declareInput(File file) {
        listenerManager.getBroadcaster(TaskInputsListener).onExecute(Mock(TaskInternal), new SimpleFileCollection(file))
    }

    private ContinuousBuildActionExecuter executer() {
        new ContinuousBuildActionExecuter(delegate, listenerManager, new TestStyledTextOutputFactory(), OperatingSystem.current(), executorFactory, waiterFactory)
    }

}
