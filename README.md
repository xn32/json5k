# json5k
[![Build](https://github.com/xn32/json5k/actions/workflows/build.yml/badge.svg)](https://github.com/xn32/json5k/actions/workflows/build.yml)

This is an experimental version of a [JSON5](https://json5.org/) binding for the [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) framework.

It is currently limited to the JVM and supports the following key features:
- Compliance with [v1.0.0](https://spec.json5.org/1.0.0/) of the JSON5 specification
- Rejection of duplicate keys during the deserialization of JSON5 objects
- Polymorphic types and configurable class discriminator names
- Carefully constructed error messages for deserialization errors
- Serialization of comments for class properties

Unit tests for the most important application scenarios exist, but the framework has not been deployed to production yet. In addition, benchmarking and performance optimization are still to be done.

Bug reports and other feedback are highly welcome, for example via the [issue tracker](https://github.com/xn32/json5k/issues) on GitHub.

## Setup instructions

This repository contains a Gradle setup that compiles the binding into a single Java library. Use this library according to your needs.

For evaluation purposes, the easiest solution might be to install it to your local Maven repository:
```bash
./gradlew publishToMavenLocal
```

Afterwards, use it from `build.gradle.kts` as follows:
```kotlin
plugins {
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.serialization") version "1.7.10"
}

repositories {
    mavenCentral()
    mavenLocal {
        content {
            includeGroup("io.github.xn32")
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.4.0")
    implementation("io.github.xn32:json5k:0.2.0-SNAPSHOT")
}
```

However, keep the [limitations](https://docs.gradle.org/7.5/userguide/declaring_repositories.html#sub:maven_local) of the local Maven repository in mind.

## Usage examples

### Non-hierarchical values
```kotlin
import io.github.xn32.json5k.Json5
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

// Serialization:
Json5.encodeToString(5142) // 5142
Json5.encodeToString(listOf(4.5, 1.5e2, 1.2e15)) // [4.5,150.0,1.2E15]
Json5.encodeToString(mapOf("a" to 10, "b" to 20)) // {a:10,b:20}
Json5.encodeToString(Double.NEGATIVE_INFINITY) // -Infinity
Json5.encodeToString<Int?>(null) // null

// Deserialization:
Json5.decodeFromString<Int?>("113") // 113
Json5.decodeFromString<List<Double>>("[1.2, .4]") // [1.2, 0.4]
Json5.decodeFromString<Map<String, Int>>("{ a: 10, 'b': 20, }") // {a=10, b=20}
Json5.decodeFromString<Double>("+Infinity") // Infinity
Json5.decodeFromString<Int?>("null") // null

// Deserialization errors:
Json5.decodeFromString<Byte>("190")
    // UnexpectedValueError: signed integer in range [-128..127] expected at position 1:1
Json5.decodeFromString<List<Double>>("[ 1.0,,")
    // CharError: unexpected character ',' at position 1:7
```

### Serializable classes
```kotlin
import io.github.xn32.json5k.Json5
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class Person(val name: String, val age: UInt? = null)

// Serialization:
Json5.encodeToString(Person("John", 31u)) // {name:"John",age:31}
Json5.encodeToString(Person("Jane")) // {name:"Jane"}

// Deserialization:
Json5.decodeFromString<Person>("{ name: 'Carl' }") // Person(name=Carl, age=null)
Json5.decodeFromString<Person>("{ name: 'Carl', age: 42 }") // Person(name=Carl, age=42)

// Deserialization errors:
Json5.decodeFromString<Person>("{ name: 'Carl', age: 42, age: 10 }")
    // DuplicateKeyError: duplicate key 'age' specified at position 1:26
```

### Classes with `@SerialName` annotations
```kotlin
import io.github.xn32.json5k.Json5
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class IntWrapper(@SerialName("integer") val int: Int)

// Serialization:
Json5.encodeToString(IntWrapper(10)) // {integer:10}

// Deserialization:
Json5.decodeFromString<IntWrapper>("{ integer: 10 }") // IntWrapper(int=10)

// Deserialization errors:
Json5.decodeFromString<IntWrapper>("{ int: 10 }")
    // UnknownKeyError: unknown key 'int' specified at position 1:3
```

### Polymorphic types
```kotlin
import io.github.xn32.json5k.ClassDiscriminator
import io.github.xn32.json5k.Json5
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
@ClassDiscriminator("mode")
sealed interface Producer

@Serializable
@SerialName("numbers")
data class NumberProducer(val init: UInt) : Producer

// Serialization:
Json5.encodeToString<Producer>(NumberProducer(10u)) // {mode:"numbers",init:10}

// Deserialization:
Json5.decodeFromString<Producer>("{ init: 0, mode: 'numbers' }") // NumberProducer(init=0)

// Deserialization errors:
Json5.decodeFromString<Producer>("{ init: 0 }")
    // MissingFieldError: missing field 'mode' in object at position 1:1
```

### Serialization of comments for class properties
```kotlin
import io.github.xn32.json5k.Json5
import io.github.xn32.json5k.SerialComment
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class Person(
    val name: String,
    val age: UInt? = null
)

@Serializable
data class Event(
    @SerialComment("First day of the event")
    val date: String,
    @SerialComment("Registered attendees")
    val attendees: List<Person>
)

val json5 = Json5 {
    prettyPrint = true
}

println(
    json5.encodeToString(
        Event("2022-10-04", listOf(Person("Emma", 31u)))
    )
)
```

Running this code will produce the following output:
```
{
    // First day of the event
    date: "2022-10-04",
    // Registered attendees
    attendees: [
        {
            name: "Emma",
            age: 31
        }
    ]
}
```

### Configuration options
Control generated JSON5 output as follows:
```kotlin
import io.github.xn32.json5k.Json5
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

val json5 = Json5 {
    prettyPrint = true
    indentationWidth = 2
    useSingleQuotes = true
    quoteMemberNames = true
    encodeDefaults = true
}

@Serializable
data class Person(val name: String, val age: UInt? = null)

println(json5.encodeToString(Person("Oliver")))
```
This will result in the following output:
```
{
  'name': 'Oliver',
  'age': null
}
```

Accept duplicate keys in JSON5 input as follows:
```kotlin
import io.github.xn32.json5k.Json5
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

val json5 = Json5 {
    failOnDuplicateKeys = false
}

@Serializable
data class Person(val name: String, val age: UInt? = null)

println(json5.decodeFromString<Person>("{ name: 'x', age: 11, name: 'y' }"))
```

In this case, the most recently written `name` value is kept:
```
Person(name=y, age=11)
```

### Further examples

See the unit tests for [serialization](src/test/kotlin/io/github/xn32/json5k/binding/SerializationTest.kt)
and [deserialization](src/test/kotlin/io/github/xn32/json5k/binding/DeserializationTest.kt) for more examples.