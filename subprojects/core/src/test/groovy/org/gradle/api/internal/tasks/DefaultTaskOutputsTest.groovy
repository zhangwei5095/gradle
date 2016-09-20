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
package org.gradle.api.internal.tasks

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.TaskExecutionHistory
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.util.UsesNativeServices
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.Callable

import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.DIRECTORY
import static org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec.OutputType.FILE

@UsesNativeServices
class DefaultTaskOutputsTest extends Specification {

    private TaskMutator taskStatusNagger = Stub() {
        mutate(_, _) >> { String method, def action ->
            if (action instanceof Runnable) {
                action.run()
            } else if (action instanceof Callable) {
                action.call()
            }
        }
    }
    def task = Mock(TaskInternal) {
        getName() >> "task"
        toString() >> "task 'task'"
    }
    private final DefaultTaskOutputs outputs = new DefaultTaskOutputs({new File(it)} as FileResolver, task, taskStatusNagger)

    public void hasNoOutputsByDefault() {
        setup:
        assert outputs.files.files.isEmpty()
        assert !outputs.hasOutput
    }

    public void outputFileCollectionIsBuiltByTask() {
        setup:
        assert outputs.files.buildDependencies.getDependencies(task) == [task] as Set
    }

    def "can register output file"() {
        when: outputs.file("a")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output file with property name"() {
        when: outputs.file("a").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output dir"() {
        when: outputs.file("a")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [FILE]
    }

    def "can register output dir with property name"() {
        when: outputs.dir("a").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a')]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a")]
        outputs.fileProperties*.outputFile == [new File("a")]
        outputs.fileProperties*.outputType == [DIRECTORY]
    }

    def "cannot register output file with same property name"() {
        outputs.file("a").withPropertyName("alma")
        outputs.file("b").withPropertyName("alma")
        when:
        outputs.fileProperties
        then:
        def ex = thrown IllegalArgumentException
        ex.message == "Multiple output file properties with name 'alma'"
    }

    def "can register unnamed output files"() {
        when: outputs.files("a", "b")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
    }

    def "can register unnamed output files with property name"() {
        when: outputs.files("a", "b").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
    }

    def "can register named output files"() {
        when: outputs.namedFiles("fileA": "a", "fileB": "b")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1.fileA', '$1.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [FILE, FILE]
    }

    @Unroll
    def "can register named #name with property name"() {
        when: outputs."named${name.capitalize()}"("fileA": "a", "fileB": "b").withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop.fileA', 'prop.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [type, type]
        where:
        name          | type
        "files"       | FILE
        "directories" | DIRECTORY
    }

    def "can register future named output files"() {
        when: outputs.namedFiles({ ["fileA": "a", "fileB": "b"] })
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['$1.fileA', '$1.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [FILE, FILE]
    }

    def "can register future named output files with property name"() {
        when: outputs.namedFiles({ ["fileA": "a", "fileB": "b"] }).withPropertyName("prop")
        then:
        outputs.files.files.toList() == [new File('a'), new File("b")]
        outputs.fileProperties*.propertyName == ['prop.fileA', 'prop.fileB']
        outputs.fileProperties*.propertyFiles*.files.flatten() == [new File("a"), new File("b")]
        outputs.fileProperties*.outputFile == [new File("a"), new File("b")]
        outputs.fileProperties*.outputType == [FILE, FILE]
    }

    public void canRegisterOutputFiles() {
        when:
        outputs.file('a')

        then:
        outputs.files.files == [new File('a')] as Set
    }

    public void hasOutputsWhenEmptyOutputFilesRegistered() {
        when:
        outputs.files([])

        then:
        outputs.hasOutput
    }

    public void hasOutputsWhenNonEmptyOutputFilesRegistered() {
        when:
        outputs.file('a')

        then:
        outputs.hasOutput
    }

    public void hasOutputsWhenUpToDatePredicateRegistered() {
        when:
        outputs.upToDateWhen { false }

        then:
        outputs.hasOutput
    }

    public void canSpecifyUpToDatePredicateUsingClosure() {
        boolean upToDate = false

        when:
        outputs.upToDateWhen { upToDate }

        then:
        !outputs.upToDateSpec.isSatisfiedBy(task)

        when:
        upToDate = true

        then:
        outputs.upToDateSpec.isSatisfiedBy(task)
    }

    public void getPreviousFilesDelegatesToTaskHistory() {
        TaskExecutionHistory history = Mock()
        FileCollection outputFiles = Mock()

        setup:
        outputs.history = history

        when:
        def f = outputs.previousOutputFiles

        then:
        f == outputFiles
        1 * history.outputFiles >> outputFiles
    }

    public void getPreviousFilesFailsWhenNoTaskHistoryAvailable() {
        when:
        outputs.previousOutputFiles

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Task history is currently not available for this task.'
    }
}
