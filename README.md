# hello-kafka-connect - example Kafka Connect source and sink

# EXAMPLE

```
$ gradle cucumber
Feature: Kafka Connect integration

  Scenario: Hello World kafka connect                             # kafka-connect.feature:2
    Given redis is running at URI "redis://192.168.99.100:6379"   # StepDefinitions.redisIsRunningAtURI(String)
    When a supplier generates names onto redis list "names"       # StepDefinitions.aSupplierGeneratesNamesOntoRedisList(String)
    Then connectors welcome the names onto redis list "greetings" # StepDefinitions.connectorsWelcomeTheNamesOntoRedisList(String)

1 Scenarios (1 passed)
3 Steps (3 passed)
0m3.322s


BUILD SUCCESSFUL

Total time: 4.514 secs
```

# ABOUT

[Kafka Connect](http://docs.confluent.io/2.0.0/connect/) is a secondary system on top of [Kafka](http://kafka.apache.org/) that simplifies common Kafka workflows, such as copying data between Kafka and databases, triggering actions on Kafka events, and supplying data feeds to Kafka.

hello-kafka-connect is a demonstration of how to develop and deploy source and sink connectors to a Kafka Connect cluster, using Redis as an example of an external system that can integrate with Kafka, and docker(-compose) as an example way to provision a Kafka Connect cluster.

# SETUP

## Download Docker image confluent/kafka

We've done most of the work with Docker Compose, but there is a small workaround required to account for the lack of an official Confluent entry on DockerHub.

```
$ git clone git@github.com:confluentinc/docker-images.git
$ cd docker-images/kafka/
$ docker build -t confluent/kafka .
$ cd ../../
```

## Download the hello-kafka-connect source

```
$ git clone git@github.com:mcandre/hello-kafka-connect.git
$ cd hello-kafka-connect/
```

## Package the connectors as a JAR, including any dependencies

```
$ gradle clean shadowJar
...
```

## Place the JAR in the CLASSPATH of the Kafka Connect node

```
$ docker-compose build --no-cache
...
```

## Configure Docker Compose

The Kafka and Kafka Connect nodes require advertised hostname configuration. Due to Docker kernel requirements, advertised hostnames may not simply match localhost AKA 127.0.0.1 on non-Linux system; Instead, advertised hostnames must match the Docker Machine IP address.

`docker-compose-docker-machine.yml.sample` provides an example configuration assuming a Docker Machine IP address of `192.168.99.100`; tweak to match your `echo $(docker-machine ip default)` address, or whichever Docker Machine environment you use.

### non-Linux hosts

non-Linux users (e.g. Mac OS) can configure docker-compose with:

```
$ ln -sf docker-compose-docker-machine.yml.sample docker-compose.yml
```

Windows users can create a soft link in an administrator shell, using [mklink](https://technet.microsoft.com/en-us/library/cc753194(v=ws.11).aspx):

```
C:\ admin> cmd /c mklink docker-compose.yml docker-compose-docker-machine.yml.sample
```

### Linux hosts

```
$ ln -sf docker-compose-linux-host.yml.sample docker-compose.yml
```

## Launch Kafka Connect

```
$ docker-compose rm -f && docker-compose up --force-recreate
...
```

## Ensure full Kafka topic creation

Work around an issue with Kafka Connect topic creation by manually creating topics:

```
$ kafka-topics --zookeeper $(docker-machine ip default):2181 --create --topic names --partitions 1 --replication-factor 1
Created topic "names".

$ kafka-topics --zookeeper $(docker-machine ip default):2181 --list
__consumer_offsets
connect-configs
connect-offsets
connect-status
names
```

## Submit connectors to Kafka Connect

```
$ curl -XPOST $(docker-machine ip default):8083/connectors \
       -H "Content-Type: application/json" \
       -d "{
             \"name\": \"name-source\",
             \"config\": {
               \"connector.class\": \"us.yellosoft.hellokafkaconnect.NameSource\",
               \"tasks.max\": \"1\",
               \"topics\": \"names\",
               \"kafka_partitions\": \"1\",
               \"redis_address\": \"redis://$(docker-machine ip default):6379\",
               \"name_list_key\": \"names\"
              }
           }" | jq .

{
  "name": "name-source",
  "config": {
    "connector.class": "us.yellosoft.hellokafkaconnect.NameSource",
    "tasks.max": "1",
    "topics": "names",
    "kafka_partitions": "1",
    "redis_address": "redis://192.168.99.100:6379",
    "name_list_key": "names",
    "name": "name-source"
  },
  "tasks": []
}

$ curl $(docker-machine ip default):8083/connectors | jq .

[
  "name-source"
]

$ curl -XPOST $(docker-machine ip default):8083/connectors \
       -H "Content-Type: application/json" \
       -d "{
             \"name\": \"greeting-sink\",
             \"config\": {
               \"connector.class\": \"us.yellosoft.hellokafkaconnect.GreeterSink\",
               \"tasks.max\": \"1\",
               \"topics\": \"names\",
               \"redis_address\": \"redis://$(docker-machine ip default):6379\",
               \"greeting_list_key\": \"greetings\"
              }
           }" | jq .

{
  "name": "greeting-sink",
  "config": {
    "connector.class": "us.yellosoft.hellokafkaconnect.GreeterSink",
    "tasks.max": "1",
    "topics": "names",
    "redis_address": "redis://192.168.99.100:6379",
    "greeting_list_key": "greetings",
    "name": "greeting-sink"
  },
  "tasks": []
}

$ curl $(docker-machine ip default):8083/connectors | jq .

[
  "greeting-sink",
  "name-source"
]
```

## Supply sample data

Finally, generate sample data to trigger data flowing through the system: a Redis list -> source connector -> `names` Kafka topic -> sink connector -> another Redis list.

```
$ redis-cli -h $(docker-machine ip default) lpush names 'Alice'
(integer) 1

$ redis-cli -h $(docker-machine ip default) lpush names 'Bob'
(integer) 2
```

In seconds, data flows through Kafka Connect and back to the external Redis system:

```
$ redis-cli -h $(docker-machine ip default) lpop greetings
"Welcome, Bob"

$ redis-cli -h $(docker-machine ip default) lpop greetings
"Welcome, Alice"
```

# REQUIREMENTS

* [JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html) 1.8+
* [gradle](http://gradle.org/) 2.7+
* [ZooKeeper](https://zookeeper.apache.org/) 3+
* [Kafka](http://kafka.apache.org/) 0.10+
* [Kafka Connect](http://docs.confluent.io/3.0.0/connect/) 0.10+
* [Redis](http://redis.io/) 3+

## Optional

* [curl](https://curl.haxx.se/)
* [Docker Toolbox](https://www.docker.com/products/docker-toolbox)
* [jq](https://stedolan.github.io/jq/)
* [editorconfig-cli](https://github.com/amyboyd/editorconfig-cli) (e.g. `go get github.com/amyboyd/editorconfig-cli`)
* [flcl](https://github.com/mcandre/flcl) (e.g. `go get github.com/mcandre/flcl/...`)

# See Also

For a more basic Kafka hello world example, see https://github.com/mcandre/hello-kafka
