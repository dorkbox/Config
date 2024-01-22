Configuration properties that can be defined via the CLI, system properties, environment variables, or file.

###### [![Dorkbox](https://badge.dorkbox.com/dorkbox.svg "Dorkbox")](https://git.dorkbox.com/dorkbox/Config) [![Github](https://badge.dorkbox.com/github.svg "Github")](https://github.com/dorkbox/Config) [![Gitlab](https://badge.dorkbox.com/gitlab.svg "Gitlab")](https://gitlab.com/dorkbox/Config)


This project provides configuration properties, such that ANYTHING in the config file can ALSO be passed in on the command line or as an env/system property

```
commandline > system property > environment variable > properties file
```
Once a property has been defined, it cannot be overloaded again via a different method, as specified in the above hierarchy,
 
During a save operation, overloaded values will be ignored unless they were manually changed to something different


Additionally, it is possible to set an environment variable names that might conflict with the standard set of names, for example,
 ```
 PATH
 ```
* The system uses PATH, but if we want to use path for something different, we can via setting the prefix.
* For example, setting the prefix to `CONFIG__` means that for us to set the `path` property, we set it via

```
CONFIG__path="/home/blah/whatever"
```
 *  And this way, **OUR** path does not conflict with the system path.
 

Maven Info
---------
```
<dependencies>
    ...
    <dependency>
      <groupId>com.dorkbox</groupId>
      <artifactId>Config</artifactId>
      <version>2.9.1</version>
    </dependency>
</dependencies>
```

Gradle Info
---------
```
dependencies {
    ...
    implementation("com.dorkbox:Config:2.9.1")
}
```

License
---------
This project is © 2022 dorkbox llc, and is distributed under the terms of the Apache v2.0 License. See file "LICENSE" for further 
references.
