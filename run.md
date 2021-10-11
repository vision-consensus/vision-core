# How to Running



## Running a local node and connecting to the public testnet 

https://developers.v.network/docs/deployment-steps

### Running a Super Representative Node for mainnet

**Use the executable JAR(Recommended way):**

```bash
java -jar FullNode.jar -p your private key --witness -c your config.conf(Example：/data/vision-core/config.conf)
Example:
java -jar FullNode.jar -p 650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812 --witness -c /data/vision-core/config.conf

```

This is similar to running a private testnet, except that the IPs in the `config.conf` are officially declared by Vision.

<details>
<summary>Correct output</summary>

```bash

20:43:18.138 INFO  [main] [o.v.p.FullNode](FullNode.java:21) Full node running.
20:43:18.486 INFO  [main] [o.v.c.c.a.Args](Args.java:429) Bind address wasn't set, Punching to identify it...
20:43:18.493 INFO  [main] [o.v.c.c.a.Args](Args.java:433) UDP local bound to: xx.xx.xx.xx
20:43:18.495 INFO  [main] [o.v.c.c.a.Args](Args.java:448) External IP wasn't set, using checkip.amazonaws.com to identify it...
20:43:19.450 INFO  [main] [o.v.c.c.a.Args](Args.java:461) External address identified: 47.74.147.87
20:43:19.599 INFO  [main] [o.s.c.a.AnnotationConfigApplicationContext](AbstractApplicationContext.java:573) Refreshing org.springframework.context.annotation.AnnotationConfigApplicationContext@124c278f: startup date [Fri Apr 27 20:43:19 CST 2018]; root of context hierarchy
20:43:19.972 INFO  [main] [o.s.b.f.a.AutowiredAnnotationBeanPostProcessor](AutowiredAnnotationBeanPostProcessor.java:153) JSR-330 'javax.inject.Inject' annotation found and supported for autowiring
20:43:20.380 INFO  [main] [o.v.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:244) update latest block header timestamp = 0
20:43:20.383 INFO  [main] [o.v.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:252) update latest block header number = 0
20:43:20.393 INFO  [main] [o.v.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:260) update latest block header id = 00
20:43:20.394 INFO  [main] [o.v.c.d.DynamicPropertiesStore](DynamicPropertiesStore.java:265) update state flag = 0
20:43:20.559 INFO  [main] [o.v.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.567 INFO  [main] [o.v.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.568 INFO  [main] [o.v.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.568 INFO  [main] [o.v.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.569 INFO  [main] [o.v.c.c.TransactionCapsule](TransactionCapsule.java:83) Transaction create succeeded！
20:43:20.596 INFO  [main] [o.v.c.d.Manager](Manager.java:300) create genesis block
20:43:20.607 INFO  [main] [o.v.c.d.Manager](Manager.java:306) save block: BlockCapsule

```

Then observe whether block synchronization success，If synchronization successfully explains the success of the super node

</details>


### Running a Super Representative Node for private testnet
* use master branch
* You should modify the config.conf
  1. Replace existing entry in genesis.block.witnesses with your address.
  2. Replace existing entry in seed.node ip.list with your ip list.
  3. The first Super Node start, needSyncCheck should be set false
  4. Set p2pversion to 666666 for vpioneer , 888888 for mainnet

* Use the executable JAR(Recommended way)

```bash
cd build/libs
java -jar FullNode.jar -p your private key --witness -c your config.conf (Example：/data/vision-core/config.conf)
Example:
java -jar FullNode.jar -p 650950B193DDDDB35B6E48912DD28F7AB0E7140C1BFDEFD493348F02295BD812 --witness -c /data/vision-core/config.conf

```
  
<details>
<summary>Show Output</summary>

```bash
> ./gradlew run -Pwitness

> Task :generateProto UP-TO-DATE
Using TaskInputs.file() with something that doesn't resolve to a File object has been deprecated and is scheduled to be removed in Gradle 5.0. Use TaskInputs.files() instead.

> Task :run 
```

</details>

* In IntelliJ IDEA
  


<details>
<summary>

In the `Program arguments` option, fill in `--witness`:

</summary>

</details> 
  
Then, run `FullNode::main()` again.

## Advanced Configurations

Read the [Advanced Configurations](common/src/main/java/org/vision/core/config/README.md).
