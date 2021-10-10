# 消息队列三大作用

#### 削峰填谷

削去秒杀场景下的峰值写流量

而在秒杀场景下，高并发的写请求并不是持续的，也不是经常发生的，而只有在秒杀活动开始后的几秒或者十几秒时间内才会存在。为了应对这十几秒的瞬间写高峰，将秒杀请求暂存在消息队列中，然后业务服务器会响应用户“秒杀结果正在计算中”，释放了系统资源之后再处理其它用户的请求。

在后台启动若干个队列处理程序，消费消息队列中的消息，再执行校验库存、下单等逻辑。因为只有有限个队列处理线程在执行，所以落入后端数据库上的并发请求是有限的。而请求是可以在消息队列中被短暂地堆积，当库存被消耗完之后，消息队列中堆积的请求就可以被丢弃了。

这就是消息队列在秒杀系统中最主要的作用：削峰填谷，也就是说它可以削平短暂的流量高峰，虽说堆积会造成请求被短暂延迟处理，但是只要时刻监控消息队列中的堆积长度，在堆积量超过一定量时，增加队列处理机数量，来提升消息的处理能力就好了，而且秒杀的用户对于短暂延迟知晓秒杀的结果，也是有一定容忍度的。

![削峰在秒杀活动中的应用](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%8A%E5%B3%B0%E5%9C%A8%E7%A7%92%E6%9D%80%E6%B4%BB%E5%8A%A8%E4%B8%AD%E7%9A%84%E5%BA%94%E7%94%A8.png)

#### 异步处理

通过异步处理简化秒杀请求中的业务流程

还是刚才的秒杀场景下，我们在处理购买请求时，需要500ms。分析了一下整个的购买流程，发现这里面会有主要的业务逻辑，也会有次要的业务逻辑：比如说，主要的流程是生成订单、扣减库存；次要的流程可能是我们在下单购买成功之后会给用户发放优惠券，会增加用户的积分。

假如发放优惠券的耗时是50ms，增加用户积分的耗时也是50ms，那么如果我们将发放优惠券、增加积分的操作放在另外一个队列处理机中执行，那么整个流程就缩短到了400ms，性能提升了20%，处理这1000件商品的时间就变成了400s。如果我们还是希望能在50s之内看到秒杀结果的话，只需要部署8个队列程序就好了。

通过消息队列可以将业务逻辑中的次要逻辑异步的处理，提升了缩短了主流程处理的时间，提高了性能。

![异步处理在秒杀活动中的作用](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%BC%82%E6%AD%A5%E5%A4%84%E7%90%86%E5%9C%A8%E7%A7%92%E6%9D%80%E6%B4%BB%E5%8A%A8%E4%B8%AD%E7%9A%84%E4%BD%9C%E7%94%A8.png)

#### 模块解耦

解耦实现秒杀系统模块之间松耦合

比如数据团队对你说，在秒杀活动之后想要统计活动的数据，借此来分析活动商品的受欢迎程度等等指标。这时需要将大量的数据发送给数据团队。

一个思路是：可以使用HTTP或者RPC的方式来同步地调用，也就是数据团队这边提供一个接口，我们实时将秒杀的数据推送给它，但是这样调用会有两个问题：

- 整体系统的耦合性比较强，当数据团队的接口发生故障时，会影响到秒杀系统的可用性。
- 当数据系统需要新的字段，就要变更接口的参数，那么秒杀系统也要随着一起变更。

这时可以考虑使用消息队列降低业务系统和数据系统的直接耦合度。

秒杀系统产生一条购买数据后，我们可以先把全部数据发送给消息队列，然后数据团队再订阅这个消息队列的话题，这样它们就可以接收到数据，然后再做过滤和处理了。解耦合之后，数据系统的故障就不会影响到秒杀系统了，同时，当数据系统需要新的字段时，只需要解析消息队列中的消息，拿到需要的数据就好了。

![系统解耦合示意图](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E7%B3%BB%E7%BB%9F%E8%A7%A3%E8%80%A6%E5%90%88%E7%A4%BA%E6%84%8F%E5%9B%BE.png)

# 架构

生产环境下Kafka可以用多种用途，从前端获取数据进行用户行为追踪，或则在不同的微服务之间进行异步解耦

![Kafka实际应用架构](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E5%AE%9E%E9%99%85%E5%BA%94%E7%94%A8%E6%9E%B6%E6%9E%84.png)

拓扑结构图：

![Kafka拓扑结构图](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%8B%93%E6%89%91%E7%BB%93%E6%9E%84%E5%9B%BE.png)

从拓扑图可以看出Kafka的broker之间是互为主备的，因为Kafka的master/slave是基于partition维度的，一个 Broker 即可以是某个分区的主副本，又可以是其他分区的从副本（Kafka有一个副本因子的配置，比如配置为3，每个分区都会有3个副本）。作为对比RocketMQ是基于broker维度的。

## 消息队列的两种模式

### 点对点模式

生产者将消息发送到queue中，然后消费者从queue中取出并且消费消息。消息被消费以后，queue中不再存储，所以消费者不可能消费到已经被消费的消息。Queue支持存在多个消费者，但是对一个消息而言，只能被一个消费者消费。类似于RocketMQ的集群模式

![Kafka点对点消费模式](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E7%82%B9%E5%AF%B9%E7%82%B9%E6%B6%88%E8%B4%B9%E6%A8%A1%E5%BC%8F.png)

### 发布/订阅模式

生产者将消息发布到topic中，同时可以有多个消费者订阅该消息。和点对点方式不同，发布到topic的消息会被所有订阅者消费。类似于RocketMQ的广播模式。

![Kafka发布订阅消费模式](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E5%8F%91%E5%B8%83%E8%AE%A2%E9%98%85%E6%B6%88%E8%B4%B9%E6%A8%A1%E5%BC%8F.jpeg)

## 主题和分区

Kafka的消息通过主题（Topic）进行分类，就好比是数据库的表，或者是文件系统里的文件夹。主题可以被分为若干个分区（Partition），一个分区就是一个提交日志。消息以追加的方式写入分区，然后以先进先出的顺序读取。**注意，由于一个主题一般包含几个分区，因此无法在整个主题范围内保证消息的顺序，但可以保证消息在单个分区内的顺序。**主题是逻辑上的概念，在物理上，一个主题是横跨多个服务器的。Kafka的分区其实就是RocketMQ的队列，用来负载均衡。

### 副本（Replica）

提到副本，肯定就会想到正本。副本是正本的拷贝。在kafka中，正本和副本都称之为副本（Repalica），但存在leader和follower之分。活跃的称之为leader，其他的是follower。

每个分区的数据都会有多份副本，以此来保证Kafka的高可用。

Topic、partition、replica的关系如下图：

![Kafka分区副本](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E5%88%86%E5%8C%BA%E5%89%AF%E6%9C%AC.jpeg)

topic下会划分多个partition，每个partition都有自己的replica，其中只有一个是leader replica，其余的是follower replica。

消息进来的时候会先存入leader replica，然后从leader replica复制到follower replica。只有复制全部完成时，consumer才可以消费此条消息。这是为了确保意外发生时，数据可以恢复。consumer的消费也是从leader replica读取的。

由此可见，leader replica做了大量的工作。所以如果不同partition的leader replica在kafka集群的broker上分布不均匀，就会造成负载不均衡。

### ACKS

acks 参数指定了必须要有多少个分区副本收到消息，生产者才会认为消息写入是成功的。 这个参数对消息丢失的可能性有重要影响。该参数有如下选项。

* 如果 acks=0，生产者在成功写入消息之前不会等待任何来自服务器的响应。也就是说， 如果当中出现了问题，导致服务器没有收到消息，那么生产者就无从得知，消息也就丢 失了。不过，因为生产者不需要等待服务器的响应，所以它可以以网络能够支持的最大 速度发送消息，从而达到很高的吞吐量。
* 如果 acks=1，只要集群的首领节点收到消息，生产者就会收到一个来自服务器的成功 响应。如果消息无法到达首领节点(比如首领节点崩溃，新的首领还没有被选举出来)， 生产者会收到一个错误响应，为了避免数据丢失，生产者会重发消息。不过，如果一个 没有收到消息的节点成为新首领，消息还是会丢失。这个时候的吞吐量取决于使用的是同步发送还是异步发送。如果让发送客户端等待服务器的响应(通过调用 Future 对象 的 get() 方法)，显然会增加延迟(在网络上传输一个来回的延迟)。如果客户端使用回 调，延迟问题就可以得到缓解，不过吞吐量还是会受发送中消息数量的限制(比如，生 产者在收到服务器响应之前可以发送多少个消息)。
* 如果 acks=all，只有当所有参与复制的节点全部收到消息时，生产者才会收到一个来自服务器的成功响应。这种模式是最安全的，它可以保证不止一个服务器收到消息，就算有服务器发生崩溃，整个集群仍然可以运行。不过，它的延迟比 acks=1 时更高，因为我们要等待不只一个服务器节点接收消息。如果 acks 被设为 all，那么请求会被保存在一个叫作**炼狱**（purgatory）的缓冲区里，直到首领发现所有跟随者副本都复制了消息，响应才会被返回给客户端

### 副本之间的同步机制ISR机制（消息可靠性）

Kafka对于producer发来的消息怎么保证可靠性？每个partition都给配上副本，做数据同步，保证数据不丢失。

副本数据同步策略：和zookeeper不同的是，Kafka选择的是全部完成同步，才发送ack。但是又有所区别。

所以，你们才会在各种博客看到这句话【kafka不是完全同步，也不是完全异步，是一种ISR机制】

首先说结论：Kafka使用的就是完全同步方案。

完全同步的优点
同样为了容忍 n 台节点的故障，过半机制需要 2n+1 个副本，而全部同步方案只需要 n+1 个副本，而 Kafka 的每个分区都有大量的数据，过半机制方案会造成大量数据的冗余。（这就是和zookeeper的不同）

完全同步会有什么问题？假设就有这么一个follower延迟太高或者某种故障的情况出现，导致迟迟不能与leader进行同步。怎么办？leader等还是不等？

等吧：producer有话要说：“Kafka也不行啊，处理个消息这么费劲，垃圾，你等NM呢等”

不等：那你Kafka对外说完全同步个鸡儿，你这是完全同步么？

基于此，Kafka的设计者和开发者想出了一个非常鸡贼的点子：ISR，什么是ISR？先来看几个概念

1、AR（Assigned Repllicas）一个partition的所有副本（就是replica，不区分leader或follower）

2、ISR（In-Sync Replicas）能够和 leader 保持同步的 follower + leader本身 组成的集合。

3、OSR（Out-Sync Relipcas）不能和 leader 保持同步的 follower 集合

