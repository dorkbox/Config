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

import dorkbox.json.Alias
import dorkbox.json.Json
import dorkbox.json.Json.Companion.isBoolean
import dorkbox.json.Json.Companion.isByte
import dorkbox.json.Json.Companion.isChar
import dorkbox.json.Json.Companion.isDouble
import dorkbox.json.Json.Companion.isFloat
import dorkbox.json.Json.Companion.isInt
import dorkbox.json.Json.Companion.isLong
import dorkbox.json.Json.Companion.isShort
import dorkbox.json.Json.Companion.isString
import dorkbox.json.OutputType
import dorkbox.os.OS
import org.slf4j.LoggerFactory
import java.io.File
import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.jvmErasure
import kotlin.system.exitProcess

@Suppress("MemberVisibilityCanBePrivate", "unused")
/**
 * We want to support configuration properties, such that ANYTHING in the config file can ALSO be passed in on the command line or as an env/system property
 *
 * ```
 * overloaded: commandline > system property > environment variable
 *
 * baseline: default object or json string or json file
 * ```
 *
 * If the json file AND string are specified, the string will be used for loading, and the file used for saving.
 *
 * Once a property has been defined, it cannot be overloaded again via a different method, as specified in the above hierarchy,
 *
 * During a save operation, overloaded values (via CLI/ENV) will be ignored unless they were manually changed to something
 * different (in the application)
 *
 **********************************************
 * SYSTEM PROPERTIES AND ENVIRONMENT VARIABLES
 **********************************************
 * For setting configuration properties via system properties or environment variables.... it is possible that a standard system
 * property or environment variable name might conflict...
 *
 * (In this example, "PATH" environment variable)
 * The system uses PATH, but if we want to use path for something different, we can via setting the prefix.
 * For example, setting the prefix to "CONFIG__" means that for us to set the `path` property, we set it via
 *
 *```
 *  CONFIG__path="/home/blah/whatever"
 *```
 *  And this way, **OUR** configuration path name does not conflict with the system path.
 *
 ***********************************
 * COMMAND LINE INTERFACE ARGUMENTS
 ***********************************
 * When specifying a CLI property,
 *  - if it is a boolean value, the existence of the value means "true"
 *    - <'do_a_thing'>
 *        - overrides the 'do_a_thing' to be "true"
 *  - if it is any other type, you must specify the property name + value
 *    - <'server_ip=1.2.3.4'>
 *        - overrides the 'server_ip' to the string value of '1.2.3.4'
 *    - <'do_a_thing=true'>
 *        - overrides the 'do_a_thing' to be "true"
 *
 *
 * Additionally, there is the ability to `get` or `set` configuration properties via the CLI, which will appropriately
 *  - <'get' 'property'>
 *    - get a value following the override/default rules
 *    - print the value to the console output
 *    - exit the application
 *
 *  - <'set' 'property' 'value'>
 *    - set a value overriding what the system properties/environment properties, string, or file.
 *    - print the old value to the console output
 *    - save() the new change to file (if possible)
 *    - exit the application
 *
 * This is very useful when wanting to set or retrieve properties from the commandline
 */
class ConfigProcessor<T : Any>


/**
 *  Specify the object that will be modified based on:
 *  - JSON string
 *  - JSON file
 *  - Command Line arguments
 *  - System properties
 *  - Environment variablse
 */
