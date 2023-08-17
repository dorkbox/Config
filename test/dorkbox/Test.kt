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
package dorkbox

import dorkbox.config.ConfigProcessor
import dorkbox.json.annotation.Json
import dorkbox.os.OS
import org.junit.Assert
import org.junit.Test
import org.slf4j.LoggerFactory


class Test {

    @Test
    fun arrayTest() {
        val conf = ArrayConf()

        Assert.assertArrayEquals(arrayOf(1, 2, 3, 4), conf.ips)

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[0]=7"))
            .process()

        Assert.assertArrayEquals(arrayOf(7, 2, 3, 4), conf.ips)

        config.cliArguments(arrayOf("ips[4]=7"))
              .process()

        Assert.assertArrayEquals(arrayOf(7, 2, 3, 4, 7), conf.ips)
    }

    @Test
    fun charArrayTest() {
        val conf = CharArrayConf()

        Assert.assertArrayEquals(arrayOf('1', '2', '3', '4'), conf.ips)

        ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[4]=7", "ips[7]=9"))
            .process()

        Assert.assertArrayEquals(arrayOf('1', '2', '3', '4', '7', Character.MIN_VALUE, Character.MIN_VALUE, '9'), conf.ips)
    }

    @Test
    fun listTest() {
        val conf = ListConf()

        Assert.assertEquals(listOf(1, 2, 3, 4), conf.ips)

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[0]=7"))
            .process()

        Assert.assertEquals(listOf(7, 2, 3, 4), conf.ips)

        config.cliArguments(arrayOf("ips[4]=7"))
            .process()

        Assert.assertEquals(listOf(7, 2, 3, 4, 7), conf.ips)
    }