4、公式：AR = ISR + OSR

> 所以，看明白了吗？ Kafka对外依然可以声称是完全同步，但是承诺是对AR中的所有replica完全同步了吗？并没有。Kafka只保证对ISR集合中的所有副本保证完全同步。至于，ISR到底有多少个follower，那不知道，别问，问就是完全同步，你再问就多了。这就好比网购买一送一，结果邮来了一大一小两个产品。你可能觉得有问题，其实是没问题的，商家说送的那个是一模一样的了吗？并没有。ISR就是这个道理，Kafka是一定会保证leader接收到的消息完全同步给ISR中的所有副本。而最坏的情况下，ISR中只剩leader自己。

基于此，上述完全同步会出现的问题就不是问题了。

因为ISR的机制就保证了，处于ISR内部的follower都是可以和leader进行同步的，一旦出现故障或延迟，就会被踢出ISR。

**ISR 的核心就是：动态调整**

总结：Kafka采用的就是一种完全同步的方案，而ISR是基于完全同步的一种优化机制。

follower的作用：读写都是由leader处理，follower只是作备份功能，不对外提供服务。

什么情况ISR中的replica会被踢出ISR？以前有2个配置

* replica.lag.time.max.ms，默认10000 即 10秒

* replica.lag.max.messages，允许 follower 副本落后 leader 副本的消息数量，超过这个数量后，follower 会被踢出 ISR

说白了就是一个衡量leader和follower之间差距的标准。

一个是基于时间间隔，一个是基于消息条数。

0.9.0.0版本之后，移除了replica.lag.max.messages 配置。

为什么？

因为producer是可以批量发送消息的，很容易超过replica.lag.max.messages，那么被踢出ISR的follower就是受了无妄之灾。

他们都是没问题的，既没有出故障也没高延迟，凭什么被踢？

replica.lag.max.messages调大呢？调多大？太大了是否会有漏网之鱼，造成数据丢失风险？

这就是replica.lag.max.messages的设计缺陷。

**replica.lag.time.max.ms的误区**

**只要在 replica.lag.time.max.ms 时间内 follower 有同步消息，即认为该 follower 处于 ISR 中**

当follower副本将leader副本的LEO之前的日志全部同步时，则认为该follower副本已经追赶上leader副本。此时更新该副本的lastCaughtUpTimeMs标识。Kafka的副本管理器（ReplicaManager）启动时会启动一个副本过期检测的定时任务，会定时检查当前时间与副本的lastCaughtUpTimeMs差值是否大于参数replica.lag.time.max.ms指定的值。所以replica.lag.time.max.ms的正确理解是：follower在过去的replica.lag.time.max.ms时间内，已经追赶上leader一次了。其实就是follower拉取完所有LEO之前的时候的时间戳和leader时间差，如果时间差在这个配置里面就说明这个副本是应该在ISR里面

**follower到底出了什么问题？**

两个方面，一个是Kafka自身的问题，另一个是外部原因

Kafka源码注释中说明了一般有两种情况会导致副本失效：

* follower副本进程卡住，在一段时间内根本没有想leader副本发起同步请求，比如频繁的Full GC。

* follower副本进程同步过慢，在一段时间内都无法追赶上leader副本，比如IO开销过大。

外部原因：

1. 通过工具增加了副本因子，那么新增加的副本在赶上leader副本之前也都是处于失效状态的。

2. 如果一个follower副本由于某些原因（比如宕机）而下线，之后又上线，在追赶上leader副本之前也是出于失效状态。

什么情况OSR中的replica会重新加入ISR？
基于上述，replica重新追上了leader，就会回到ISR中。

### 高水位机制

- HW是 High Watermark 的缩写，俗称高水位，它标识了一个特定的消息偏移量（offset），消费者只能拉取到这个 offset 之前的消息。HW 保证了 Kafka 集群中消息的一致性。确切地说，是保证了 Partition 的 Follower 与 Leader 间数 据的一致性。
- LEO是 Log End Offset 的缩写，日志最后消息的偏移量。消息是被写入到 Kafka 的日志文件中的， 它标识当前日志文件中下一条**待写入**消息的 offset。

![Kafka高水位机制](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E9%AB%98%E6%B0%B4%E4%BD%8D%E6%9C%BA%E5%88%B6.png)

如上图所示，第一条消息的 offset（LogStartOffset）为0，最后一条消息的 offset 为8，offset 为9的消息用虚线框表示，代表下一条待写入的消息。日志文件的 HW 为6，表示消费者只能拉取到 offset 在0至5之间的消息，而 offset 为6的消息对消费者而言是不可见的。

对于 Leader 新写入的消息，Consumer 是不能立刻消费的。Leader 会等待该消息被所有 ISR 中的 Partition Follower 同步后才会更新 HW，此时消息才能被 Consumer 消费。

在 Kafka 中，高水位的作用主要有 2 个

- 定义消息可见性，即用来标识分区下的哪些消息是可以被消费者消费的。
- 帮助 Kafka 完成副本同步。

在分区高水位以下的消息被认为是已提交消息，反之就是未提交消息。消费者只能消费已提交消息。位移值等于高水位的消息也属于未提交消息。也就是说，高水位上的消息是不能被消费者消费的。同一个副本对象，其高水位值不会大于 LEO 值

#### 副本同步机制（高水位机制）

每个副本对象都保存了一组高水位值和 LEO 值，但实际上，在 Leader 副本所在的 Broker 上，还保存了其他 Follower 副本的 LEO 值。

![高水位副本保存机制](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E9%AB%98%E6%B0%B4%E4%BD%8D%E5%89%AF%E6%9C%AC%E4%BF%9D%E5%AD%98%E6%9C%BA%E5%88%B6.png)

在上图中，我们可以看到，Broker 0 上保存了某分区的 Leader 副本和所有 Follower 副本的 LEO 值，而 Broker 1 上仅仅保存了该分区的某个 Follower 副本。Kafka 把 Broker 0 上保存的这些 Follower 副本又称为远程副本（Remote Replica）。

简单来说，每个副本都有HW和LEO的存储，而leader不但保存自己的HW和LEO, 还保存了每个远端副本的LEO，用于在自身的HW更新时计算值。

Kafka 副本机制在运行过程中，会更新 Broker 1 上 Follower 副本的高水位和 LEO 值，同时也会更新 Broker 0 上 Leader 副本的高水位和 LEO 以及所有远程副本的 LEO，**但它不会更新远程副本的高水位值**。

在 Broker 0 上保存这些远程副本的主要作用是，**帮助 Leader 副本确定其高水位，也就是分区高水位**。

HW和LEO的更新策略如下：

| Follower自己的LEO | Follower从leader副本拉取消息，写入磁盘后，更新LEO值          |
| :---------------- | :----------------------------------------------------------- |
| Leader自己的LEO   | Leader收到producer消息，写入磁盘后，更新LEO值                |
| Leader的远程LEO   | Follower fech时带上自己的LEO, leader使用这个值更新远程LEO    |
| Follower的自己HW  | followerfetch成功更新LEO后，比较leader发来的hw和自己的hw,取较小值 |
| Leader自己的hw    | Leader更新LEO之后，更新完远程LEO之后，取所有副本的最小LEO    |

一次完整的写请求的 HW / LEO 更新流程：

当生产者发送一条消息时，Leader 和 Follower 副本对应的高水位是怎么被更新的呢？我给出了一些图片，我们一一来看。首先是初始状态。下面这张图中的 remote LEO 就是刚才的远程副本的 LEO 值。在初始状态时，所有值都是 0。

![副本更新初始状态](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%AF%E6%9C%AC%E6%9B%B4%E6%96%B0%E5%88%9D%E5%A7%8B%E7%8A%B6%E6%80%81.webp)

当生产者给主题分区发送一条消息后，状态变更为：

![副本更新生产者发送消息后状态](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%AF%E6%9C%AC%E6%9B%B4%E6%96%B0%E7%94%9F%E4%BA%A7%E8%80%85%E5%8F%91%E9%80%81%E6%B6%88%E6%81%AF%E5%90%8E%E7%8A%B6%E6%80%81.webp)

此时，Leader 副本成功将消息写入了本地磁盘，故 LEO 值被更新为 1。Follower 再次尝试从 Leader 拉取消息。和之前不同的是，这次有消息可以拉取了，因此状态进一步变更为：

![副本更新follower再次拉取状态](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%AF%E6%9C%AC%E6%9B%B4%E6%96%B0follower%E5%86%8D%E6%AC%A1%E6%8B%89%E5%8F%96%E7%8A%B6%E6%80%81.webp)

这时，Follower 副本也成功地更新 LEO 为 1。此时，Leader 和 Follower 副本的 LEO 都是 1，但各自的高水位依然是 0，还没有被更新。它们需要在下一轮的拉取中被更新，如下图所示：

![副本更新最后一次拉取后状态](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%AF%E6%9C%AC%E6%9B%B4%E6%96%B0%E6%9C%80%E5%90%8E%E4%B8%80%E6%AC%A1%E6%8B%89%E5%8F%96%E5%90%8E%E7%8A%B6%E6%80%81.webp)

在新一轮的拉取请求中，由于位移值是 0 的消息已经拉取成功，因此 Follower 副本这次请求拉取的是位移值 =1 的消息。Leader 副本接收到此请求后，更新远程副本 LEO 为 1，然后更新 Leader 高水位为 1。做完这些之后，它会将当前已更新过的高水位值 1 发送给 Follower 副本。Follower 副本接收到以后，也将自己的高水位值更新成 1。至此，一次完整的消息同步周期就结束了。事实上，Kafka 就是利用这样的机制，实现了 Leader 和 Follower 副本之间的同步。

这种 HW 和 LEO 更新策略有个很明显的问题，**即 follower 的 HW 更新需要 follower 的 2 轮 fetch 中的 leader 返回才能更新， 而 Leader 的 HW 已更新**。这之间，如果 follower 和 leader 的节点发生故障，则 follower 的 HW 和 leader 的 HW 会处于不一致状态，带来比较多的一致性问题。比如如下场景：

- Leader 更新完分区 HW 后，follower HW 还未更新，此时 follower 重启
- Follower 重启后，LEO 设置为之前的 follower HW 值(0), 此时发生**消息截断**，把多余的没有提交成功的消息删除(截断主要是因为follower必须要与leader保持一致，而一旦某个“落后”的副本成为leader，其他“领先”的follower必须与其保持一致，必须truncate掉自己多余的消息)
- Follower 重新同步 leader, 此时 leader 宕机，则不选举则不可用
- Follower 被选举为 leader, 则 msg 1 永久丢失了

**消息截断**

