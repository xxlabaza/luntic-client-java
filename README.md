
# Overview

This is a Java REST client for [Luntic](https://github.com/xxlabaza/luntic) dicovery service.

## Usage

```java
Map<String, Object> metadata = new HashMap<>(); // some client's meta data for
...
Discovery discovery = Discovery.create()
        .url("localhost:8080/api")
        .group("popa") // this is optional, default value -> "default"
        .meta(metadata) // this is optional too, it sets meta data during registration
        .register();

// returns client's instance
Instance instance = discovery.me();

// returns all instances of client's group
List<Instance> groupInstances = discovery.group();

// returns absolutely all instances from Luntic dicovery service
// key   - group name
// value - list of instances
Map<String, List<Instance>> allInstances = discovery.all();

// updates last modified time of client's instance
Instance updated = discovery.update();

// updates last modified time and meta data of client's instance
// in Luntic discovery service
Map<String, Object> newMetadata = new HashMap<>(4, 1.F);
...
Instance updated = discovery.update(newMetadata);

discovery.deregister(); // closing and deregistering client
```