    @Test
    fun cliTest() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ip_address=1.2.3.4"))
            .process()


        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)

        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.json())
    }

    @Test
    fun sysPropTest() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        OS.setProperty("server", "true")
        try {
            val config = ConfigProcessor(conf)
                .envPrefix("")
                .cliArguments(arrayOf("ip_address=11.12.13.14"))
                .process()


            Assert.assertTrue(conf.ip == "11.12.13.14")
            Assert.assertTrue(conf.server)
            Assert.assertFalse(conf.client)

            Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
            Assert.assertEquals("{\"ip_address\":\"11.12.13.14\",\"server\":true,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.json())
        }
        finally {
            OS.clearProperty("server")
        }
    }

    @Test
    fun updateTest() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ip_address=1.2.3.4", "nested[0].iceCream"))
            .process()


        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)


        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())


        config.loadAndProcess("{ip_address:0.0.0.0,server:true,client:true}")

        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertTrue(conf.server)
        Assert.assertTrue(conf.client)

        Assert.assertEquals("{\"ip_address\":\"0.0.0.0\",\"server\":true,\"client\":true,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":true,\"client\":true,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())
    }


    @Test
    fun update2Test() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ip_address=1.2.3.4", "nested[0].iceCream"))
            .process()


        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)


        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())


        // since we did not PROCESS the cli arguments again -- it means that the original has not been overloaded. To consider overloading stuff, process() must be called
        config.load("{ip_address:0.0.0.0,server:true,client:true}")

        Assert.assertTrue(conf.ip == "0.0.0.0")
        Assert.assertTrue(conf.server)
        Assert.assertTrue(conf.client)
        Assert.assertFalse(conf.nested[0].iceCream)

        Assert.assertEquals("{\"ip_address\":\"0.0.0.0\",\"server\":true,\"client\":true,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"0.0.0.0\",\"server\":true,\"client\":true,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.json())
    }

    @Test
    fun updateInvalidTest() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ip_address=1.2.3.4", "nested[0].iceCream"))
            .process()


        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)


        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())


        // on purpose, there is a field here that doesn't exist
        config.logger = LoggerFactory.getLogger("test")
        config.loadAndProcess("{does_not_exit:0}")

        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)

        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())
    }

    @Test
    fun updateInvalid2Test() {
        val conf = Conf()

        Assert.assertTrue(conf.ip == "127.0.0.1")

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ip_address=1.2.3.4", "nested[0].iceCream"))
            .process()


        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)


        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())


        // on purpose, there is a field here that doesn't exist
        config.json.exceptionOnMissingFields = false
        config.logger = LoggerFactory.getLogger("test")
        config.loadAndProcess("{does_not_exit:0}")

        // the "default" object should be loaded!!! (but the override should still take effect)

        Assert.assertTrue(conf.ip == "1.2.3.4")
        Assert.assertFalse(conf.server)
        Assert.assertFalse(conf.client)
        Assert.assertTrue(conf.nested[0].iceCream)

        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true}]}", config.originalJson())
        Assert.assertEquals("{\"ip_address\":\"1.2.3.4\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":true,\"potatoes\":true}]}", config.json())
    }

    @Test
    fun updateArrayTest() {
        val conf = ArrayConf()

        Assert.assertArrayEquals(arrayOf(1, 2, 3, 4), conf.ips)

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[0]=7"))
            .process()

        // since we did not PROCESS the cli arguments again -- it means that the original has not been overloaded. To consider overloading stuff, process() must be called
        config.load("""
            {"ips":[1,2,3,4,5,6,7,]}
        """.trimIndent())

        Assert.assertArrayEquals(arrayOf(1,2,3,4,5,6,7), conf.ips)

        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.originalJson())
        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.json())

        config.process() // overrides ips[0] -> 7
        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.originalJson())
        Assert.assertEquals("{\"ips\":[7,2,3,4,5,6,7]}", config.json())
    }

    @Test
    fun updateArrayCliTest() {
        val conf = ArrayConf()

        Assert.assertArrayEquals(arrayOf(1, 2, 3, 4), conf.ips)

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[7]=7"))
            .process()

        Assert.assertArrayEquals(arrayOf(1,2,3,4,0,0,0,7), conf.ips)

        Assert.assertEquals("{\"ips\":[1,2,3,4]}", config.originalJson())
        Assert.assertEquals("{\"ips\":[1,2,3,4,0,0,0,7]}", config.json())
    }

    @Test
    fun updateArraySysTest() {
        val conf = ArrayConf()

        Assert.assertArrayEquals(arrayOf(1, 2, 3, 4), conf.ips)

        OS.setProperty("ips[7]", "7")
        try {
            val config = ConfigProcessor(conf)
                .envPrefix("")
                .process()

            Assert.assertArrayEquals(arrayOf(1,2,3,4,0,0,0,7), conf.ips)

            Assert.assertEquals("{\"ips\":[1,2,3,4]}", config.originalJson())
            Assert.assertEquals("{\"ips\":[1,2,3,4,0,0,0,7]}", config.json())
        }
        finally {
            OS.clearProperty("ips[7]")
        }
    }

    @Test
    fun updateArrayEmptyTest() {
        val conf = ArrayEmptyConf()

        Assert.assertArrayEquals(arrayOf<Int>(), conf.ips)

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("ips[0]=7"))
            .process()

        // since we did not PROCESS the cli arguments again -- it means that the original has not been overloaded. To consider overloading stuff, process() must be called
        config.load("""
            {"ips":[1,2,3,4,5,6,7,]}
        """.trimIndent())

        Assert.assertArrayEquals(arrayOf(1,2,3,4,5,6,7), conf.ips)

        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.originalJson())
        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.json())

        config.process()
        Assert.assertEquals("{\"ips\":[1,2,3,4,5,6,7]}", config.originalJson())
        Assert.assertEquals("{\"ips\":[7,2,3,4,5,6,7]}", config.json())
    }

    @Test
    fun updateArrayListTest() {
        val conf = Conf()

        val config = ConfigProcessor(conf)
            .envPrefix("")
            .cliArguments(arrayOf("nested[0].iceCream=true"))
            .process()

        // since we did not PROCESS the cli arguments again -- it means that the original has not been overloaded. To consider overloading stuff, process() must be called
        config.load("""
           {"ip_address":"127.0.0.1","server":false,"client":false,"nested":[{"iceCream":false,"potatoes":true},{"iceCream":true,"potatoes":true},{"iceCream":false,"potatoes":false}]}
        """.trimIndent())

        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true},{\"iceCream\":true,\"potatoes\":true},{\"iceCream\":false,\"potatoes\":false}]}", config.originalJson())

        Assert.assertEquals("{\"ip_address\":\"127.0.0.1\",\"server\":false,\"client\":false,\"nested\":[{\"iceCream\":false,\"potatoes\":true},{\"iceCream\":true,\"potatoes\":true},{\"iceCream\":false,\"potatoes\":false}]}", config.json())
    }

    class ListConf {
        var ips = mutableListOf(1, 2, 3, 4)
    }

    class ArrayConf {
        var ips = arrayOf(1, 2, 3, 4)
    }

    class ArrayEmptyConf {
        var ips = arrayOf<Int>()
    }

    class CharArrayConf {
        var ips = arrayOf('1', '2', '3', '4')
    }

    class Conf {
        @Json("ip_address")
        var ip = "127.0.0.1"

        var server = false
        var client = false

        var nested = mutableListOf(NestedConf())
    }

    class NestedConf {
        var iceCream = false
        var potatoes = true
    }
}


