package dorkbox.config

import com.squareup.moshi.Json
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dorkbox.os.OS
import mu.KLogger
import java.io.File
import java.lang.reflect.Modifier
import java.util.*
import mu.KotlinLogging.logger
import kotlin.reflect.KProperty
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.exitProcess

/**
 * we want to support configuration properties, such that ANYTHING in the config file can ALSO be passed in on the command line or as an env/system property
 *
 * ```
 * commandline > system property > environment variable > config file
 * ```
 * Once a property has been defined, it cannot be overloaded again via a different method, as specified in the above hierarchy,
 *
 * During a save operation, overloaded values will be ignored unless they were manually changed to something different
 */
@Suppress("MemberVisibilityCanBePrivate", "unused")
open class Config<T: Any>(
    /**
     * Enables setting environment variable names that might conflict with the standard set of names, for example,
     * PATH...
     *
     * The system uses PATH, but if we want to use path for something different, we can via setting the prefix.
     * For example, setting the prefix to "CONFIG__" means that for us to set the `path` property, we set it via
     *
     *```
     *  CONFIG__path="/home/blah/whatever"
     *```
     *  And this way, **OUR** path does not conflict with the system path.
     */
    private val environmentVarPrefix: String = "",
    moshiAdapter: Moshi.Builder.() -> JsonAdapter<T>) {


    companion object {
        private val regexEquals = "=".toRegex()
        private val locale = Locale.getDefault()

        /**
         * Gets the version number.
         */
        const val version = "1.3"

        init {
            // Add this project to the updates system, which verifies this class + UUID + version information
            dorkbox.updates.Updates.add(Config::class.java, "23475d7cdfef4c1e9c38c310420086ca", version)
        }
    }

    private val logger: KLogger = logger() {}

    private val moshi = moshiAdapter(Moshi.Builder())

    @Volatile
    private var configFile: File? = null

    private lateinit var originalConfig: T
    private lateinit var originalConfigMap: Map<String, ConfigProp>

    private lateinit var configMap: Map<String, ConfigProp>
    private lateinit var config: T

    private val arguments = ArrayList<String>()


    /** Contains a list of the changed properties (via overloading) */
    private val trackedConfigProperties = mutableMapOf<String, Any>()
    private val originalOverloadedProperties = mutableMapOf<String, Any>()

    /**
     * Loads a configuration from disk, if possible.
     *
     * ```
     * commandline > system property > environment variable > config file
     * ```
     */
    fun load(configFile: File, createDefaultObject: () -> T): T {
        return loadMoshi(loadOrNull(configFile), createDefaultObject)
    }

    /**
     * Loads a configuration from a string, if possible.
     *
     * ```
     * commandline > system property > environment variable > input string
     * ```
     */
    fun load(configText: String, createDefaultObject: () -> T): T {
        return loadMoshi(loadOrNull(configText), createDefaultObject)
    }

    /**
     * Loads a configuration from disk, if possible.
     *
     * ```
     * commandline > system property > environment variable > config file
     * ```
     */
    fun loadOrNull(configFile: File): T? {
        this.configFile = configFile
        var localConfig: T? = null
        if (configFile.canRead()) {
            val argFileContents = configFile.readText(Charsets.UTF_8)
            if (argFileContents.isNotEmpty()) {
                try {
                    // we want to make ALL NEW configs, each referencing a DIFFERENT object
                    localConfig = moshi.fromJson(argFileContents)!!
                }
                catch (ignored: Exception) {
                }
            }
        }

        return localConfig
    }

    /**
     * Loads a configuration from a string, if possible.
     *
     * ```
     * commandline > system property > environment variable > input string
     * ```
     */
    fun loadOrNull(configText: String): T? {
        this.configFile = null
        return try {
            // we want to make ALL NEW configs, each referencing a DIFFERENT object
            moshi.fromJson(configText)!!
        } catch (ignored: Exception) {
            // there was a problem parsing the config, so we will use null to signify that
            null
        }
    }

    private fun loadMoshi(localConfig: T?, createDefaultObject: () -> T): T {
        var localConfig = localConfig
        val generateLocalConfig = localConfig == null
        if (generateLocalConfig) {
            localConfig = createDefaultObject()
            configMap = createConfigMap(localConfig)
        }

        // now, we make a COPY of the original values from the file (so when saving, we know what the overloaded values are and not save them)
        originalConfig = localConfig!!
        // create the map that knows what members have what values
        originalConfigMap = createConfigMap(originalConfig)


        // let's also make a deep copy.
        config = moshi.fromJson(moshi.toJson(originalConfig))!!
        // create the map that knows what members have what values
        configMap = createConfigMap(config)

        if (generateLocalConfig) {
            save()
        }

        return config
    }


    fun save(): String {
        val possiblyOverloadedConfigMap = configMap

        // now, when we SAVE, we want to make sure that we DO NOT save overloaded values!
        // we ONLY save the original values + any values that have been modified.
        val changedProperties = mutableListOf<String>()
        trackedConfigProperties.entries.forEach { (key, _) ->
            // check to see if our overloaded value has been changed...
            val inMemoryProperty = possiblyOverloadedConfigMap[key]
            val originalOverloadedProp = originalOverloadedProperties[key]

            // if it has changed, then REMOVE IT from the list of things to ignore
            if (inMemoryProperty!!.get() != originalOverloadedProp) {
                changedProperties.add(key)
            }
        }
        changedProperties.forEach {
            trackedConfigProperties.remove(it)
        }


        // copy all the in-memory values to the "original" config, but SKIP the ones that are overloaded (and unchanged).
        // this is a tad slow, but makes sure that our saves do not include properties that have been overloaded
        if (trackedConfigProperties.isNotEmpty()) {
            possiblyOverloadedConfigMap.entries.forEach { (key, value) ->
                if (!trackedConfigProperties.containsKey(key)) {
                    originalConfigMap[key]!!.set(value.get())
                }
            }
        }

        val configToString = moshi.toJson(originalConfig)
        configFile?.writeText(configToString, Charsets.UTF_8)
        return configToString
    }

    /**
     * process the arguments, and if applicable perform a `get` or `set` operation
     */
    fun process(arguments: Array<String>, onSaveAction: () -> Unit): List<String> {
        this.arguments.addAll(arguments)

        // now we have to see if there are any OVERLOADED properties
        manageOverloadProperties()
        manageGetAndSet(onSaveAction)

        // this contains a "cleaned" set of arguments that excludes overloaded args.
        return this.arguments
    }


    private fun createConfigMap(config: Any): Map<String, ConfigProp> {
        // this creates an EASY-to-use map of all arguments we have
        val argumentMap = mutableMapOf<String, ConfigProp>()

        val kClass = config::class
        require(kClass.hasAnnotation<JsonClass>()) { "Cannot parse configuraion if it is not annotated with @JsonClass"}

        // get all the members of this class.
        for (member in kClass.declaredMemberProperties) {
            @Suppress("UNCHECKED_CAST")
            assignFieldsToMap(argumentMap, member as KProperty<Any>, config, member.getter.call(config)!!, "", -1)
        }

        return argumentMap
    }

    // the class is treated as lowercase, but the VALUE of properties is treated as case-sensitive
    @OptIn(ExperimentalStdlibApi::class)
    private fun assignFieldsToMap(argMap: MutableMap<String, ConfigProp>, field: KProperty<Any>,
                                  parentObj: Any, obj: Any,
                                  prefix: String, index: Int = -1)

    {
        if (Modifier.isTransient(field.javaField!!.modifiers)) {
            // ignore transient fields!
            return
        }

        var jsonName = (field.findAnnotations<Json>().firstOrNull()?.name ?: field.name).lowercase(locale)
        jsonName = when {
            prefix.isEmpty() -> jsonName
            else             ->"$prefix.$jsonName".lowercase(locale)
        }

        if (index > -1) {
            jsonName += "[$index]"
        }

        val kClass = obj::class
        if (kClass.hasAnnotation<JsonClass>()) {
            // get all the members of this class.
            for (member in kClass.declaredMemberProperties) {
                @Suppress("UNCHECKED_CAST")
                assignFieldsToMap(argMap, member as KProperty<Any>, obj, member.getter.call(obj)!!, jsonName, -1)
            }
        } else {
            if (field.returnType.jvmErasure.isSubclassOf(List::class)) {
                // iterate over the collection, but assign the index with the name.
                @Suppress("UNCHECKED_CAST")
                val collection = obj as List<Any>
                collection.forEachIndexed { i, any ->
                    assignFieldsToMap(argMap, field, obj, any, prefix, i)
                }
            } else {
                // only add fields to map that are ones we care about
                @Suppress("UNCHECKED_CAST")
                argMap[jsonName] = ConfigProp(parentObj, field)
            }
        }
    }

    private fun logAndExit(message: String) {
        println(message)
        exitProcess(0)
    }
    private fun logAndExit(argProp: ConfigProp?) {
        if (argProp != null) {
            println(argProp.get().toString())
        } else {
            println()
        }
        exitProcess(0)
    }

    /**
     * Allows the ability to `get` or `set` configuration properties. Will call System.exit() if a get/set was done
     */
    private fun manageGetAndSet(onSaveAction: () -> Unit) {
        if ((arguments.isEmpty())) {
            // nothing to do
            return
        }

        val args = arguments.map { it.lowercase(locale) }
        val getIndex = args.indexOf("get")
        val setIndex = args.indexOf("set")

        if (getIndex > -1) {
            val propIndex = getIndex + 1
            if (propIndex > arguments.size - 1) {
                logAndExit("Must specify property to get. For Example: 'get server.ip'")
            }

            val propToQuery = arguments[propIndex]
            val prop = configMap[propToQuery]
            logAndExit(prop)
        } else if (setIndex > -1) {
            val propIndex = setIndex + 1
            if (propIndex > arguments.size - 1) {
                logAndExit("Must specify property to set. For Example: 'set server.ip 127.0.0.1'")
            }

            val valueIndex = setIndex + 2
            if (valueIndex > arguments.size - 1) {
                logAndExit("Must specify property value to set. For Example: 'set server.ip 127.0.0.1'")
            }

            val propToSet = arguments[propIndex]
            val valueToSet = arguments[valueIndex]

            val prop = configMap[propToSet]
            if (prop != null) {
                // we output the OLD value, in case we want it from the CLI
                val oldValue = prop.set(valueToSet)

                // we ALWAYS want to re-save this file back
                onSaveAction()

                logAndExit(oldValue.toString())
            } else {
                // prop wasn't found
                logAndExit("")
            }
        }

        return
    }

    /**
     * Overloaded properties can be of the form
     * For strings (and all other supported types)
     *    config.z="flag" (for a string)
     *    config.x="true" (for a boolean)
     *
     * Additional support for booleans
     *    config.x (absent any value is a 'true')
     */
    @Suppress("UNCHECKED_CAST", "DuplicatedCode")
    private fun manageOverloadProperties() {
        configMap.forEach { (arg, prop) ->
            val returnType = prop.member.returnType.jvmErasure

            if (prop.isSupported()) {
                ////
                // CLI CHECK IF PROPERTY EXIST  (explicit check for arg=value)
                // if arg is found, no more processing happens
                ////
                var foundArg = arguments.firstOrNull { it.startsWith("$arg=") }
                if (foundArg != null) {
                    // we know that split[0] == 'arg=' because we already checked for that
                    val splitValue = foundArg.split(regexEquals)[1].trim().getType(returnType)

                    // save this, so we can figure out what the values are on save (and not save overloaded properties, that are unchanged)
                    val get = prop.get()
                    if (get != splitValue) {
                        // only track it if it's different
                        trackedConfigProperties[arg] = get
                        originalOverloadedProperties[arg] = splitValue
                        prop.set(splitValue)
                    }

                    arguments.remove(arg)
                    return@forEach
                }

                ////
                // CLI CHECK IF PROPERTY EXIST  (check if 'arg' exists, and is a boolean)
                // if arg is found, no more processing happens
                ////
                if (returnType.isSubclassOf(Boolean::class)) {
                    // this is a boolean type? if present then we make it TRUE
                    foundArg = arguments.firstOrNull { it.startsWith(arg) }
                    if (foundArg != null) {
                        // save this, so we can figure out what the values are on save (and not save overloaded properties, that are unchanged)
                        val get = prop.get()
                        if (get != true) {
                            // only track it if it's different
                            trackedConfigProperties[arg] = get
                            originalOverloadedProperties[arg] = true
                            prop.set(true)
                        }

                        arguments.remove(arg)
                        return@forEach
                    }
                }


                ////
                // SYSTEM PROPERTY CHECK
                ////
                var sysProperty: String? = OS.getProperty(arg)?.trim()

                // try lowercase
                if (sysProperty.isNullOrEmpty()) {
                    sysProperty = OS.getProperty(arg.lowercase(Locale.getDefault()))?.trim()
                }
                // try uppercase
                if (sysProperty.isNullOrEmpty()) {
                    sysProperty = OS.getProperty(arg.uppercase(Locale.getDefault()))?.trim()
                }

                if (!sysProperty.isNullOrEmpty()) {
                    val splitValue = sysProperty.getType(returnType)

                    val get = prop.get()
                    if (get != splitValue) {
                        // only track it if it's different
                        trackedConfigProperties[arg] = get
                        originalOverloadedProperties[arg] = splitValue
                        prop.set(splitValue)
                    }

                    return@forEach
                }


                ////
                // ENVIRONMENT VARIABLE CHECK
                ////
                var envProperty = OS.getEnv(environmentVarPrefix + arg)?.trim()

                // try lowercase
                if (envProperty.isNullOrEmpty()) {
                    envProperty = OS.getEnv(environmentVarPrefix + arg.lowercase(Locale.getDefault()))?.trim()
                }
                // try uppercase
                if (envProperty.isNullOrEmpty()) {
                    envProperty = OS.getEnv(environmentVarPrefix + arg.uppercase(Locale.getDefault()))?.trim()
                }

                if (!envProperty.isNullOrEmpty()) {
                    val get = prop.get()
                    if (get != envProperty) {
                        // only track it if it's different
                        trackedConfigProperties[arg] = get
                        originalOverloadedProperties[arg] = envProperty
                        prop.set(envProperty)
                    }

                    return@forEach
                }
            } else {
               logger.error("${prop.member.name} (${returnType.javaObjectType.simpleName}) overloading is not supported. Ignoring")
            }
        }
    }

    private fun String.getType(propertyType: Any): Any {
        return when (propertyType) {
            Boolean::class -> this.toBoolean()
            Byte::class -> this.toByte()
            Char::class -> this[0]
            Double::class -> this.toDouble()
            Float::class -> this.toFloat()
            Int::class -> this.toInt()
            Long::class -> this.toLong()
            Short::class -> this.toShort()
            else -> this
        }
    }
}
