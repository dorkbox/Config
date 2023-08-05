/*
 * Copyright 2023 dorkbox, llc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dorkbox.config

import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

data class ConfigProp(val parent: Any, val member: KProperty<Any>) {
    var override = false

    @Synchronized
    fun isSupported(): Boolean {
        return member is KMutableProperty<*>
    }

    @Synchronized
    fun get(): Any {
        return get(parent)
    }

    @Synchronized
    fun get(parent: Any): Any {
        return member.getter.call(parent)
    }

    @Synchronized
    fun set(value: Any) {
        return set(parent, value)
    }

    @Synchronized
    fun set(parent: Any, value: Any) {
        require(member is KMutableProperty<*>) { "Cannot set the immutable type ${member.returnType.jvmErasure}" }

        member.setter.call(parent, value)
        // if the value is manually "set", then we consider it "not overridden"
        override = false
    }

    /**
     * When we are initially parsing the CLI/sys/env info, we use this to specify that it is overridden or not.
     *
     * When we are SAVING the data, we only save the baseline data, meaning that we do not save the overridden values
     */
    fun setOverload(overriddenValue: Any) {
        val get = get()
        if (get != overriddenValue) {
            set(overriddenValue)
            override = true
        }
    }

    // NOTE: this doesn't compare overridden!!
    override fun equals(other: Any?): Boolean {
        if (other !is ConfigProp) return false

        if (isSupported() != other.isSupported()) return false
        if (get() != other.get()) return false
        return true
    }
}
