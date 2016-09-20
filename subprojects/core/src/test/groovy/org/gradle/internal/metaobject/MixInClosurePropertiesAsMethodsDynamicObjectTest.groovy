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

package org.gradle.internal.metaobject

import spock.lang.Specification

class MixInClosurePropertiesAsMethodsDynamicObjectTest extends Specification {
    def obj = new MixInClosurePropertiesAsMethodsDynamicObject() {
        @Override
        String getDisplayName() {
            return "<obj>"
        }
    }

    def "invokes method on first delegate that has a property with closure value"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        when:
        def result = obj.invokeMethod("m", ["value"] as Object[])

        then:
        result == "result"

        and:
        1 * obj1.invokeMethod("m", _, ["value"] as Object[])
        1 * obj2.invokeMethod("m", _, ["value"] as Object[])
        1 * obj3.invokeMethod("m", _, ["value"] as Object[])
        1 * obj1.getProperty("m", _)
        1 * obj2.getProperty("m", _) >> { String name, GetPropertyResult r -> r.result({ it -> "result" }) }
        0 * _
    }

    def "fails when first closure property does not accept the given parameters"() {
        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        given:
        obj3.getProperty("m", _) >> { String name, GetPropertyResult r -> r.result({ Number a -> "result 3" }) }

        expect:
        obj.invokeMethod("m", [12] as Object[]) == "result 3"

        when:
        obj.invokeMethod("m", [new Date()] as Object[])

        then:
        MissingMethodException e = thrown()
        e.method == "m"
    }

    def "can invoke custom closure implementation"() {
        def cl = new Closure(this, this) {
            @Override
            Object call(Object... args) {
                return "result"
            }
        }

        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        given:
        obj3.getProperty("m", _) >> { String name, GetPropertyResult r -> r.result(cl) }

        expect:
        obj.invokeMethod("m", [12] as Object[]) == "result"
    }

    def "can invoke curried closure"() {
        def cl = { String result, Number n -> "$result: $n" }.curry("result")

        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        given:
        obj3.getProperty("m", _) >> { String name, GetPropertyResult r -> r.result(cl) }

        expect:
        obj.invokeMethod("m", [12] as Object[]) == "result: 12"
    }

    def "fails when curried closure does not accept given parameters"() {
        def cl = { String result, Number n -> "$result: $n" }.curry("result")

        def obj1 = Mock(DynamicObject)
        def obj2 = Mock(DynamicObject)
        def obj3 = Mock(DynamicObject)
        obj.setObjects(obj1, obj2, obj3)

        given:
        obj3.getProperty("m", _) >> { String name, GetPropertyResult r -> r.result(cl) }

        when:
        obj.invokeMethod("m", ["not-a-number"] as Object[])

        then:
        MissingMethodException e = thrown()
        e.method == "doCall"
    }
}
