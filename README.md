[**Issues**](https://issues.couchbase.com)
| [**Forums**](https://forums.couchbase.com)
| [**Discord**](https://discord.com/invite/sQ5qbPZuTh)
| [**Documentation**](https://docs.couchbase.com/home/sdk.html)

# Couchbase JVM Clients

[![license](https://img.shields.io/github/license/couchbase/couchbase-jvm-clients?color=brightgreen)](https://opensource.org/licenses/Apache-2.0)

[![java-client](https://img.shields.io/maven-central/v/com.couchbase.client/java-client?color=brightgreen&label=java-client)](https://search.maven.org/artifact/com.couchbase.client/java-client)
[![scala-client](https://img.shields.io/maven-central/v/com.couchbase.client/scala-client_2.12?color=brightgreen&label=scala-client)](https://search.maven.org/artifact/com.couchbase.client/scala-client_2.12)
[![kotlin-client](https://img.shields.io/maven-central/v/com.couchbase.client/kotlin-client?color=brightgreen&label=kotlin-client)](https://search.maven.org/artifact/com.couchbase.client/kotlin-client)

This repository contains the third generation of the Couchbase SDKs on the JVM ("SDK 3").

## Overview

This repository contains the following projects:

 - `core-io`: the foundational library for all language bindings
 - `java-client`: the Java language binding
 - `scala-client`: the Scala language binding
 - `kotlin-client`: the Kotlin language binding
 - `tracing-opentracing`: module to integrate with [OpenTracing](https://opentracing.io/)
 - `tracing-opentelemetry`: module to integrate with [OpenTelemetry](https://opentelemetry.io/) tracing
 - `metrics-opentelemetry`: module to integrate with [OpenTelemetry](https://opentelemetry.io/) metrics
 - `metrics-micrometer`: module to integrate with [Micrometer](https://micrometer.io/) metrics
 - `java-fit-performer`: for internal testing of the java-client transactions implementation

Other toplevel modules might be present which contain examples, experimental code or internal tooling and test infrastructure.

Documentation is available for [Java](https://docs.couchbase.com/java-sdk/current/hello-world/start-using-sdk.html), 
[Scala](https://docs.couchbase.com/scala-sdk/current/start-using-sdk.html)
and [Kotlin](https://docs.couchbase.com/kotlin-sdk/current/hello-world/overview.html).  
These include getting started guides.

## Building
Stable releases are published on [maven central](https://search.maven.org/search?q=com.couchbase.client).

You can always also just build it from source, using any JDK 8+:

```
$ git clone https://github.com/couchbase/couchbase-jvm-clients.git
$ cd couchbase-jvm-clients
$ make
```

Yes, we need `make` because maven doesn't support the setup we need and neither does gradle. If you
want to build for different Scala versions, after the first `make` you can do this through:

```sh
$ ./mvnw -D"scala.compat.version=2.13" -D"scala.compat.library.version=2.13.7" clean install
```

Notes:
+ Couchbase provides, tests and supports builds for Scala 2.12 and 2.13.
+ Default `scala.compat.`X properties are defined in file [.mvn/maven.config]
+ You can always go into one of the sub-directories like `core-io` to only build or test an
individual project:
    ```shell script
    cd scala-client
    ../mvnw -DskipTests clean install
    ```
+ Use `-DskipTests` to skip testing.

### Testing

You can test like this:

```shell script
$ ./mvnw clean test -fae
```

### Branches & Release Trains

Since this monorepo houses different versions of different artifacts, release train names have been chosen
to identify a collection of releases that belong to the same train.

These trains are named after historic computers for your delight.

Tags in each branch are named `branchname-ga` for the initial GA release, and then subsequently `branchname-sr-n` for
each service release. See the tag information for specifics of what's in there.

 - [Eos](https://nvidianews.nvidia.com/news/nvidia-announces-dgx-h100-systems-worlds-most-advanced-enterprise-ai-infrastructure) (Initial Release 2022-03-26)
 - [Colossus](https://en.wikipedia.org/wiki/Colossus_computer) (Initial Release 2020-01-10)
 - [Pegasus](https://en.wikipedia.org/wiki/Ferranti_Pegasus) (Initial Release 2020-12-02)
 - [Hopper](https://en.wikipedia.org/wiki/Grace_Hopper) (Initial Release 2021-07-20)

| Release Train | Java-Client | Scala-Client | Core-Io | Tracing-OpenTelemetry | Tracing-OpenTracing | Metrics-OpenTelemetry | Metrics-Micrometer |
|---------------|-------------|--------------|---------|-----------------------|---------------------|-----------------------|--------------------|
| colossus      | 3.0.x       | 1.0.x        | 2.0.x   | 0.2.x                 | 0.2.x               | -                     | -                  |
| pegasus       | 3.1.x       | 1.1.x        | 2.1.x   | 0.3.x                 | 0.3.x               | 0.1.x                 | 0.1.x              |
| hopper        | 3.2.x       | 1.2.x        | 2.2.x   | 1.0.x                 | 1.0.x               | 0.2.x                 | 0.2.x              |
| eos           | 3.3.x       | 1.3.x        | 2.3.x   | 1.1.x                 | 1.2.x               | 0.3.x                 | 0.3.x              |

### Testing Info

To cover all tests, the suite needs to be run against the following topologies, but by default it
runs against the mock. Recommended topologies:

 - 1 node, no replica
 - 2 nodes, 1 replica
 - 2 nodes, 2 replicas

Also to have maximum service coverage use a cluster which has all services enabled (can be MDS setup).

### Building Documentation
Documentation will be built automatically by the `mvn install` command above.

According to the Maven standard, the file is named artifact-version-javadoc.jar (i.e. java-client-3.3.1-javadoc.jar).

This file can be extracted (jars are like zip files) with the following command:

```
jar xvf java-client-3.3.1-javadoc.jar
```

This will extract the contents of the javadoc file into the current directory. After the original jar is removed it can be uploaded to s3.

The location of the javadoc files depends on where you get it from. The easiest is, once published, from Maven central.
For example, look it up on Maven central: https://search.maven.org/artifact/com.couchbase.client/java-client/3.0.4/jar and download the javadoc jar: https://search.maven.org/remotecontent?filepath=com/couchbase/client/java-client/3.0.4/java-client-3.0.4-javadoc.jar

The exact same approach can be used for any artifact, including Scala.
The Scala documentation can also be built with this command:
```
cd scala-client && mvn scala:doc
```

## IDE Configuration

### IntelliJ
Scala code is automatically formatted on compile with the tool `scalafmt`.  To make IntelliJ use the same settings:

Editor -> Code Style -> Scala, change formatter to scalafmt
and check [Reformat on file save](https://scalameta.org/scalafmt/docs/installation.html#format-on-save)

(`mvn validate` can be used from command-line to force reformat)
