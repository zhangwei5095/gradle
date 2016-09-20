/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.tasks.api.internal

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import spock.lang.Unroll

import java.lang.reflect.Modifier

class ApiClassExtractorTest extends ApiClassExtractorTestSupport {

    def "should not remove public method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                public void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        clazz.clazz.getDeclaredMethod('foo').modifiers == Modifier.PUBLIC
        hasMethod(extracted, 'foo')

        when:
        def o = extracted.newInstance()
        o.foo()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should not remove protected method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                protected void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PROTECTED
        hasMethod(extracted, 'foo')

        when:
        extracted.newInstance()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should remove private method"() {
        given:
        def api = toApi 'A': '''
            public class A {
                private void foo() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasMethod(clazz.clazz, 'foo').modifiers == Modifier.PRIVATE
        noSuchMethod(extracted, 'foo')

    }

    def "should not remove package private method if no API is defined"() {
        given:
        def api = toApi 'A': '''
            public class A {
                void foo() {}
                static void bar() {}
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasMethod(clazz.clazz, 'foo').modifiers == 0
        hasMethod(clazz.clazz, 'bar').modifiers == Opcodes.ACC_STATIC
        hasMethod(extracted, 'foo').modifiers == 0
        hasMethod(extracted, 'bar').modifiers == Opcodes.ACC_STATIC

    }

    def "should remove package private method if API is defined"() {
        given:
        def api = toApi([''], ['A': '''
            public class A {
                void foo() {}
                static void bar() {}
            }
        '''
        ])

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasMethod(clazz.clazz, 'foo').modifiers == 0
        hasMethod(clazz.clazz, 'bar').modifiers == Opcodes.ACC_STATIC
        noSuchMethod(extracted, 'foo')
        noSuchMethod(extracted, 'bar')

    }