(private val configObject: T) {

    companion object {
        private val regexEquals = "=".toRegex()
        private val locale = Locale.getDefault()

        /**
         * Gets the version number.
         */
        const val version = "2.1"

        init {
            // Add this project to the updates system, which verifies this class + UUID + version information
            dorkbox.updates.Updates.add(ConfigProcessor::class.java, "23475d7cdfef4c1e9c38c310420086ca", version)
        }

        private fun <T: Any> createConfigMap(config: T, objectType: KClass<T>): Map<String, ConfigProp> {
            // this creates an EASY-to-use map of all arguments we have
            val argumentMap = mutableMapOf<String, ConfigProp>()

            // get all the members of this class.
            for (member in objectType.declaredMemberProperties) {
                @Suppress("UNCHECKED_CAST")
                assignFieldsToMap(argumentMap, member as KProperty<Any>, config, member.getter.call(config)!!, "", -1)
            }

            return argumentMap
        }

        // the class is treated as lowercase, but the VALUE of properties is treated as case-sensitive
        private fun assignFieldsToMap(argMap: MutableMap<String, ConfigProp>, field: KProperty<Any>,
                                      parentObj: Any, obj: Any,
                                      prefix: String, index: Int = -1)

        {
            val javaField = field.javaField!!
            if (Modifier.isTransient(javaField.modifiers)) {
                // ignore transient fields!
                return
            }

            val annotation = field.javaField?.annotations?.filterIsInstance<dorkbox.json.annotation.Json>()?.firstOrNull()
            if (annotation?.ignore == true) {
                return
            }

            var jsonName = (annotation?.name ?: field.name)
            jsonName = when {
                prefix.isEmpty() -> jsonName
                else             ->"$prefix.$jsonName"
            }

            if (index > -1) {
                jsonName += "[$index]"
            }

            val type = obj::class.java

            if (isString(type) ||
                isInt(type) ||
                isBoolean(type) ||
                isFloat(type) ||
                isLong(type) ||
                isDouble(type) ||
                isShort(type) ||
                isByte(type) ||
                isChar(type) ||

                Enum::class.java.isAssignableFrom(type) ||
                annotation != null) // if there is an annotation on the field, we add it as the object!
            {

                argMap[jsonName] = ConfigProp(parentObj, field)
            } else if (type.isArray) {
                // iterate over the array, but assign the index with the name.
                @Suppress("UNCHECKED_CAST")
                val collection = obj as Array<Any>
                collection.forEachIndexed { i, any ->
                    assignFieldsToMap(
                        argMap = argMap,
                        field = field,
                        parentObj = obj,
                        obj = any,
                        prefix = prefix,
                        index = i)
                }
            } else if (Collection::class.java.isAssignableFrom(type)) {
                // iterate over the collection, but assign the index with the name.
                @Suppress("UNCHECKED_CAST")
                val collection = obj as Collection<Any>
                collection.forEachIndexed { i, any ->
                    assignFieldsToMap(
                        argMap = argMap,
                        field = field,
                        parentObj = obj,
                        obj = any,
                        prefix = prefix,
                        index = i)
                }
            } else {
                val kClass = obj::class
                // get all the members of this class.
                for (member in kClass.declaredMemberProperties) {
                    if (member.visibility != KVisibility.PRIVATE) {
                        @Suppress("UNCHECKED_CAST")
                        assignFieldsToMap(
                            argMap = argMap,
                            field = member as KProperty<Any>,
                            parentObj = obj,
                            obj = member.getter.call(obj)!!,
                            prefix = jsonName,
                            index = -1
                        )
                    }
                }
            }
        }

        private fun consoleLog(message: String) {
            println(message)

        }
        private fun consoleLog(argProp: ConfigProp?) {
            if (argProp != null) {
                println(argProp.get().toString())
            } else {
                println()
            }
        }
    }




    /**
     * This is exposed in order to customize JSON parsing/writing behavior
     */
    val json = Json()

    @Suppress("UNCHECKED_CAST")
    private val objectType: KClass<T> = configObject::class as KClass<T>

    // these are the defaults if nothing is set!
    private var environmentVarPrefix = ""
    private var commandLineArguments = Array(0) { "" }
    private var configString: String? = null
    private var saveFile: File? = null
    private var configFile: File? = null
    private var saveLogic: () -> Unit = { savePretty() }


    /**
     * The cleaned CLI arguments which no longer have elements used to modify configuration properties
     */
    var arguments: List<String> = mutableListOf()

    private val config: T = configObject

    private var processed = false

    /**
     * this creates an EASY-to-use map of all arguments we have
     *
     * our configMap will ALWAYS modify the default (or rather, incoming object)!!
     */
    private val configMap: Map<String, ConfigProp> = createConfigMap(configObject, objectType)

    // DEEP COPY of the original values from the file, so when saving, we know what the
    // overridden values are and can skip saving them
    // NOTE: overridden values are set via CLI/Sys/Env!!
    private lateinit var configCopy: T
    private lateinit var configMapCopy: Map<String, ConfigProp>


    init {
        // we want to default to the standard JSON output format
        json.outputType = OutputType.json
    }

    /**
     * Registers a serializer to use for the specified type instead of the default behavior of serializing all of an objects
     * fields.
     */
    @Synchronized
    fun <T2: Any> setSerializer(type: Class<T2>, serializer: dorkbox.json.JsonSerializer<T2>): ConfigProcessor<T> {
        json.setSerializer(type, serializer)
        return this
    }

    /**
     * Specify the environment variable prefix, for customizing how system properties or environment variables are parsed.
     *
     * The system properties and environment variables are augmented by the "environmentVarPrefix" string.
     */
    @Synchronized
    fun envPrefix(environmentVarPrefix: String): ConfigProcessor<T> {
        this.environmentVarPrefix = environmentVarPrefix
        return this
    }

    /**
     * Specify the command line arguments, if desired, to be used to overlaod
     */
    @Synchronized
    fun cliArguments(commandLineArguments: Array<String>): ConfigProcessor<T> {
        this.commandLineArguments = commandLineArguments
        return this
    }

    /**
     * Specify the file to save to (useful in the case where the file saved to and the file loaded from are different)
     */
    @Synchronized
    fun saveFile(saveFile: File?): ConfigProcessor<T> {
        this.saveFile = saveFile
        return this
    }


    /**
     * Specify the save logic. By default, will attempt to save to the saveFile (if available), then will attempt
     * to save to the configFile (if available).
     */
    @Synchronized
    fun saveFile(save: () -> Unit): ConfigProcessor<T> {
        this.saveLogic = save
        return this
    }

    /**
     * Specify the baseline data (as a JSON string) used to populate the values of the config object.
     *
     * If the specified string DOES NOT load, then it will not be used or processed!
     */
    @Synchronized
    fun loadAndProcess(configString: String): ConfigProcessor<T> {
        return if (load(configString)) {
            process()
        } else {
            this
        }
    }

    /**
     * Specify the baseline data (as a JSON file) used to populate the values of the config object.
     *
     * If the specified file DOES NOT load, then it will not be used or processed!
     */
    @Synchronized
    fun loadAndProcess(configFile: File): ConfigProcessor<T> {
        return if (load(configFile)) {
            process()
        } else {
            this
        }
    }

    /**
     * Specify the baseline data (as a JSON string) used to populate the values of the config object.
     *
     * If the specified string DOES NOT load, then it will not be used!
     */
    @Synchronized
    fun load(configString: String): Boolean {
        val configObject =
            try {
                if (configString.isNotBlank()) {
                    json.fromJson(objectType.java, configString)
                } else {
                    null
                }
            } catch (ignored: Exception) {
                // there was a problem parsing the config
                null
            }

        if (configObject == null) {
            return false
        }

        this.configString = configString
        load(configObject)

        return true
    }

    /**
     * Specify the baseline data (as a JSON file) used to populate the values of the config object.
     *
     * If the specified file DOES NOT load, then it will not be used!
     */
    @Synchronized
    fun load(configFile: File): Boolean {
        val configObject =
            if (configFile.canRead()) {
                // get the text from the file
                val fileContents = configFile.readText(Charsets.UTF_8)
                if (fileContents.isNotEmpty()) {
                    try {
                        json.fromJson(objectType.java, fileContents)
                    }
                    catch (ignored: Exception) {
                        // there was a problem parsing the config
                        null
                    }
                }
                else {
                    null
                }
            }
            else {
                null
            }

        if (configObject == null) {
            return false
        }

        this.configFile = configFile
        load(configObject)

        return true
    }


    @Synchronized
    private fun load(configObject: T) {
        // we must make sure to modify the ORIGINAL object (which has already been set)
        // if we are invoked multiple times, then we might "undo" the overloaded value, but then "redo" it later.
        val incomingDataConfigMap = createConfigMap(configObject, objectType)
        incomingDataConfigMap.forEach { (k,v) ->
            configMap[k]!!.set(v.get())
        }
    }

    /**
     * Processes and populates the values of the config object, if any
     */
    @Synchronized
    fun process(): ConfigProcessor<T> {
        if (!processed) {
            processed = true
            // only do this once
            configCopy = json.fromJson(objectType.java, json.toJson(configObject))!!
            configMapCopy = createConfigMap(configCopy, objectType)
        }

        // when processing data, the FILE, if present, is always the FIRST to load.
        configFile?.also { load(it) }

        // the string will be used to MODIFY what was set by the FILE (if present),
        configString?.also { load(it) }



        // this permits a bash/batch/CLI invocation of "get xyz" or "set xyz" so we can be interactive with the CLI
        // if there is no get/set CLI argument, then this does nothing.
        if (manageGetAndSet()) {
            exitProcess(0)
        }

        // always save the config!
        saveLogic()


        // now we have to see if there are any OVERLOADED properties


        // when we are processing the command line arguments, we can be processing them MULTIPLE times, for example if
        // we initially load data from a file, then we load data from a remote server
        val arguments = commandLineArguments.toMutableList()

        /**
         * Overloaded properties can be of the form
         * For strings (and all other supported types)
         *    config.z="flag" (for a string)
         *    config.x="true" (for a boolean)
         *
         * Additional support for booleans
         *    config.x (absent any value is a 'true')
         */
        configMap.forEach { (arg, prop) ->
            val returnType = prop.member.returnType.jvmErasure

            if (prop.isSupported()) {
                ////
                // CLI CHECK IF PROPERTY EXIST  (explicit check for arg=value)
                // if arg is found, no more processing happens
                ////
                var foundArg = commandLineArguments.firstOrNull { it.startsWith("$arg=") }
                if (foundArg != null) {
                    // we know that split[0] == 'arg=' because we already checked for that
                    val overriddenValue = foundArg.split(regexEquals)[1].trim().getType(returnType)

                    // This will only be saved if different, so we can figure out what the values are on save (and not save
                    // overridden properties, that are unchanged)
                    prop.setOverload(overriddenValue)

                    arguments.remove(foundArg)
                    return@forEach
                }

                ////
                // CLI CHECK IF PROPERTY EXIST  (check if 'arg' exists, and is a boolean)
                // if arg is found, no more processing happens
                ////
                if (returnType.isSubclassOf(Boolean::class)) {
                    // this is a boolean type? if present then we make it TRUE
                    foundArg = commandLineArguments.firstOrNull { it.startsWith(arg) }
                    if (foundArg != null) {

                        // This will only be saved if different, so we can figure out what the values are on save (and not save
                        // overridden properties, that are unchanged)
                        prop.setOverload(true)

                        arguments.remove(foundArg)
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
                    val overriddenValue = sysProperty.getType(returnType)

                    // This will only be saved if different, so we can figure out what the values are on save (and not save
                    // overridden properties, that are unchanged)
                    prop.setOverload(overriddenValue)

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
                    // This will only be saved if different, so we can figure out what the values are on save (and not save
                    // overridden properties, that are unchanged)
                    prop.setOverload(envProperty)

                    return@forEach
                }
            } else {
                LoggerFactory.getLogger(ConfigProcessor::class.java).error("${prop.member.name} (${returnType.javaObjectType.simpleName}) overloading is not supported. Ignoring")
            }
        }

        // we also will save out the processed arguments, so if we want to use a "cleaned" list of CLI arguments, we can
        this.arguments = arguments

        return this
    }

    /**
    * @return the JSON string representing the ORIGINAL configuration (does not include overridden properties)
    */
    @Synchronized
    fun originalJson(): String {
        // we use configCopy to save the state of everything as a snapshot (and then we serialize it)
        configMap.forEach { (k,v) ->
            if (!v.override) {
                // this will change what the "original copy" is recorded as having.
                configMapCopy[k]!!.set(v.get())
            }
        }

        return json.toJson(configCopy)
    }

    /**
     * @return the JSON string representing this configuration INCLUDING the overridden properties
     */
    @Synchronized
    fun json(): String {
        return json.toJson(config)
    }

    /**
     * Saves the baseline (ie: not the overridden values) JSON representation of this configuration to file, if possible
     *
     * @return the JSON string representing the ORIGINAL configuration (does not include overridden properties)
     */
    @Synchronized
    fun save(): String {
        val configToString = originalJson()

        saveFile ?: configFile ?.writeText(configToString, Charsets.UTF_8)
        return configToString
    }

    /**
     * Saves the baseline (ie: not the overridden values) JSON representation of this configuration to file, if possible
     *
     * @return the JSON string representing the ORIGINAL configuration (does not include overridden properties)
     */
    @Synchronized
    fun savePretty(): String {
        val configToString = json.prettyPrint(originalJson())

        saveFile ?: configFile ?.writeText(configToString, Charsets.UTF_8)
        return configToString
    }



    /**
     * Allows the ability to `get` or `set` configuration properties. Will call System.exit() if a get/set was done
     *
     * @return false if nothing is to be done, true if the system should exit
     */
    private fun manageGetAndSet(): Boolean {
        if ((commandLineArguments.isEmpty())) {
            // nothing to do
            return false
        }

        val args = commandLineArguments.map { it.lowercase(locale) }
        val getIndex = args.indexOf("get")
        val setIndex = args.indexOf("set")

        if (getIndex > -1) {
            val propIndex = getIndex + 1
            if (propIndex > commandLineArguments.size - 1) {
                consoleLog("Must specify property to get. For Example: 'get server.ip'")
                return true
            }

            val propToQuery = commandLineArguments[propIndex]
            val prop = configMap[propToQuery]
            consoleLog(prop)
            return true
        } else if (setIndex > -1) {
            val propIndex = setIndex + 1
            if (propIndex > commandLineArguments.size - 1) {
                consoleLog("Must specify property to set. For Example: 'set server.ip 127.0.0.1'")
                return true
            }

            val valueIndex = setIndex + 2
            if (valueIndex > commandLineArguments.size - 1) {
                consoleLog("Must specify property value to set. For Example: 'set server.ip 127.0.0.1'")
                return true
            }

            val propToSet = commandLineArguments[propIndex]
            val valueToSet = commandLineArguments[valueIndex]

            val prop = configMap[propToSet]
            if (prop != null) {
                // we output the OLD value, in case we want it from the CLI
                val oldValue = prop.get()

                prop.set(valueToSet)

                // we ALWAYS want to re-save this file back
                save()

                consoleLog(oldValue.toString())
                return true
            } else {
                // prop wasn't found
                consoleLog("")
                return true
            }
        }

        // no get/set found
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (other !is ConfigProcessor<*>) return false
        if (configMap.size != other.configMap.size) return false

        configMap.forEach { (k,v) ->
            if (v != other.configMap[k]) {
                return false
            }
        }

        // test maps in both directions
        other.configMap.forEach { (k,v) ->
            if (v != configMap[k]) {
                return false
            }
        }

        return true
    }

    /**
     * This is extremely slow, it is not recommended using this
     */
    override fun hashCode(): Int {
        return json().hashCode()
    }

    /**
     *  this returns the JSON string **WITH** overridden values!
     */
    override fun toString(): String {
        return json()
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
