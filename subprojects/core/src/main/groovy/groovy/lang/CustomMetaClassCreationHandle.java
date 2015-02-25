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

package groovy.lang;

import com.google.common.collect.Maps;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.runtime.GeneratedClosure;
import org.codehaus.groovy.runtime.metaclass.ClosureMetaClass;

import java.util.Map;

public class CustomMetaClassCreationHandle extends MetaClassRegistry.MetaClassCreationHandle {

    @Override
    protected MetaClass createNormalMetaClass(Class theClass, MetaClassRegistry registry) {
        if (GeneratedClosure.class.isAssignableFrom(theClass)) {
            return new ClosureMetaClass(registry, theClass);
        } else {
            return new CustomMetaClassImpl(registry, theClass);
        }
    }

    @Override
    public boolean isDisableCustomMetaClassLookup() {
        return true;
    }

    private static class CacheKey {
        private final String name;
        private final CachedClass cachedClass;

        public CacheKey(String name, CachedClass cachedClass) {
            this.name = name;
            this.cachedClass = cachedClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            CacheKey cacheKey = (CacheKey) o;

            return cachedClass.equals(cacheKey.cachedClass) && name.equals(cacheKey.name);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + cachedClass.hashCode();
            return result;
        }
    }

    private static class CustomMetaClassImpl extends MetaClassImpl {

        private static final Object NULL = new Object();
        private static Map<CacheKey, Object> CACHE = Maps.newHashMap();

        public CustomMetaClassImpl(MetaClassRegistry registry, Class theClass) {
            super(registry, theClass);
        }

        @Override
        protected MetaBeanProperty findPropertyInClassHierarchy(String propertyName, CachedClass theClass) {
            CacheKey key = new CacheKey(propertyName, theClass);
            Object o = CACHE.get(key);
            if (o == NULL) {
                MetaBeanProperty property = super.findPropertyInClassHierarchy(propertyName, theClass);
                if (property == null) {
                    CACHE.put(key, NULL);
                    return null;
                } else {
                    CACHE.put(key, property);
                    return property;
                }
            } else {
                return (MetaBeanProperty) o;
            }
        }
    }
}