    def "interface type should not generate implementation"() {
        given:
        def api = toApi 'A': '''
            public interface A {
                void foo();
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasMethod(clazz.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(extracted, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC

    }

    def "abstract class can have both implemented and non-implemented methods"() {
        given:
        def api = toApi(
            'com.acme.A': '''
                package com.acme;

                public abstract class A {
                    public static void STATIC_IN_A() {}
                    public abstract void foo();
                    public void bar() {}
                }
            ''',
            'com.acme.B': '''
                package com.acme;
                public class B extends A {
                    public static void STATIC_IN_B() {}
                    public void foo() {}
                }
            '''
        )

        when:
        def clazzA = api.classes['com.acme.A']
        def clazzB = api.classes['com.acme.B']
        def extractedA = api.extractAndLoadApiClassFrom(clazzA)
        def extractedB = api.extractAndLoadApiClassFrom(clazzB)

        then:
        api.shouldExtractApiClassFrom(clazzA)
        api.shouldExtractApiClassFrom(clazzB)
        hasMethod(clazzA.clazz, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(clazzA.clazz, 'bar').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(extractedA, 'foo').modifiers == Opcodes.ACC_ABSTRACT + Opcodes.ACC_PUBLIC
        hasMethod(extractedA, 'bar').modifiers == Opcodes.ACC_PUBLIC

        and:
        hasMethod(clazzB.clazz, 'foo').modifiers == Opcodes.ACC_PUBLIC
        hasMethod(extractedB, 'foo').modifiers == Opcodes.ACC_PUBLIC

        when:
        extractedB.newInstance()

        then:
        thrown(UnsupportedOperationException)

        when:
        extractedA.STATIC_IN_A()

        then:
        thrown(UnsupportedOperationException)

        when:
        extractedB.STATIC_IN_B()

        then:
        thrown(UnsupportedOperationException)

    }

    void "static initializer is removed"() {
        given:
        def api = toApi 'com.acme.A': '''
            package com.acme;

            public abstract class A {
                public static void forceInit() {}

                static {
                    if (true) {
                        throw new RuntimeException("This is a static initializer");
                    }
                }
            }
        '''

        when:
        api.classes['com.acme.A'].clazz.forceInit()

        then:
        def ex = thrown(ExceptionInInitializerError)
        ex.cause.message == 'This is a static initializer'

        when:
        def clazz = api.extractAndLoadApiClassFrom(api.classes['com.acme.A'])
        clazz.forceInit()

        then:
        ex = thrown(UnsupportedOperationException)
        ex.message =~ /You tried to call a method on an API class/
    }

    @Unroll
    void "constant initial value for #type is #expected"() {
        given:
        def api = toApi 'com.acme.A': """
            package com.acme;

            public abstract class A {
                public static $type CONSTANT = $value;
            }
        """

        when:
        def extracted = api.extractAndLoadApiClassFrom(api.classes['com.acme.A'])
        def extractedValue = extracted.CONSTANT

        then:
        extractedValue == expected

        where:
        type      | value          | expected
        'String'  | '"foo"'        | null
        'String'  | 'null'         | null
        'int'     | 123            | 0
        'Class'   | 'String.class' | null
        'boolean' | 'true'         | false
    }

    void "target binary compatibility is maintained"() {
        given:
        def api = toApi(target, [A: 'public class A {}'])

        when:
        def cr = new ClassReader(api.extractApiClassFrom(api.classes.A))
        def stubVersion = 0
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                stubVersion = version
            }
        }, 0)

        then:
        stubVersion == expectedVersion

        where:
        target | expectedVersion
        '1.6'  | 50
        '1.7'  | 51
    }

    def "should not remove public field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                public String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PUBLIC
        hasField(extracted, 'foo', String)

        when:
        def o = extracted.newInstance()
        o.foo()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should not remove protected field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                protected String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PROTECTED
        hasField(extracted, 'foo', String)

        when:
        extracted.newInstance()

        then:
        thrown(UnsupportedOperationException)

    }

    def "should remove private field"() {
        given:
        def api = toApi 'A': '''
            public class A {
                private String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasField(clazz.clazz, 'foo', String).modifiers == Modifier.PRIVATE
        noSuchField(extracted, 'foo', String)

    }

    def "should not remove package private field if no API is declared"() {
        given:
        def api = toApi 'A': '''
            public class A {
                String foo;
            }
        '''

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasField(clazz.clazz, 'foo', String).modifiers == 0
        hasField(extracted, 'foo', String).modifiers == 0

    }

    def "should remove package private field if API is declared"() {
        given:
        def api = toApi([''], ['A': '''
            public class A {
                String foo;
            }
        '''])

        when:
        def clazz = api.classes.A
        def extracted = api.extractAndLoadApiClassFrom(clazz)

        then:
        api.shouldExtractApiClassFrom(clazz)
        hasField(clazz.clazz, 'foo', String).modifiers == 0
        noSuchField(extracted, 'foo', String)

    }

    def "stubs should not contain any source or debug information"() {
        given:
        def api = toApi 'com.acme.A': '''
            package com.acme;

            public abstract class A {
                public static int FOO = 666;
                public void hello(String message) {
                    System.out.println(message);
                }
            }
        '''

        when:
        def apiClassBytes = api.extractApiClassFrom(api.classes['com.acme.A'])
        def cr = new ClassReader(apiClassBytes)
        cr.accept(new ClassVisitor(Opcodes.ASM5) {
            @Override
            void visitSource(String source, String debug) {
                super.visitSource(source, debug)
                if (source) {
                    throw new AssertionError("Source information should not be visited, but found source [$source]")
                }
                if (debug) {
                    throw new AssertionError("Debug information should not be visited, but found debug [$debug]")
                }
            }

            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM5) {
                    @Override
                    void visitLineNumber(int line, Label start) {
                        throw new AssertionError("Should not produce any line number information but " +
                            "method $name$desc contains line $line label $start")
                    }

                    @Override
                    void visitLocalVariable(String lname, String ldesc, String lsignature,
                                            Label start, Label end, int index) {
                        throw new AssertionError("Should not visit any local variable, but " +
                            "found $lname in method $name$desc")
                    }
                }
            }
        }, 0)

        then:
        noExceptionThrown()
    }

    def "package private class belongs to API if no API declared"() {
        given:
        def api = toApi 'A': '''
            class A {
                String foo;
            }
        '''

        when:
        def clazz = api.classes.A

        then:
        api.shouldExtractApiClassFrom(clazz)
    }

    def "package private class does not belong to API if API declared"() {
        given:
        def api = toApi([''], ['A': '''
            class A {
                String foo;
            }
        '''])

        when:
        def clazz = api.classes.A

        then:
        !api.shouldExtractApiClassFrom(clazz)
    }
}
