/*
 * Copyright 2020 dorkbox, llc
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

import java.time.Instant

///////////////////////////////
//////    PUBLISH TO SONATYPE / MAVEN CENTRAL
////// TESTING : (to local maven repo) <'publish and release' - 'publishToMavenLocal'>
////// RELEASE : (to sonatype/maven central), <'publish and release' - 'publishToSonatypeAndRelease'>
///////////////////////////////

gradle.startParameter.showStacktrace = ShowStacktrace.ALWAYS   // always show the stacktrace!


plugins {
    id("com.dorkbox.GradleUtils") version "3.17"
    id("com.dorkbox.Licensing") version "2.24"
    id("com.dorkbox.VersionUpdate") version "2.8"
    id("com.dorkbox.GradlePublish") version "1.18"

    kotlin("jvm") version "1.8.0"
}

object Extras {
    // set for the project
    const val description = "Configuration properties that can be defined via the CLI, system properties, environment variables, or file."
    const val group = "com.dorkbox"
    const val version = "1.9"

    // set as project.ext
    const val name = "Config"
    const val id = "Config" // this is the maven ID!
    const val vendor = "Dorkbox LLC"
    const val vendorUrl = "https://dorkbox.com"
    const val url = "https://git.dorkbox.com/dorkbox/Config"

    val buildDate = Instant.now().toString()
}

///////////////////////////////
/////  assign 'Extras'
///////////////////////////////
GradleUtils.load("$projectDir/../../gradle.properties", Extras)
GradleUtils.defaults()
GradleUtils.compileConfiguration(JavaVersion.VERSION_1_8) {
    // see: https://kotlinlang.org/docs/reference/using-gradle.html
    freeCompilerArgs = listOf(
        // enable the use of experimental methods
        "-Xopt-in=kotlin.RequiresOptIn"
    )
}
//GradleUtils.jpms(JavaVersion.VERSION_1_9)// moshi doesn't support JPMS yet. See: https://github.com/square/moshi/issues/1160


licensing {
    license(License.APACHE_2) {
        description(Extras.description)
        author(Extras.vendor)
        url(Extras.url)
    }
}

tasks.jar.get().apply {
    manifest {
        // https://docs.oracle.com/javase/tutorial/deployment/jar/packageman.html
        attributes["Name"] = Extras.name

        attributes["Specification-Title"] = Extras.name
        attributes["Specification-Version"] = Extras.version
        attributes["Specification-Vendor"] = Extras.vendor

        attributes["Implementation-Title"] = "${Extras.group}.${Extras.id}"
        attributes["Implementation-Version"] = Extras.buildDate
        attributes["Implementation-Vendor"] = Extras.vendor
    }
}

dependencies {
    api("com.dorkbox:Updates:1.1")
    api("com.dorkbox:OS:1.6")

    val moshiVer = "1.15.0"

    // For JSON serialization
    compileOnly("com.squareup.moshi:moshi:$moshiVer")
    compileOnly("com.squareup.moshi:moshi-kotlin:$moshiVer")

    // https://github.com/MicroUtils/kotlin-logging
    api("io.github.microutils:kotlin-logging:2.1.21")
    api("org.slf4j:slf4j-api:1.8.0-beta4")
}

publishToSonatype {
    groupId = Extras.group
    artifactId = Extras.id
    version = Extras.version

    name = Extras.name
    description = Extras.description
    url = Extras.url

    vendor = Extras.vendor
    vendorUrl = Extras.vendorUrl

    issueManagement {
        url = "${Extras.url}/issues"
        nickname = "Gitea Issues"
    }

    developer {
        id = "dorkbox"
        name = Extras.vendor
        email = "email@dorkbox.com"
    }
}