当[acks](#acks)=-1的情况下，新消息只有被 ISR 中的所有 follower都从 leader 复制过去才会回 ack, ack 后，无论那种机器故障情况(全部或部分), 写入的消息，都不会丢失， 消息状态满足一致性 C 要求。正常情况下，所有 follower 复制完成后，leader 回 producer ack。

异常情况下，如果当数据发送到 leader后部分只有副本同步成功， leader 挂了？此时任何 follower 都有可能变成新的 leader， producer 端会得到返回异常，producer 端会重新发送数据，但这样数据可能会重复(但不会丢失)， 暂不考虑数据重复的情况。

min.insync.replicas 参数用于保证当前集群中处于正常同步状态的副本 follower 数量，当实际值小于配置值时，集群停止服务。如果配置为 N/2+1, 即多一半的数量，则在满足此条件下，通过算法保证强一致性。当不满足配置数时，牺牲可用性即停服。

异常情况下，leader 挂掉，此时需要重新从 follower 选举 leader。可以为 f2 或者 f3。

![Leader挂掉时副本同步情况](https://gitee.com/syllr/images/raw/master/uPic/kafka/Leader%E6%8C%82%E6%8E%89%E6%97%B6%E5%89%AF%E6%9C%AC%E5%90%8C%E6%AD%A5%E6%83%85%E5%86%B5.png)

如果选举 f3 为新 leader, 则可能会发生消息截断，因为 f3 还未同步 msg4 的数据，这样会直接丢失msg4。Kafka 的通 unclean.leader.election.enable 来控制在这种情况下，是否可以选举 f3 为 leader。旧版本中默认为 true,在某个版本下已默认为 false，避免这种情况下消息截断的出现，但是无论如何当leader写入了数据还没有来得及同步给任意一个follower的时候，这个时候如果leader宕机，这个时候选出的新的leader就要进行日志截断（本质上是因为follower需要请求两次同步leader的HW和LOE，当一个follower的HW和LOE不一致的时候，这个时候这个follower如果被选成leader就必须要把自己的LOE设置为HW的值，即把文件中没有提交的消息删除掉），日志截断主要是因为follower必须要与leader保持一致，而一旦某个“落后”的副本成为leader，其他“领先”的follower必须与其保持一致，必须truncate掉自己多余的消息。至于如何在这个过程中保持副本间的一致，社区之前使用高水位机制，但发现有一些固有的缺陷，进而开发了leader epoch。

#### Leader Epoch 登场

从刚才的分析中，我们知道，Follower 副本的高水位更新需要一轮额外的拉取请求才能实现。如果把上面那个例子扩展到多个 Follower 副本，情况可能更糟，也许需要多轮拉取请求。也就是说，Leader 副本高水位更新和 Follower 副本高水位更新在时间上是存在错配的。这种错配是很多“数据丢失”或“数据不一致”问题的根源。基于此，社区在 0.11 版本正式引入了 Leader Epoch 概念，来规避因高水位更新错配导致的各种不一致问题。

所谓 Leader Epoch，我们大致可以认为是 Leader 版本。它由两部分数据组成

1. Epoch。一个单调增加的版本号。每当副本领导权发生变更时，都会增加该版本号。小版本号的 Leader 被认为是过期 Leader，不能再行使 Leader 权力。
2. 起始位移（Start Offset）。Leader 副本在该 Epoch 值上写入的首条消息的位移。

我举个例子来说明一下 Leader Epoch。假设现在有两个 Leader Epoch<0, 0> 和 <1, 120>，那么，第一个 Leader Epoch 表示版本号是 0，这个版本的 Leader 从位移 0 开始保存消息，一共保存了 120 条消息。之后，Leader 发生了变更，版本号增加到 1，新版本的起始位移是 120。

Kafka Broker 会在内存中为每个分区都缓存 Leader Epoch 数据，同时它还会定期地将这些信息持久化到一个checkpoint文件中。当 Leader 副本写入消息到磁盘时，Broker 会尝试更新这部分缓存。如果该 Leader 是首次写入消息，那么 Broker 会向缓存中增加一个 Leader Epoch 条目，否则就不做更新。这样，每次有 Leader 变更时，新的 Leader 副本会查询这部分缓存，取出对应的 Leader Epoch 的起始位移，以避免数据丢失和不一致的情况。

接下来，我们来看一个实际的例子，它展示的是 Leader Epoch 是如何防止数据丢失的，看下图。

![副本更新Leader宕机follwer成为leader读取echopo](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E5%89%AF%E6%9C%AC%E6%9B%B4%E6%96%B0Leader%E5%AE%95%E6%9C%BAfollwer%E6%88%90%E4%B8%BAleader%E8%AF%BB%E5%8F%96echopo.webp)

引用 Leader Epoch 机制后，Follower 副本 B 重启回来后，需要向 A 发送一个特殊的请求去获取 Leader 的 LEO 值。在这个例子中，该值为 2。当获知到 Leader LEO=2 后，B 发现该 LEO 值不比它自己的 LEO 值小，而且缓存中也没有保存任何起始位移值 > 2 的 Epoch 条目，因此 B 无需执行任何日志截断操作。这是对高水位机制的一个明显改进，即副本是否执行日志截断不再依赖于高水位进行判断。

现在，副本 A 宕机了，B 成为 Leader。同样地，当 A 重启回来后，执行与 B 相同的逻辑判断，发现也不用执行日志截断，至此位移值为 1 的那条消息在两个副本中均得到保留。后面当生产者程序向 B 写入新消息时，副本 B 所在的 Broker 缓存中，会生成新的 Leader Epoch 条目：[Epoch=1, Offset=2]。之后，副本 B 会使用这个条目帮助判断后续是否执行日志截断操作。这样，通过 Leader Epoch 机制，Kafka 完美地规避了这种数据丢失场景。

### Kafka以Partition作为存储单元

一个partition是一个有序的，不变的消息队列，消息总是被追加到尾部。一个partition不能被切分成多个散落在多个broker上或者多个磁盘上。

Kafka数据保留策略。你可以设置要被保留的数据量和时长，之后Kafka就会按照你的配置去清除消息数据，无论这个数据是否被消费。

### Partition是由多个Segment组成

Kafka需要在磁盘上查找需要删除的消息，假设一个partition是一个单个非常长的文件的话，那么这个查找操作会非常慢并且容易出错。为解决这个问题，partition又被划分成多个segment来组织数据。

当Kafka要写数据到一个partition时，它会写入到状态为active的segment中。如果该segment被写满，则一个新的segment将会被新建，然后变成新的“active” segment。

Segment以该segment的base offset作为自己的名称。

![Kafka的分区是由多个segment组成的](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E7%9A%84%E5%88%86%E5%8C%BA%E6%98%AF%E7%94%B1%E5%A4%9A%E4%B8%AAsegment%E7%BB%84%E6%88%90%E7%9A%84.png)

在磁盘上，一个partition就是一个目录，然后每个segment由一个index文件和一个log文件在高版本下还有.timeIndex文件组成。如下：

├── events-1

│ ├── 00000000003064504069.index

│ ├── 00000000003064504069.log

│ ├── 00000000003064504069.timeIndex

│ ├── 00000000003065011416.index

│ ├── 00000000003065011416.log

│ ├── 00000000003065011416.timeIndex

### Segment下的log文件就是存储消息的地方

每个消息都会包含消息体、offset、timestamp、key、size、压缩编码器、校验和、消息版本号等。

在磁盘上的数据格式和producer发送到broker的数据格式一模一样，也和consumer收到的数据格式一模一样。由于磁盘格式与consumer以及producer的数据格式一模一样，这样就使得Kafka可以通过零拷贝（zero-copy）技术来提高传输效率。

**消息内容**

![Kafka消息格式](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%B6%88%E6%81%AF%E6%A0%BC%E5%BC%8F.png)

* CRC32：4个字节。消息的CRC校验码。

* magic：1个字节。魔数标识，与消息格式有关，取值为0或1。当magic为0时，消息的offset使用绝对offset且消息格式中没有timestamp部分；当magic为1时，消息的offset使用相对offset且消息格式中存在timestamp部分。

* attributes：1个字节。0~2位表示消息使用的压缩类型，0(000)->无压缩 1(001)->gzip 压缩 2(010)->snappy 压缩 3(011)-> lz4 压缩。第3位表示时间戳类型，0->创建时间 1->追加时间。

* timestamp：8个字节，时间戳。

* key length：消息key的长度。

* key：消息的key。

* value length：消息的内容长度。

* value：消息的内容。

还有一点，Record的写入是支持数据分包的，也就是一个完整的VALUE值可以通过valueOffset和valueSize来指定偏移和这次写入的数据大小来进行分包操作，这样就可以将一个完整的消息，分成多个Record。如果valueSize是负数，就表示从valueOffset开始到末尾的数据都写入。与之相对应的是RocketMQ不支持数据分包。

**Kafka消息压缩**

生产者发送压缩消息，是把多个消息批量发送，把多个消息压缩成一个wrapped message来发送。和普通的消息一样，在磁盘上的数据和从producer发送来到broker的数据格式一模一样，发送给consumer的数据也是同样的格式。

![Kafka批消息](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%89%B9%E6%B6%88%E6%81%AF.png)

**.index和.timeindex文件**

.index和.timeindex是在.log基础上建立起来的索引文件。这是它们第一个相同点。

**接下来首先讨论.index文件。**

在kafka中，每个日志分段文件都对应了两个索引文件——**偏移量索引文件和时间戳索引文件**（就是.indxe文件和.timeIndex文件），主要用来**提高查找消息的效率**。

- 偏移量索引文件用来**建立消息偏移量(offset)到物理地址之间的映射关系**，方便快速定位消息所在的物理文件位置；
- 时间戳索引文件则根据指定的时间戳(timestamp)来**查找对应的偏移量信息**。

偏移量索引文件用来建立消息偏移量(offset)到物理地址之间的映射关系，方便快速定位消息所在的物理文件位置；时间戳索引文件则根据指定的时间戳(timestamp)来查找对应的偏移量信息。

**Kafka 中的索引文件以稀疏索引(sparse index)的方式构造消息的索引，它并不保证每个消息在索引文件中都有对应的索引项。**

每当写入一定量  (由 broker 端参数 log.index.interval.bytes 指定，默认值为 4096，即 4KB)  的消息时，偏移量索引文件和时间戳索引文件分别增加一个偏移量索引项和时间戳索引项，增大或减小 log.index.interval.bytes 的值，对应地可以缩小或增加索引项的密度。

稀疏索引通过 MappedByteBuffer 将索引文件映射到内存中，以加快索引的查询速度。

偏移量索引文件中的偏移量是单调递增的，查询指定偏移量时，使用二分查找法来快速定位偏移量的位置，如果指定的偏移量不在索引文件中，则会返回小于指定偏移量的最大偏移量。（因为index文件里面的元素的大小是固定的，所以可以用二分法）

时间戳索引文件中的时间戳也保持严格的单调递增，查询指定时间戳时，也根据二分查找法来查找不大于该时间戳的最大偏移量，至于要找到对应的物理文件位置还需要根据偏移量索引文件来进行再次定位。

稀疏索引的方式是在磁盘空间、内存空间、查找时间等多方面之间的一个折中。

以偏移量索引文件来做具体分析。偏移量索引项的格式如下图所示。

每个索引项占用 8 个字节，分为两个部分：

* relativeOffset: 相对偏移量，表示消息相对于 baseOffset 的偏移量，占用 4 个字节，当前索引文件的文件名即为 baseOffset 的值。

* position: 物理地址，也就是消息在日志分段文件中对应的物理位置，占用 4 个字节。

![Kafka索引文件结构](https://gitee.com/syllr/images/raw/master/uPic/kafka/watermark,type_ZmFuZ3poZW5naGVpdGk,shadow_10,text_aHR0cHM6Ly9ibG9nLmNzZG4ubmV0L3UwMTA5MDA3NTQ=,size_16,color_FFFFFF,t_70-20210818212149055.png)

消息的偏移量(offset)占用 8 个字节，也可以称为绝对偏移量。

索引项中没有直接使用绝对偏移量而改为只占用 4 个字节的相对偏移量(relativeOffset = offset - baseOffset)，这样可以减小索引文件占用的空间。

举个例子，一个日志分段的 baseOffset 为 32，那么其文件名就是 00000000000000000032.log，offset 为 35 的消息在索引文件中的 relativeOffset 的值为 35-32=3。

![Kafka日志和索引文件](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%97%A5%E5%BF%97%E5%92%8C%E7%B4%A2%E5%BC%95%E6%96%87%E4%BB%B6.png)

如果我们要查找偏移量为 23 的消息，那么应该怎么做呢?首先通过二分法在偏移量索引文件中找到不大于 23 的最大索引项，即[22, 656]，然后从日志分段文件中的物理位置 656 开始顺序查找偏移量为 23 的消息。

![KafkaLogSegmentOffSet图](https://gitee.com/syllr/images/raw/master/uPic/kafka/KafkaLogSegmentOffSet%E5%9B%BE.png)

**以上是最简单的一种情况。参考上图，如果要查找偏移量为 268 的消息，那么应该怎么办呢?**

首先肯定是定位到baseOffset为251的日志分段，然后计算相对偏移量relativeOffset = 268 - 251 = 17，之后再在对应的索引文件中找到不大于 17 的索引项，最后根据索引项中的 position 定位到具体的日志分段文件位置开始查找目标消息。

**那么又是如何查找 baseOffset 为 251 的日志分段的呢?**

这里并不是顺序查找，而是用了跳跃表的结构。Kafka 的每个日志对象中使用了 ConcurrentSkipListMap 来保存各个日志分段，每个日志分段的 baseOffset 作为 key，这样可以根据指定偏移量来快速定位到消息所在的日志分段。

在Kafka中要定位一条消息，那么首先根据 offset 从 ConcurrentSkipListMap 中来查找到到对应（baseOffset）日志分段的索引文件，然后读取偏移量索引索引文件，之后使用二分法在偏移量索引文件中找到不大于 offset - baseOffset z的最大索引项，接着再读取日志分段文件并且从日志分段文件中顺序查找relativeOffset对应的消息。

Kafka中通过offset查询消息内容的整个流程我们可以简化成下图：

![Kafka索引查找流程](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E7%B4%A2%E5%BC%95%E6%9F%A5%E6%89%BE%E6%B5%81%E7%A8%8B.png)

### Kafka和MySQL的索引区别

**Kafka中的offset类比成InnoDB中的主键**

Kafka中消息的offset可以类比成InnoDB中的主键，前者是通过offset检索出整条Record的数据，后者是通过主键检索出整条Record的数据。

InnoDB中通过主键查询数据内容的整个流程建议简化成下图

![Mysql索引查找过程](https://gitee.com/syllr/images/raw/master/uPic/kafka/Mysql%E7%B4%A2%E5%BC%95%E6%9F%A5%E6%89%BE%E8%BF%87%E7%A8%8B.png)

Kafka中通过时间戳索引文件去检索消息的方式可以类比于InnoDB中的辅助索引的检索方式：

前者是通过timestamp去找offset，后者是通过索引去找主键，后面两者的过程就和上面的陈述相同。

**Kafka中当有新的索引文件建立的时候ConcurrentSkipListMap才会更新，而不是每次有数据写入时就会更新，这块的维护量基本可以忽略**

**B+树中数据有插入、更新、删除的时候都需要更新索引，还会引来“页分裂”等相对耗时的操作。Kafka中的索引文件也是顺序追加文件的操作，和B+树比起来工作量要小很多。**

**应用场景不同所决定**

说到底还是应用场景不同所决定的。MySQL中需要频繁地执行CRUD的操作，CRUD是MySQL的主要工作内容，而为了支撑这个操作需要使用维护量大很多的B+树去支撑。

Kafka中的消息一般都是顺序写入磁盘，再到从磁盘顺序读出（不深入探讨page cache等），他的主要工作内容就是：写入+读取，很少有检索查询的操作

换句话说，**检索查询只是Kafka的一个辅助功能，不需要为了这个功能而去花费特别太的代价去维护一个高level的索引。**

Kafka中的这种方式是在磁盘空间、内存空间、查找时间等多方面之间的一个折中。

**.timeindex文件与.index原理相同，只是它的IndexEntry的两个字段分别是timestamp(8bytes)和relativeOffset(4bytes)。**用来减少按时间戳查找消息时遍历元素数量。

# Broker控制器

Kafka的集群由n个的broker所组成，每个broker就是一个kafka的实例或者称之为kafka的服务。其实控制器也是一个broker，控制器也叫leader broker。

他除了具有一般broker的功能外，还负责分区leader的选取，也就是负责选举partition的leader replica，控制器主要用来管理和协调集群，具体是通过`ZooKeeper`临时节点和`Watcher`机制来监控集群的变化，更新集群的元数据，并且通知集群中的其他`Broker`进行相关的操作（**控制器会为集群中所有的broker都创建一个各自的连接，假设集群里面有100台broker，就会创建100个连接（包括他自己）**）。

![Kafka控制器和broker集群通信图](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%8E%A7%E5%88%B6%E5%99%A8%E5%92%8Cbroker%E9%9B%86%E7%BE%A4%E9%80%9A%E4%BF%A1%E5%9B%BE.webp)

## 控制器选举

每台 Broker 都能充当控制器，那么，当集群启动后，Kafka 怎么确认控制器位于哪台 Broker 呢？
实际上，kafka每个broker启动的时候，都会实例化一个KafkaController，并将broker的id注册到zookeeper,并会尝试去 ZooKeeper 中创建 /controller 节点。Kafka 当前选举控制器的规则是：第一个成功创建 /controller 节点的 Broker 会被指定为控制器。

包括集群启动在内，有三种情况触发控制器选举：

1. 集群启动

2. 控制器所在代理发生故障

3. zookeeper心跳感知，控制器与自己的session过期

控制器选举过程如下图

![Kafka控制器选举过程](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%8E%A7%E5%88%B6%E5%99%A8%E9%80%89%E4%B8%BE%E8%BF%87%E7%A8%8B.jpeg)

此外zk中还有controller_epoch节点，存储了leader的变更次数，初始值为0，以后leader每变一次，该值+1。所有向控制器发起的请求，都会携带此值。如果控制器和自己内存中比较，请求值小，说明kafka集群已经发生了新的选举，此请求过期，此请求无效。如果请求值大于控制器内存的值，说明已经有新的控制器当选了，自己已经退位，请求无效。kafka通过controller_epoch保证集群控制器的唯一性及操作的一致性。

由此可见，Kafka控制器选举就是看谁先争抢到/controller节点写入自身信息。

![Kafka控制器内部组件](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%8E%A7%E5%88%B6%E5%99%A8%E5%86%85%E9%83%A8%E7%BB%84%E4%BB%B6.jpeg)

因为kafka是通过zk来进行同步的，所以需要选出一个控制器，控制器的作用就是直接和zk进行通信来管理集群相关的数据，因为如果不通过控制器做一个收口的话，每个broker和zk单独通信，容易造成羊群效应和脑裂问题，也是因为这个原因，kafka才加入了协调器。

![Kafka控制器和zk关系](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E6%8E%A7%E5%88%B6%E5%99%A8%E5%92%8Czk%E5%85%B3%E7%B3%BB.png)

控制器在选举成功之后会读取Zookeeper中各个节点的数据来初始化上下文信息（ControllerContext），并且也需要管理这些上下文信息，比如为某个topic增加了若干个分区，控制器在负责创建这些分区的同时也要更新上下文信息，并且也需要将这些变更信息同步到其他普通的broker节点中。

不管是监听器触发的事件，还是定时任务触发的事件，亦或者是其他事件（比如ControlledShutdown）都会读取或者更新控制器中的上下文信息，那么这样就会涉及到多线程间的同步，如果单纯的使用锁机制来实现，那么整体的性能也会大打折扣。

针对这一现象，Kafka的控制器使用单线程基于事件队列的模型，将每个事件都做一层封装，然后按照事件发生的先后顺序暂存到LinkedBlockingQueue中，然后使用一个专用的线程（ControllerEventThread）按照FIFO（First Input First Output, 先入先出）的原则顺序处理各个事件，这样可以不需要锁机制就可以在多线程间维护线程安全。

## 分区控制

* 为ZK中的/admin/reassign_partitions节点注册PartitionReassignmentHandler用来处理分区重新分配的动作

* 为ZK中的/admin/isr_change_notification节点注册IsrChangeNotificetionHandler ，用来处理ISR副本集合的变更动作

* 为ZK中的/admin/preferred-replica-election节点添加PreferredReplicaElectionHandler，用来处理优先副本选举的动作

  > 对于每一个topic的partition，控制器先获得preferred replica（在Kafka中默认的leader的选举策略是OfflinePartitionLeaderElectionStrategy，这个策略会从AR中按顺序查找第一个存活的副本，并且这个副本必须在ISR中，如果不进行分区的重新分配，AR中的副本以及顺序是不变的，但是ISR会变，所以一般来说，Leader就是优先副本）如果这个preferred replica不是leader，controller则向preferred replica所在的那个broker发送一个请求，使之成为partition的leader，然后控制器给集群中的每一个broker发送一个LeaderAndIsrRequest请求，告知`Broker`主题相关分区`Leader`和`ISR`副本都在哪些 `Broker`上。

## 主题控制

- 为ZK中的/brokers/topics节点添加**TopicChangeHandler**，用来处理主题增减的变化
- 为ZK中的/admin/delete_topics节点注册**TopicDeletionHandler**，用来处理主题的删除动作

## Broker控制

- 为ZK中的/brokers/ids节点添加**BrokerChangeHandler**,用来处理Broker增减的变化

## 其他

* 从ZK中获取Broker、Topic、Partition相关的元数据信息
  * 为/brokers/topics/<topic>节点注册PartitionModificationHandler，监听主题中分区分配的变化
* 启动并管理分区状态机和副本状态机
* 更新集群的元数据信息,并同步给其它的Broker
* 如果开启了自动优先副本选举，那么会后台启动一个任务用来自动维护优先副本的均衡。

## 控制器和集群同步

`Controller`从`ZooKeeper`那儿得到变更通知之后，需要告知集群中的`Broker`（包括它自身）做相应的处理。

Controller只会给集群的Broker发送三种请求：分别是 `LeaderAndIsrRequest`、`StopReplicaRequest`和 `UpdateMetadataRequest`

### **LeaderAndIsrRequest**

告知`Broker`主题相关分区`Leader`和`ISR`副本都在哪些 `Broker`上。

### **StopReplicaRequest**

告知`Broker`停止相关副本操作，用于删除主题场景或分区副本迁移场景。

### **UpdateMetadataRequest**

更新`Broker`上的元数据。

`Controller事件处理线程`会把事件封装成对应的请求，然后将请求写入对应的`Broker`的请求阻塞队列，然后`RequestSendThread`不断从阻塞队列中获取待发送的请求。

![img](https://gitee.com/syllr/images/raw/master/uPic/kafka/v2-e923c9de910709a30b3a82f6a854d7f4_1440w.jpg)

# 协调器

主要的协调器有如下两个：

1、消费者协调器（ConsumerCoordinator）（消费者端）

2、组协调器（GroupCoordinator）（服务端）

kafka引入协调器有其历史过程，原来consumer信息依赖于zookeeper存储，当代理或消费者发生变化时，引发消费者平衡，此时消费者之间是互不透明的，每个消费者和zookeeper单独通信，容易造成羊群效应和脑裂问题。

为了解决这些问题，kafka引入了协调器。服务端引入组协调器（GroupCoordinator），消费者端引入消费者协调器（ConsumerCoordinator）。每个broker启动的时候，都会创建GroupCoordinator实例，管理部分消费组（集群负载均衡）和组下每个消费者消费的偏移量（offset）。每个consumer实例化时，同时实例化一个ConsumerCoordinator对象，负责同一个消费组下各个消费者和服务端组协调器之前的通信。如下图：

![Kafka协调器](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E5%8D%8F%E8%B0%83%E5%99%A8.jpeg)

Kafka 在**服务端引入了组协调器(GroupCoordinator)**，每个 Kafka Server（broker）启动时都会创建一个 GroupCoordinator 实例，**用于管理部分消费者组和该消费者组下的每个消费者的消费偏移量**。同时**在客户端引入了消费者协调器(ConsumerCoordinator)**，实例化一个消费者就会实例化一个 ConsumerCoordinator 对象，ConsumerCoordinator **负责同一个消费者组下各消费者与服务端的 GroupCoordinator 进行通信**。

### 消费者协调器(ConsumerCoordinator)

```java
public class KafkaConsumer<K, V> implements Consumer<K, V> {
    //ConsumerCoordinator 是 KafkaConsumer 的一个私有的成员变量，因此 ConsumerCoordinator 中存储的信息也只有与之对应的消费者可见，不同消费者之间是			看不到彼此的 ConsumerCoordinator 中的信息的。
    private final ConsumerCoordinator coordinator;
    
}
```

从上面可以知道只要有一个消费者就有一个消费者协调器，ConsumerCoordinator 的作用：

- 处理更新消费者缓存的 Metadata 请求
- 向组协调器发起加入消费者组的请求
- 对本消费者加入消费者前后的相应处理
- 请求离开消费者组(例如当消费者取消订阅时)
- 向组协调器发送提交偏移量的请求
- 通过一个定时的心跳检测任务来让组协调器感知自己的运行状态
- **Leader消费者的 ConsumerCoordinator 还负责执行分区的分配，一个消费者组中消费者 leader 由组协调器选出，leader 消费者的 ConsumerCoordinator 负责消费者与分区的分配，然后把分配结果发送给组协调器，然后组协调器再把分配结果返回给其他消费者的消费者协调器，这样减轻了服务端的负担**

ConsumerCoordinator 实现上述功能的组件是 ConsumerCoordinator 类的私有成员或者是其父类的私有成员：

```java
public final class ConsumerCoordinator extends AbstractCoordinator {
    private final List<PartitionAssignor> assignors;
    private final OffsetCommitCallback defaultOffsetCommitCallback;
    private final SubscriptionState subscriptions;
    private final ConsumerInterceptors<?, ?> interceptors;
    private boolean isLeader = false;
    private MetadataSnapshot metadataSnapshot;
    private MetadataSnapshot assignmentSnapshot;
    
    省略了部分代码....
}


public abstract class AbstractCoordinator implements Closeable {
    private enum MemberState {
        UNJOINED,    // the client is not part of a group
        REBALANCING, // the client has begun rebalancing
        STABLE,      // the client has joined and is sending heartbeats
    }

    private final Heartbeat heartbeat;
    protected final ConsumerNetworkClient client;
    private HeartbeatThread heartbeatThread = null;
    private MemberState state = MemberState.UNJOINED;
    private RequestFuture<ByteBuffer> joinFuture = null;
    省略了部分代码....
}
```

各组件及其功能如下图所示：

![kafka消费者协调器组件图](https://gitee.com/syllr/images/raw/master/uPic/kafka/kafka%E6%B6%88%E8%B4%B9%E8%80%85%E5%8D%8F%E8%B0%83%E5%99%A8%E7%BB%84%E4%BB%B6%E5%9B%BE.png)

## 组协调器(GroupCoordinator)

每一个broker都有一个组协调器（一个broker中的组协调器会负责和这个broker中所有消费组的消费者的协调器进行连接），

GroupCoordinator 的作用：

- 负责对其管理的组员(消费者)提交的相关请求进行处理
- **与消费者之间建立连接，并从与之连接的消费者之间选出一个 leader**
- 当 leader 分配好消费者与分区的订阅关系后，会把结果发送给组协调器，组协调器再把结果返回给各个消费者
- 管理与之连接的消费者的消费偏移量的提交，将每个消费者的消费偏移量保存到kafka的内部主题中
- 通过心跳检测消费者与自己的连接状态
- 启动组协调器的时候创建一个定时任务，用于清理过期的消费组元数据以及过去的消费偏移量信息

GroupCoordinator 依赖的组件及其作用：

![组消费者组件图](https://gitee.com/syllr/images/raw/master/uPic/kafka/%E7%BB%84%E6%B6%88%E8%B4%B9%E8%80%85%E7%BB%84%E4%BB%B6%E5%9B%BE.png)

- KafkaConfig：实例化 OffsetConfig 和 GroupConfig
- ZkUtils：分消费者分配组协调器时从Zookeeper获取内部主题的分区元数据信息。
- GroupMetadataManager：负责管理 GroupMetadata以及消费偏移量的提交，并提供了一系列的组管理的方法供组协调器调用。GroupMetadataManager 不仅把 GroupMetadata 写到kafka内部主题中，而且还在内存中缓存了一份GroupMetadata，其中包括了组员(消费者)的元数据信息，例如消费者的 memberId、leaderId、分区分配关系，状态元数据等。状态元数据可以是以下五种状态： 
  - PreparingRebalance：消费组准备进行平衡操作
  - AwaitingSync：等待leader消费者将分区分配关系发送给组协调器
  - Stable：消费者正常运行状态，心跳检测正常
  - Dead：处于该状态的消费组没有任何消费者成员，且元数据信息也已经被删除
  - Empty：处于该状态的消费组没有任何消费者成员，但元数据信息也没有被删除，知道所有消费者对应的消费偏移量元数据信息过期。
- ReplicaManager：GroupMetadataManager需要把消费组元数据信息以及消费者提交的已消费偏移量信息写入 Kafka 内部主题中，对内部主题的操作与对其他主题的操作一样，先通过 ReplicaManager 将消息写入 leader 副本，ReplicaManager 负责 leader 副本与其他副本的管理。
- DelayedJoin：延迟操作类，用于监视处理所有消费组成员与组协调器之间的心跳超时
- GroupConfig：定义了组成员与组协调器之间session超时时间配置

## 消费者协调器和组协调器的交互

### 心跳

消费者协调器通过和组协调器发送心跳来维持它们和群组的从属关系以及它们对分区的所有权关系。只要消费者以正常的时间间隔发送心跳，就被认为是活跃的，说明它还在读取分区里的消息（消费者会单独启用一个线程来发送心跳）。

消费者心跳判断逻辑

* 如果消费者停止发送心跳的时间足够长，会话就会过期，组协调器认为它已经死亡，就会触发一次再均衡。
* 如果消费者一直在发送心跳，但是消费者执行pull()函数的间隔超过session.timeout.ms这个配置的时间间隔，即sessionTimeout超时，则会被标记为当前协调器处理断开（这种被称为liveLock，活锁，这个消费者被称为活锁状态，超时的原因有很多，有可能是因为消费逻辑里面有耗时操作，比如调用第三方借接口的时候第三方接口超时了），此时，会将消费者移除，重新分配分区和消费者的对应关系（触发分区再均衡）。

重平衡的通知机制正是通过心跳线程来完成的。当协调者决定开启新一轮重平衡后，它会将“REBALANCE_IN_PROGRESS”封装进心跳请求的响应中，发还给消费者实例。当消费者实例发现心跳响应中包含了“REBALANCE_IN_PROGRESS”，就能立马知道重平衡又开始了，这就是重平衡的通知机制。

### 分区再均衡

发生分区再均衡的3种情况：

- 一个新的消费者加入群组时，它读取的是原本由其他消费者读取的消息。
- 当一个消费者被关闭或发生崩溃时，它就离开群组，原本由它读取的分区将由群组里的其他消费者来读取。如果一个消费者主动离开消费组，消费者会通知组协调器它将要离开群组，组协调器会立即触发一次再均衡，尽量降低处理停顿。如果一个消费者意外发生崩溃，没有通知组协调器就停止读取消息，组协调器会等待几秒钟，确认它死亡了才会触发再均衡。在这几秒钟时间里，死掉的消费者不会读取分区里的消息。
- 在主题发生变化时，比如管理员添加了新的分区，会发生分区重分配。

分区的所有权从一个消费者转移到另一个消费者，这样的行为被称为**分区再均衡**。再均衡非常重要，它为消费者群组带来了高可用性和伸缩性（我们可以放心地添加或移除消费者），不过在正常情况下，我们并不希望发生这样的行为。在再均衡期间，消费者无法读取消息，造成整个群组一小段时间的不可用。另外，当分区被重新分配给另一个消费者时，消费者当前的读取状态会丢失，它有可能还需要去刷新缓存，在它重新恢复状态之前会拖慢应用程序。

![分区均衡流程图](https://gitee.com/syllr/images/raw/master/uPic/20210819173752vIqATG.png)

### leader 消费者分配分区的策略

在消费者端，重平衡分为两个步骤：分别是加入组和等待领导者消费者（Leader Consumer）分配方案。这两个步骤分别对应两类特定的请求：JoinGroup 请求和 SyncGroup 请求。

当消费者要加入群组时，它会向群组协调器发送一个 JoinGroup 请求。第一个加入群组的消费者将成为leader消费者。leader消费者从组协调器那里获得群组的成员列表（列表中包含了所有最近发送过心跳的消费者，它们被认为是活跃的），并负责给每一个消费者分配分区。

每个消费者的消费者协调器在向组协调器请求加入组时，都会把自己支持的分区分配策略报告给组协调器(轮询或者是按跨度分配或者其他)，组协调器选出该消费组下所有消费者都支持的的分区分配策略发送给leader消费者，leader消费者根据这个分区分配策略进行分配。

完毕之后，leader消费者把分配情况列表发送给组协调器，消费者协调器再把这些信息发送给所有消费者。每个消费者只能看到自己的分配信息，只有leader消费者知道群组里所有消费者的分配信息。这个过程会在每次再均衡时重复发生，**Kafka2.3及以前的版本在整个重平衡的过程中所有的消费者都是停止消费的类似于JVM中的STW**（因为要重新分配分区，所以在分配的过程中每个消费者都需要停下自己正在消费的分区），2.4版本用了新的重平衡方法，可以做到**将一次全局重平衡，改成每次小规模重平衡，直至最终收敛平衡的过程**。。

### 消费者入组过程

- 消费者创建后，消费者协调器会选择一个负载较小的节点，向该节点发送寻找组协调器的请求
-  KafkaApis 处理请求，调用返回组协调器所在的节点
- 找到组协调器后，消费者协调器申请加入消费组，发送 JoinGroupRequest请求
- KafkaApis 调用 handleJoinGroup() 方法处理请求 
  - 把消费者注册到消费组中
  - 把消费者的clientId与一个UUID值生成一个memberId分配给消费者
  - 构造器该消费者的MemberMetadata信息
  - 把该消费者的MemberMetadata信息注册到GroupMetadata中
  - 第一个加入组的消费者将成为leader
- 把处理JoinGroupRequest请求的结果返回给消费者
- 加入组成功后，进行分区再均衡

# ZOOKEEPER节点

![Kafka ZK结点数据图](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%20ZK%E7%BB%93%E7%82%B9%E6%95%B0%E6%8D%AE%E5%9B%BE.png)

Kafka 2.8.0 用自管理的Quorum代替ZooKeeper管理元数据，官方称这个为 "Kafka Raft metadata mode"，即KRaft mode

KRaft最大的好处在于移除了ZooKeeper，这样我们无需维护zk集群，只要维护Kafka集群就可以了，当然，2.8.0版本还有很多未完善的地方，可能还不适合应用在生产环境。

## admin

该目录下znode只有在有相关操作时才会存在，操作结束时会将其删除

/admin/reassign_partitions用于将一些Partition分配到不同的broker集合上。对于每个待重新分配的Partition，Kafka会在该znode上存储其所有的Replica和相应的Broker id。该znode由管理进程创建并且一旦重新分配成功它将会被自动移除。

## broker

即/brokers/ids/[brokerId]）存储“活着”的broker信息。

topic注册信息（/brokers/topics/[topic]），存储该topic的所有partition的所有replica所在的broker id，第一个replica即为preferred replica，对一个给定的partition，它在同一个broker上最多只有一个replica,因此broker id可作为replica id。

## controller

/controller -> int (broker id of the controller)存储当前controller的信息

/controller_epoch -> int (epoch)直接以整数形式存储controller epoch，而非像其它znode一样以JSON字符串形式存储。

![KafkaZK节点数据图](https://gitee.com/syllr/images/raw/master/uPic/kafka/KafkaZK%E8%8A%82%E7%82%B9%E6%95%B0%E6%8D%AE%E5%9B%BE-20210818212316648.jpeg)

# 生产者

<img src="https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E7%94%9F%E4%BA%A7%E8%80%85%E7%BB%84%E4%BB%B6%E5%9B%BE-20210818212322054.png" alt="Kafka生产者组件图" style="zoom: 50%;" />

生产者发送流程如下图：

![Kafka生产者写入流程](https://gitee.com/syllr/images/raw/master/uPic/kafka/Kafka%E7%94%9F%E4%BA%A7%E8%80%85%E5%86%99%E5%85%A5%E6%B5%81%E7%A8%8B.png)

1. producer先从zookeeper的 "/brokers/.../state"节点找到该partition的leader

2. producer将消息发送给该leader

3. leader将消息写入本地log

4. followers从leader pull消息，写入本地log后向leader发送ACK

5. leader收到所有ISR中的replication的ACK后，增加HW（high watermark，最后commit 的offset）并向producer发送ACK

## 生产者属性

* bootstrap.servers

  > 该属性指定 broker 的地址清单，地址的格式为 host:port。清单里不需要包含所有的 broker 地址，生产者会从给定的 broker 里查找到其他 broker 的信息。不过建议至少要 提供两个 broker 的信息，一旦其中一个宕机，生产者仍然能够连接到集群上。

* key.serializer

  > broker 希望接收到的消息的键和值都是字节数组。生产者接口允许使用参数化类型，因 此可以把 Java 对象作为键和值发送给 broker。这样的代码具有良好的可读性，不过生 产者需要知道如何把这些 Java 对象转换成字节数组。key.serializer 必须被设置为一 个实现了 org.apache.kafka.common.serialization.Serializer 接口的类，生产者会使 用这个类把键对象序列化成字节数组。

* value.serializer

  > 与 key.serializer 一样，value.serializer 指定的类会将值序列化。如果键和值都是字 符串，可以使用与 key.serializer 一样的序列化器。如果键是整数类型而值是字符串， 那么需要使用不同的序列化器。

## 发送方式

* 同步
* 异步（需要指定回调函数）
* 发送并忘记（单向）

生产者发送的时候是有一个缓冲区，所以请求并不是一个消息一个消息的发送，而是通过缓冲区把同一个批次的请求一起发出去（同一个批次的消息都是同一个分区的），Kafka提供了很多的配置参数来调整批次的发送频率，比如可以选择调整这个缓冲区的大小，比如配置缓冲区的最大消息条数，缓冲区的最大消息size。

* 

## 消息的键

消息可以手动指定一个key，这个key类似于RocketMQ的MessageKey，可以是业务相关的，比如订单号，可以设置也可以不设置，如果设置了的话，生产者在分区的时候会使用基于key的分区策略（就是用key hash），确保同一个key的消息都发送到同一个分区（这个通过key来hash的过程是Kafka内部实现的，RocketMQ里面需要自己实现），如果key为 null，并且使用了默认的分区器，那么记录将被随机地发送到主题内各个可用 的分区上。分区器使用轮询(Round Robin)算法将消息均衡地分布到各个分区上。

## 选择分区

如果有指定的Key，则通过Key的散列值把消息映射到特定的分区，如果没有指定Key则通过分区策略来进行分区，默认的分区策略是轮询，还可以自己实现分区策略。

# 消费者

消息由生产者发布到Kafka集群后，会被消费者消费。消息的消费模型有两种：推送模型（push）和拉取模型（pull）。

基于推送模型（push）的消息系统，由消息代理记录消费者的消费状态。消息代理在将消息推送到消费者后，标记这条消息为已消费，但这种方式无法很好地保证消息被处理。比如，消息代理把消息发送出去后，当消费进程挂掉或者由于网络原因没有收到这条消息时，就有可能造成消息丢失（因为消息代理已经把这条消息标记为已消费了，但实际上这条消息并没有被实际处理）。如果要保证消息被处理，消息代理发送完消息后，要设置状态为“已发送”，只有收到消费者的确认请求后才更新为“已消费”，这就需要消息代理中记录所有的消费状态，这种做法显然是不可取的。

Kafka采用拉取模型，由消费者自己记录消费状态，每个消费者互相独立地顺序读取每个分区的消息。如下图所示，有两个消费者（**不同消费者组**）拉取同一个主题的消息，消费者A的消费进度是3，消费者B的消费进度是6。消费者拉取的最大上限通过最高水位（watermark）控制，生产者最新写入的消息如果还没有达到备份数量，对消费者是不可见的。这种由消费者控制偏移量的优点是：消费者可以按照任意的顺序消费消息。比如，消费者可以重置到旧的偏移量，重新处理之前已经消费过的消息；或者直接跳到最近的位置，从当前的时刻开始消费。

<img src="https://gitee.com/syllr/images/raw/master/uPic/kafka/%E6%B6%88%E8%B4%B9%E8%80%85%E9%80%9A%E8%BF%87%E9%AB%98%E6%B0%B4%E4%BD%8D%E6%8B%89%E5%8F%96%E6%95%B0%E6%8D%AE%E5%9B%BE.png" alt="消费者通过高水位拉取数据图" style="zoom:150%;" />

生产者发布的所有消息会一致保存在Kafka集群中，不管消息有没有被消费。用户可以通过设置保留时间来清理过期的数据，比如，设置保留策略为两天。那么，在消息发布之后，它可以被不同的消费者消费，在两天之后，过期的消息就会自动清理掉。

## 消费者心跳

[每一个消费者都有一个消费者协调器，消费者协调器负责和broker协调器进行心跳交互](#心跳)，通过消费者心跳监控，如果消费者被判断为超时或宕机，则该消费者就会被踢出消费组，触发消费者再均衡。

## 拉取消息

[消费者通过消费者协调器和组协调器通信](#协调器)（协调器中有消费者负载均衡的逻辑，确定了哪个消费者从哪个分区拉取消息），每次pull的消息的数量通过配置控制，一次可以拉取多个消息

## 进度管理

Kafka 不会像rocketMQ一样需要得到消费者的确认，这是 Kafka 的一个独特之处。相反，消费者可以使用 Kafka 来追踪消息在分区里的位置(偏移量，因为消费者的消费进度都存在本地，然后定时提交到Kafka)。

假设一个分区中有 10 条消息，位移分别是 0 到 9。某个Consumer 应用已消费了 5 条消息，这就说明该 Consumer 消费了位移为 0 到 4 的 5 条消息，此时 Consumer 的位移是 5，指向了下一条消息的位移。

**Consumer 需要向 Kafka 汇报自己的位移数据，这个汇报过程被称为提交位移**（Committing Offsets，实现的逻辑是往一个__consumer_offsets主题提交数据，在老的版本中是直接修改ZK节点中的数据，但是ZK节点不适合大量写操作，所以改成直接向一个特定的主题提交数据，因为Kafka是顺序写的天然就有很高的写性能）。因为 Consumer 能够同时消费多个分区的数据，所以位移的提交实际上是在分区粒度上进行的，即Consumer 需要为分配给它的每个分区提交各自的位移数据。

怎么确定往__consumer_offsets这个主题的哪个分区提交数据：用消费组的groupID进行hash来确定往哪个分区提交数据，所以同一个主题的所有的分区的进度都存在同一个broker中。

Kafka每次提交进度数据都是往consumer_offsets提交消息，每次都会新增一条消息，位移主题的 Key中保存 3 部分内容：GourpId，主题名，分区号，Value为进度数据，消费者不停的消费消息就会不停的提交进度数据，这样consumer_offsets中的消息就会无限扩张，Kafka 使用 Compact 策略来删除位移主题中的过期消息，避免该主题无限期膨胀，Compact 的过程就是扫描日志的所有消息，剔除那些过期的消息，然后把剩下的消息整理在一起。

![img](https://gitee.com/syllr/images/raw/master/uPic/20210822133344qg7Ycg.jpg)

所以从上图就可以看出，Kafka在判断进度信息的时候只会看最后一次提交的进度信息（即以最新的为准，不会以最大的为准），所以该主题中的消息数比较少，broker内存中会维护offsetMap。

位移主题用来记住位移，那么这个位移主题的位移由谁来记住呢？位移主题的位移由Kafka内部的Coordinator自行管理。

### 自动提交

最简单的提交方式是让消费者自动提交偏移量。如果 enable.auto.commit 被设为 true，那 么每过 5s，消费者会自动把从 poll() 方法接收到的最大偏移量提交上去。提交时间间隔 由 auto.commit.interval.ms 控制，默认值是 5s。与消费者里的其他东西一样，自动提交 也是在轮询里进行的。消费者每次在进行轮询时会检查是否该提交偏移量了，如果是，那 么就会提交从上一次轮询返回的偏移量。

不过，在使用这种简便的方式之前，需要知道它将会带来怎样的结果。

假设我们仍然使用默认的 5s 提交时间间隔，在最近一次提交之后的 3s 发生了再均衡，再 均衡之后，消费者从最后一次提交的偏移量位置开始读取消息。这个时候偏移量已经落后 了 3s，所以在这 3s 内到达的消息会被重复处理。可以通过修改提交时间间隔来更频繁地 提交偏移量，减小可能出现重复消息的时间窗，不过这种情况是无法完全避免的。

### 手动提交

把 auto.commit.offset 设为 false，让应用程序决定何时提交偏移量

#### 同步提交 commitSync()

使用 commitSync() 提交偏移量最简单也最可靠。这个 API 会提交由 poll() 方法返回的最新偏移量，提交成 功后马上返回，如果提交失败就抛出异常。

#### 异步提交 commitAsync()

手动提交有一个不足之处，在 broker 对提交请求作出回应之前，应用程序会一直阻塞，这样会限制应用程序的吞吐量。我们可以通过降低提交频率来提升吞吐量，但如果发生了再 均衡，会增加重复消息的数量。

这个时候可以使用异步提交 API。我们只管发送提交请求，无需等待 broker 的响应。在成功提交或碰到无法恢复的错误之前，commitSync() 会一直重试，但是 commitAsync() 不会，这也是 commitAsync() 不好的一个地方。它之所以不进行重试，是因为在它收到 服务器响应的时候，可能有一个更大的偏移量已经提交成功。假设我们发出一个请求用 于提交偏移量 2000，这个时候发生了短暂的通信问题，服务器收不到请求，自然也不会 作出任何响应。与此同时，我们处理了另外一批消息，并成功提交了偏移量 3000。如果 commitAsync() 重新尝试提交偏移量 2000，它有可能在偏移量 3000 之后提交成功。这个时 候如果发生再均衡，就会出现重复消息。

#### 同步和异步组合提交

一般情况下，针对偶尔出现的提交失败，不进行重试不会有太大问题，因为如果提交失败 是因为临时问题导致的，那么后续的提交总会有成功的。但如果这是发生在关闭消费者或再均衡前的最后一次提交，就要确保能够提交成功。打个比方如果程序提交的偏移量为2000，提交失败了，不进行重试，如果后面再进行一次3000偏移量的提交成功了，最终提交的偏移量都是3000，没有影响。

如果提交的间隔中发生了再均衡，这时消费者最新的偏移量没有提交，再均衡之后消费者会从最后一次提交的偏移量位置开始读取消息，这样会出现重复消费的情况，所以在关闭消费或者在再均衡前要进行一次提交。这样就能避免重复消费。

那还有什么情况会发生重复消费呢：当消费者消费成功，准备提交消费进度时，如果消费者突然重启，这个时候消费进度会丢失，还是可能出现重复消费的问题，还有可能因为网络的问题导致提交进度失败，然后消费者服务重启，这样也会丢失消费进度，所以重复消费的问题是无法避免的，最好还是要业务端做好业务幂等。

#### 提交特定的偏移量

无论是同步还是异步提交都提供了提交特定偏移量的api，如果一次拉取了1000条消息，可以分批次每次提交100的偏移量。

### 消费重试和死信队列

Kafka本身没有像RocketMQ一样提供消费重试和死信队列的功能，需要在客户端自己实现重试功能，如果一个主题为TopicA的消息消费失败了，可以把原消息的topic信息保存到扩展字段中，然后手动把消息投递到重试队列中，实现重试的功能，如果消息重试超过次数，就可以把消息手动投递到死信队列中。

# Kafka事务

Kafka 的事务概念类似于我们熟知的数据库提供的事务。在数据库领域，事务提供的安全性保障是经典的 ACID，即原子性（Atomicity）、一致性 (Consistency)、隔离性 (Isolation) 和持久性 (Durability)。

Kafka 自 0.11 版本开始也提供了对事务的支持，目前主要是在 read committed 隔离级别上做事情。它能保证多条消息原子性地写入到目标分区，同时也能保证 Consumer 只能看到事务成功提交的消息。

Kafka 事务机制支持了跨分区的消息原子写功能。具体来说，Kafka 生产者在同一个事务内提交到多个分区的消息（比如在一个事务里面先发送TopicA的消息，然后再发送TopicB的消息），要么同时成功，要么同时失败。这一保证在生产者运行时出现异常甚至宕机重启之后仍然成立。

此外，同一个事务内的消息将以生产者发送的顺序，唯一地提交到 Kafka 集群上。也就是说，事务机制从某种层面上保证了消息被恰好一次地提交到 Kafka 集群。众所周知，恰好一次送达在分布式系统中是不可能实现的。这个论断有一些微妙的名词重载问题，但大抵没错，所有声称能够做到恰好一次处理的系统都在某个地方依赖了幂等性。

Kafka 的事务机制被广泛用于现实世界中复杂业务需要保证一个业务领域中原子的概念被原子地提交的场景。

例如，一次下单流水包括订单生成消息和库存扣减消息，如果这两个消息在历史上由两个主题分管，那么它们在业务上的原子性就要求 Kafka 要利用事务机制原子地提交到 Kafka 集群上。

还有，对于复杂的流式处理系统，Kafka 生产者的上游可能是另一个流式处理系统，这个系统可能有着自己的一致性方案。为了跟上游系统的一致性方案协调，Kafka 就需要提供一个尽可能通用且易于组合的一致性机制，即灵活的事务机制，来帮助实现端到端的一致性。

![Kafka事务提交流程](https://gitee.com/syllr/images/raw/master/uPic/20210820200956mcKxyj.svg)

实现事务机制最关键的概念就是事务的唯一标识符（ TransactionalID ），Kafka 使用 TransactionalID 来关联进行中的事务。TransactionalID 由用户提供，这是因为 Kafka 作为系统本身无法独立的识别出宕机前后的两个不同的进程其实是要同一个逻辑上的事务。

对于同一个生产者应用前后进行的多个事务，TransactionalID 并不需要每次都生成一个新的。这是因为 Kafka 还实现了 ProducerID 以及 epoch 机制。这个机制在事务机制中的用途主要是用于标识不同的会话，同一个会话 ProducerID 的值相同，但有可能有多个任期。ProducerID 仅在会话切换时改变，而任期会在每次新的事物初始化时被更新。这样，同一个 TransactionalID 就能作为跨会话的多个独立事务的标识。

生产者通过调用 initTransactions 方法初始化事务上下文。首要做的事情就是找到 Kafka 集群负责管理当前事务的事务协调者（ TransactionCoordinator ），向其申请 ProducerID 资源。初始的 ProducerID 及 epoch 都是未初始化的状态。

生产者一侧的事务管理者（ TransactionManager ）收到相应的方法调用之后先后发送查找事务协调者的信息和初始化 ProducerID 的信息。事务相关的所有元数据信息都会由客户端即生产者一侧的事务管理者和服务端即 Kafka 集群的一个 Broker 上的事务协调者交互完成。

1. 一开始，生产者并不知道哪个 Broker 上有自己 TransactionalID 关联的事务协调者。逻辑上，所有事务相关的需要持久化的数据最终都会写到一个特殊的主题 transaction_state 上。这跟管理消费者消费位点的特殊主题 consumer_offsets 构成了目前 Kafka 系统里唯二的特殊主题。对于一个生产者或者说被 TransactionalID 唯一标识的事务来说，它的事务协调者就是该事务的元数据最终存储在 transaction_state 主题上对应分区的分区首领。对于一个具体的事务来说，它的元数据将被其 TransactionalID 的哈希值的绝对值模分区数的分区所记录，这也是常见的确定分区的方案。生产者将查找事务协调者的信息发送到集群的任意一个 Broker 上，由它计算出实际的事务协调者，获取对应的节点信息后返回给生产者。这样，生产者就找到了事务协调者。随后，生产者会向事务协调者申请一个 **ProducerID** 资源，这个资源包括 ProducerID 和对应的 epoch 信息。事务协调者收到对应请求后，将会首先判断同一个 TransactionalID 下的事务的状态，以应对好跨会话的事务的管理。
2. Producer开始顺序向不同的主题发送消息
   1. 生产者在发送事务中的消息的时候，会将消息对应的分区添加到事务管理器中去，如果这个分区此前没被添加过，那么事务管理器会在下一次发送消息之前插入一条 AddPartitionsToTxnRequest 请求来告诉 Kafka 集群的事务协调者参与事务的分区的信息。事务协调者收到这条信息之后，将会更新事务的元数据，并将元数据持久化到 __transaction_state 中。对于生产者发送的消息，仍然和一般的消息生产一样采用 ProduceRequest 请求。除了会在请求中带上相应的 TransactionalID 信息和属于事务中的消息的标识符，它跟生产者生产的普通信息别无二致。如果消费者没有配置读已提交的隔离级别，那么这些消息在被 Kafka 集群接受并持久化到主题分区中时，就已经对消费者可见而且可以被消费了。事务中的消息的顺序性保证也是在发送事务的时候检查的。生产者此时已经申请到了一个 ProducerID 资源，当它向一个分区发送消息时，内部会有一个消息管理器为每个不同的分区维护一个顺序编号（ SequenceNumber ）。相应地，Kafka 集群也会为每个 ProducerID 到每个分区的消息生产维护一个顺序编号。ProducerRequest 请求中包含了顺序编号信息。如果 Kafka 集群看到请求的顺序编号跟自己的顺序编号是连续的，即比自己的顺序编号恰好大一，那么接受这条消息。否则，如果请求的顺序编号大一以上，则说明是一个乱序的消息，直接拒绝并抛出异常。如果请求的顺序编号相同或更小，则说明是一个重复发送的消息，直接忽略并告诉客户端是一个重复消息。
   2. producer往TopicA发送消息
   3. producer往TopicB发送消息
3. 在一个事务相关的所有消息都发送完毕之后，生产者就可以调用 commitTransaction 方法来提交整个事务了，对于事务中途发生异常的情形，也可以通过调用 abortTransaction 来丢弃整个事务。这两个操作都是将事务状态转移到终结状态，彼此之间有许多相似点。无论是提交还是丢弃，生产者都是给事务协调者发送 EndTxnRequest 请求，请求中包含一个字段来判断是提交还是丢弃。事务协调者在收到这个请求后，首先更新事务状态到 PrepareAbort 或 PrepareCommit 并更新状态到 __transaction_state 中。
4. TransactionCoordinator向所有涉及到的分区（TopicA的某个分区，TopicB的某个分区）发送commit信息，让各个分区上的消息提交（这样消费者就可以消费了）
5. 更新__transaction_state的事务成功信息

# 问题讨论

## Kafka 只是消息引擎系统吗

Kafka 是消息引擎系统，也是分布式流处理平台

根据官网的介绍，ApacheKafka®是一个分布式流处理平台，它主要有3种功能：

1. 发布和订阅消息流，这个功能类似于消息队列，这也是kafka归类为消息队列框架的原因
2. 以容错的方式记录消息流，kafka以文件的方式来存储消息流
3. 可以在消息发布的时候进行处理

### 使用场景

- 在系统或应用程序之间构建可靠的用于传输实时数据的管道，消息队列功能
- 构建实时的流数据处理程序来变换或处理数据流，数据处理功能

### 应用场景

- 消息队列
- 行为跟踪
- 元信息监控
- 日志收集
- 流处理
- 事件源
- 持久性日志（commit log）

可以看到Kafka最主要的作用除了作为消息队列之外，还有实时流式计算

一般流式计算会与批量计算相比较。在流式计算模型中，输入是持续的，可以认为在时间上是无界的，也就意味着，永远拿不到全量数据去做计算。同时，计算结果是持续输出的，也即计算结果在时间上也是无界的。流式计算一般对实时性要求较高，同时一般是先定义目标计算，然后数据到来之后将计算逻辑应用于数据。同时为了提高计算效率，往往尽可能采用增量计算代替全量计算。

### 批处理

批处理主要操作大容量静态数据集，并在计算过程完成后返回结果。

- 有界：批处理数据集代表数据的有限集合
- 持久：数据通常始终存储在某种类型的持久存储位置中
- 大量：批处理操作通常是处理极为海量数据集的唯一方法

批处理非常适合需要访问全套记录才能完成的计算工作。例如在计算总数和平均数时，必须将数据集作为一个整体加以处理，而不能将其视作多条记录的集合。这些操作要求在计算进行过程中数据维持自己的状态。

需要处理大量数据的任务通常最适合用批处理操作进行处理。无论直接从持久存储设备处理数据集，或首先将数据集载入内存，批处理系统在设计过程中就充分考虑了数据的量，可提供充足的处理资源。由于批处理在应对大量持久数据方面的表现极为出色，因此经常被用于对历史数据进行分析。
Apache Hadoop是一种专用于批处理的处理框架

### 流处理

流处理系统会对随时进入系统的数据进行计算。相比批处理模式，这是一种截然不同的处理方式。流处理方式无需针对整个数据集执行操作，而是对通过系统传输的每个数据项执行操作。

流处理中的数据集是“无边界”的，这就产生了几个重要的影响：

- 完整数据集只能代表截至目前已经进入到系统中的数据总量。
- 工作数据集也许更相关，在特定时间只能代表某个单一数据项。
- 处理工作是基于事件的，除非明确停止否则没有“尽头”。处理结果立刻可用，并会随着新数据的抵达继续更新。

Apache Storm是一种侧重于极低延迟的流处理框架，也许是要求近实时处理的工作负载的最佳选择。该技术可处理非常大量的数据，通过比其他解决方案更低的延迟提供结果。

Kafka Stream是Apache Kafka从0.10版本引入的一个新Feature。它是提供了对存储于Kafka内的数据进行流式处理和分析的功能。

## Kafka集群磁盘计算

* 磁盘的选择：机械磁盘还是SSD

  > 机械硬盘和SSD的速度差别主要在于随机读取，顺序读写虽然SSD依然会比机械磁盘快，但是差距不是很大，Kafka是把消息顺序存储在磁盘上的，所以如果考虑性价比的话机械硬盘会更好（SSD的顺序读写的速度是机械的4倍）。

* 磁盘要不要组RAID

  > RAID可以一定程度增加磁盘的读写速度，同时多块磁盘增加了抗风险的能力，但是本身Kafka就有副本机制，默认副本数是3个（3个里面包含了Leader本身），如果考虑性价比不需要组RAID

* 集群磁盘容量计算

  > 计算容量主要考虑这几个方面
  >
  > * 消息数，消息大小，消息保留时间
  > * 每条消息都有.index索引和.timeIndex索引，索引的大小都是固定的8bit
  > * 集群的副本数配置（默认是3）
  > * 是否开启压缩，压缩比是多少（默认0.75）

  最后流出百分之30的冗余空间

  磁盘配置参数log.dirs：指定了 Broker 需要使用的若干个文件目录路径，可以配置多个，如果有多个磁盘，最好保证这些目录挂载到不同的物理磁盘上。这样做有两个好处：

  * 提升读写性能：比起单块磁盘，多块物理磁盘同时读写数据有更高的吞吐量。
  * 能够实现故障转移：即 Failover。这是 Kafka 1.1 版本新引入的强大功能。要知道在以前，只要 Kafka Broker 使用的任何一块磁盘挂掉了，整个 Broker 进程都会关闭。但是自 1.1 开始，这种情况被修正了，坏掉的磁盘上的数据会自动地转移到其他正常的磁盘上，而且 Broker 还能正常工作。还记得上一期我们关于 Kafka 是否需要使用 RAID 的讨论吗？这个改进正是我们舍弃 RAID 方案的基础：没有这种 Failover 的话，我们只能依靠 RAID 来提供保障。

## Kafka为什么不像Mysql一样让从节点对外提供读服务

如果允许follower副本对外提供读服务（主写从读），首先会存在数据一致性的问题，消息从主节点同步到从节点需要时间，可能造成主从节点的数据不一致。主写从读无非就是为了减轻leader节点的压力，将读请求的负载均衡到follower节点，如果Kafka的分区相对均匀地分散到各个broker上，同样可以达到负载均衡的效果，没必要刻意实现主写从读增加代码实现的复杂程度

而与之对应的是rocketMq一般情况下也是不让从节点对外提供读服务，但是当主节点的消息堆积超过物理内存的百分之40的时候，主节点会让消费者到从节点拉取消息

## Kafka如何实现消息的精确一次消费

消息处理语义分为三种

- 最多一次(at most once):消息可能丢失也可能被处理，但最多只会处理一次
- 至少一次(at least once):消息不会丢失，但可能被处理多次
- 精确一次(exactly once):消息被处理且只会被处理一次

Kafka默认的模式是至少一次模式，因为默认情况下producer可能会重复发送，如果想要满足最多一次，可以把kafka的生产者发送失败重试次数设置为1（即不重试）那么，怎么才能实现**精确一次**呢，答案是borker要对消息进行幂等验证

Kafka的保证一个分区，且producer和broker的同一次连接的消息的幂等性

实现逻辑很简单:

- 区分producer会话

producer每次启动后，首先向broker申请一个全局唯一的pid，用来标识本次会话。

- 消息检测

message_v2 增加了sequence number字段，producer每发一批消息，seq就加1。

broker在内存维护(pid,seq)映射，收到消息后检查seq，如果，

```xml
new_seq=old_seq+1: 正常消息；

new_seq<=old_seq : 重复消息；

new_seq>old_seq+1: 消息丢失；
```

- producer重试

producer在收到明确的的消息丢失ack，或者超时后未收到ack，要进行重试。

## Kafka有哪些选举

### 控制器选举

Kafka Controller的选举是依赖Zookeeper来实现的，在Kafka集群中哪个broker能够成功创建/controller这个临时（EPHEMERAL）节点他就可以成为Kafka Controller。

### 分区leader选举

从AR列表中找到第一个存活的副本，且这个副本在目前的ISR列表中，与此同时还要确保这个副本不处于正在被关闭的节点上。

### 消费组leader选举

如果消费组内还没有leader，那么第一个加入消费组的消费者即为消费组的leader。如果leader消费者退出了消费组，那么会重新选举一个新的leader，在GroupCoordinator中消费者的信息是以HashMap的形式存储的，其中key为消费者的member_id，而value是消费者相关的元数据信息。leaderId表示leader消费者的member_id，它的取值为HashMap中的第一个键值对的key，这种选举的方式基本上和随机无异。总体上来说，消费组的leader选举过程是很随意的。
