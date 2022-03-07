package dorkbox.config

import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.reflect.jvm.jvmErasure

data class ConfigProp(val parent: Any, val member: KProperty<Any>) {
    fun isSupported(): Boolean {
        return member is KMutableProperty<*>
    }

    fun get(): Any {
        return member.getter.call(parent)
    }

    fun set(value: Any): Any {
        require(member is KMutableProperty<*>) { "Cannot set the immutable type ${member.returnType.jvmErasure}" }

        val originalValue = get()
        member.setter.call(parent, value)
        return originalValue
    }
}
