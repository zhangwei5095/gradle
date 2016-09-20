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
package org.gradle.api.internal.tasks.execution;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskActionListener;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.ContextAwareTaskAction;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.tasks.StopActionException;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.groovy.scripts.ScriptSource;
import org.gradle.logging.StandardOutputCapture;
import org.gradle.util.JUnit4GroovyMockery;
import org.jmock.Expectations;
import org.jmock.Sequence;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.action.CustomAction;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.Collections.emptyList;
import static org.gradle.util.Matchers.*;
import static org.gradle.util.WrapUtil.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class ExecuteActionsTaskExecuterTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final TaskInternal task = context.mock(TaskInternal.class, "<task>");
    private final ContextAwareTaskAction action1 = context.mock(ContextAwareTaskAction.class, "action1");
    private final ContextAwareTaskAction action2 = context.mock(ContextAwareTaskAction.class, "action2");
    private final TaskStateInternal state = context.mock(TaskStateInternal.class);
    private final TaskExecutionContext executionContext = context.mock(TaskExecutionContext.class);
    private final ScriptSource scriptSource = context.mock(ScriptSource.class);
    private final StandardOutputCapture standardOutputCapture = context.mock(StandardOutputCapture.class);
    private final Sequence sequence = context.sequence("seq");
    private final TaskActionListener publicListener = context.mock(TaskActionListener.class);
    private final TaskActionExecutionListener internalListener = context.mock(TaskActionExecutionListener.class);
    private final ExecuteActionsTaskExecuter executer = new ExecuteActionsTaskExecuter(internalListener, publicListener);

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            ProjectInternal project = context.mock(ProjectInternal.class);

            allowing(task).getProject();
            will(returnValue(project));

            allowing(project).getBuildScriptSource();
            will(returnValue(scriptSource));

            allowing(task).getStandardOutputCapture();
            will(returnValue(standardOutputCapture));

            ignoring(scriptSource);
        }});
    }

    @Test
    public void doesNothingWhenTaskHasNoActions() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(emptyList()));

            oneOf(publicListener).beforeActions(task);
            inSequence(sequence);

            oneOf(state).setExecuting(true);
            inSequence(sequence);

            oneOf(state).executed(null);
            inSequence(sequence);

            oneOf(state).setExecuting(false);
            inSequence(sequence);

            oneOf(publicListener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void executesEachActionInOrder() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            oneOf(publicListener).beforeActions(task);
            inSequence(sequence);

            oneOf(internalListener).startTaskActions();
            inSequence(sequence);

            oneOf(state).setExecuting(true);
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action1).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action1).execute(task);
            inSequence(sequence);

            oneOf(action1).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action2).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action2).execute(task);
            inSequence(sequence);

            oneOf(action2).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).executed(null);
            inSequence(sequence);

            oneOf(state).setExecuting(false);
            inSequence(sequence);

            oneOf(publicListener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void executeDoesOperateOnNewActionListInstance() {
        context.checking(new Expectations() {
            {
                allowing(task).getActions();
                will(returnValue(toList(action1)));

                allowing(task).getTaskActions();
                will(returnValue(toList(action1)));

                oneOf(publicListener).beforeActions(task);
                inSequence(sequence);

                oneOf(internalListener).startTaskActions();
                inSequence(sequence);

                oneOf(state).setExecuting(true);
                inSequence(sequence);

                oneOf(state).setDidWork(true);
                inSequence(sequence);

                oneOf(standardOutputCapture).start();
                inSequence(sequence);

                oneOf(action1).contextualise(executionContext);
                inSequence(sequence);

                oneOf(action1).execute(task);
                will(new CustomAction("Add action to actions list") {
                    public Object invoke(Invocation invocation) throws Throwable {
                        task.getActions().add(action2);
                        return null;
                    }
                });

                inSequence(sequence);

                oneOf(action1).contextualise(null);
                inSequence(sequence);

                oneOf(standardOutputCapture).stop();
                oneOf(state).executed(null);
                inSequence(sequence);

                oneOf(state).setExecuting(false);
                inSequence(sequence);

                oneOf(publicListener).afterActions(task);
                inSequence(sequence);
            }
        });
        executer.execute(task, state, executionContext);
    }


    @Test
    public void stopsAtFirstActionWhichThrowsException() {
        final Throwable failure = new RuntimeException("failure");
        final Collector<Throwable> wrappedFailure = collector();
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            oneOf(publicListener).beforeActions(task);
            inSequence(sequence);

            oneOf(internalListener).startTaskActions();
            inSequence(sequence);

            oneOf(state).setExecuting(true);
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action1).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action1).execute(task);
            will(throwException(failure));
            inSequence(sequence);

            oneOf(action1).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).executed(with(notNullValue(Throwable.class)));
            will(collectTo(wrappedFailure));
            inSequence(sequence);

            oneOf(state).setExecuting(false);
            inSequence(sequence);

            oneOf(publicListener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);

        assertThat(wrappedFailure.get(), instanceOf(TaskExecutionException.class));
        TaskExecutionException exception = (TaskExecutionException) wrappedFailure.get();
        assertThat(exception.getTask(), equalTo((Task) task));
        assertThat(exception.getMessage(), equalTo("Execution failed for <task>."));
        assertThat(exception.getCause(), sameInstance(failure));
    }

    @Test
    public void stopsAtFirstActionWhichThrowsStopExecutionException() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            oneOf(publicListener).beforeActions(task);
            inSequence(sequence);

            oneOf(internalListener).startTaskActions();
            inSequence(sequence);

            oneOf(state).setExecuting(true);
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action1).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action1).execute(task);
            will(throwException(new StopExecutionException("stop")));
            inSequence(sequence);

            oneOf(action1).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).executed(null);
            inSequence(sequence);

            oneOf(state).setExecuting(false);
            inSequence(sequence);

            oneOf(publicListener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }

    @Test
    public void skipsActionWhichThrowsStopActionException() {
        context.checking(new Expectations() {{
            allowing(task).getTaskActions();
            will(returnValue(toList(action1, action2)));

            oneOf(publicListener).beforeActions(task);
            inSequence(sequence);

            oneOf(internalListener).startTaskActions();
            inSequence(sequence);

            oneOf(state).setExecuting(true);
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action1).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action1).execute(task);
            will(throwException(new StopActionException("stop")));
            inSequence(sequence);

            oneOf(action1).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).setDidWork(true);
            inSequence(sequence);

            oneOf(standardOutputCapture).start();
            inSequence(sequence);

            oneOf(action2).contextualise(executionContext);
            inSequence(sequence);

            oneOf(action2).execute(task);
            inSequence(sequence);

            oneOf(action2).contextualise(null);
            inSequence(sequence);

            oneOf(standardOutputCapture).stop();
            inSequence(sequence);

            oneOf(state).executed(null);
            inSequence(sequence);

            oneOf(state).setExecuting(false);
            inSequence(sequence);

            oneOf(publicListener).afterActions(task);
            inSequence(sequence);
        }});

        executer.execute(task, state, executionContext);
    }
}
