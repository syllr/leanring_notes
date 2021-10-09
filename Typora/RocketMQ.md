# RocketMQ

## 架构

![RocketMq架构图](https://raw.githubusercontent.com/syllr/image/main/uPic/20211008213030Na20wX.png)

## 路由中心NameServer

### NameServer作用

NameServer 中维护着 Producer集群、Broker集群、 Consumer集群的服务状态。通过定时发送心跳数据包进行维护更新各个服务的状态

当有新的Producer 加入集群时，通过上报自身的服务信息，及获取各个 Broker Master的信息（Broker 地址、Topic、Queue 等信息），这样就可以决定把对应的Topic消息存储到哪个Broker、哪个Queue 上。Consumer 同理

NameServer 可以部署多个，多个NameServer互相独立，不会交换消息。Producer、Broker、Consumer 启动的时候都需要指定多个 NameServer，各个服务的信息会同时注册到多个 NameServer 上，从而能到达高可用

### 为什么选择自己开发NameServer

目前可以作为服务发现组件有很多，如etcd、consul，zookeeper等：

那么为什么rocketmq选择自己开发一个NameServer，而不是使用这些开源组件呢？特别的，Zookeeper其提供了Master选举、分布式锁、数据的发布和订阅等诸多功能RocketMQ设计之初时参考的另一款消息中间件Kafka就使用了Zookeeper。

事实上，在RocketMQ的早期版本，即MetaQ 1.x和MetaQ 2.x阶段，也是依赖Zookeeper的。但MetaQ 3.x（即RocketMQ）却去掉了ZooKeeper依赖，转而采用自己的NameServer。

而RocketMQ的架构设计决定了只需要一个轻量级的元数据服务器就足够了，只需要保持最终一致，而不需要Zookeeper这样的强一致性解决方案，不需要再依赖另一个中间件，从而减少整体维护成本。

nameServer的最大的特点就是各个nameServer之间是互不通信的，无法进行数据复制

根据CAP理论，RocketMQ在名称服务这个模块的设计上选择了AP，而不是CP：

![CAP图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/CAP%E5%9B%BE.png)

- 一致性(Consistency)：Name Server 集群中的多个实例，彼此之间是不通信的，这意味着某一时刻，不同实例上维护的元数据可能是不同的，客户端获取到的数据也可能是不一致的。
- 可用性(Availability)：只要不是所有NameServer节点都挂掉，且某个节点可以在指定之间内响应客户端即可。
- 分区容错(Partiton Tolerance)：对于分布式架构，网络条件不可控，出现网络分区是不可避免的，只要保证部分NameServer节点网络可达，就可以获取到数据。具体看公司如何实施，例如：为了实现跨机房的容灾，可以将NameServer部署的不同的机房，某个机房出现网络故障，其他机房依然可用，当然Broker集群/Producer集群/Consumer集群也要跨机房部署。

### NameServer如何保证数据的最终一致

NameServer作为一个名称服务，需要提供服务注册、服务剔除、服务发现这些基本功能，但是NameServer节点之间并不通信，在某个时刻各个节点数据可能不一致的情况下，如何保证客户端可以最终拿到正确的数据。下面分别从服务注册、服务下线，路由发现三个角度进行介绍

#### 服务注册

因为rocketMq中的各个nameServer之间是不通信的，为了保证NameServer中的数据一致，RocketMQ路由注册是通过 Broker与 NameServer的心跳功能实现的。 Broker启动时向集群中**所有的**NameServer发送心跳包，然后每隔30s向集群中**所有**NameServer发送心跳包，NameServer收到Broker心跳包时会更新brokerLiveTable缓存中BrokerLivelnfo中对应的Broker的lastUpdateTimestamp，然后NameServer每隔10s扫描brokerLiveTable，如果连续120s没 有收到心跳包，NameServer将移除该 Broker的路由信息同时关闭 Socket连接

NameServer在处理心跳包的时候，存在多个Broker同时操作一张Broker表，为了防止并发修改Broker表导致不安全，路由注册操作引入了ReadWriteLock读写锁，这个设计亮点允许多个Broker并发读，保证了消息发送时的高并发，但是同一时刻NameServer只能处理一个Broker心跳包，多个心跳包串行处理。这也是读写锁的经典使用场景，即读多写少

#### 服务下线

Broker 每隔 30s向集群里面所有NameServer发送一个心跳包，心跳包中包含 BrokerId、Broker地址、Broker名称、 Broker所属集群名称、Broker关联的 FilterServer列表。 但是如果 Broker若机 ，NameServer无法收到心跳包，此时 NameServer如何来剔除这些失效的Broker呢? Name Server会每隔10s扫描brokerLiveTable状态表，如果BrokerLive的lastUpdateTimestamp的时间戳距当前时间超过 120s，则认为Broker失效，移除该 Broker, 关闭与Broker连接，并同时更新topicQueueTable、 brokerAddrTable、 brokerLiveTable、 filterServerTable

RocktMQ有两个触发点来触发路由删除

1. NameServer定时扫描 brokerLiveTable检测上次心跳包与当前系统时间的时间差， 如果时间戳大于120s，则需要移除该 Broker信息

2. Broker在正常被关闭的情况下，会执行 unregisterBroker指令。 由于不管是何种方式触发的路由删除，路由删除的方法都是一样的，就是从 topicQueueTable、 brokerAddrTable、 brokerLiveTable、 filterServerTable删除与该Broker相关的信息

#### 路由发现（服务发现）

路由发现是客户端的行为，这里的客户端主要说的是生产者和消费者。具体来说：

- 对于生产者，可以发送消息到多个Topic，因此一般是在发送第一条消息时，才会根据Topic从NameServer获取路由信息。（这个以前我遇到过，我们的qa环境的topic是不用申请的，但是必须要生产者发送一条消息后才会新建，如果生产者没有发送第一条消息，消费者去注册订阅这个topic的时候会报错）
- 对于消费者，订阅的Topic一般是固定的，所以在启动时就会拉取

RocketMQ路由发现是非实时的，当Topic路由出现变化后，NameServer不主动推送给客户端 ，而是由客户端定时拉取主题最新的路由 。根据主题名称拉取路由信息的命令编码为: GET ROUTEINTO BY_TOPIC，对于生产者，那种本地缓存有信息但是客户端调用不通的broker，生产者会在一段时间内将其从可调用列表中排除，然后调用下一个broker发送消息，而对于消费者，因为一个消费者的是通过负载均衡来确定消费的queue的，所以如果broker宕机（且从节点没有变成主节点）则该的boker的消息将无法被消费，以前消费这个broker的消费者，会在下一次消费者执行负载均衡的时候去消费另一个broker中的消息

## TOPIC

### topic存储模型

![Topic存储模型](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/Topic%E5%AD%98%E5%82%A8%E6%A8%A1%E5%9E%8B.png)

RocketMQ的队列是有读写权限的区分的，设置读写权限的区分主要是为了进行负载均衡，弹性的扩缩容

* 负载均衡

  如果因为某种原因造成一个broker的消息堆积（但是别的broker中的队列是正常的，因为一个队列只会被一个消费者消费，这样会造成这个消费者的压力过大）可以手动将这个broker上的所有的队列的写权限关闭，让别的服务器分担压力，这也是一个临时解决堆积的办法。

* 弹性缩容

  如果想要下掉几台broker，可以先把这几台broker上的所有的队列都设置为不可写，这样新的请求不会发到这几台broker上，等到这几台broker上的消息都已经被消费队列完全清空了之后再下掉服务器。

### topic创建

#### 自动创建

![RocketMq自动创建topic流程](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/RocketMq%E8%87%AA%E5%8A%A8%E5%88%9B%E5%BB%BAtopic%E6%B5%81%E7%A8%8B.png)

如果开启自动创建topic的功能，会在producer第一次发送mq的时候在第一次发送的时候在该broker中创建该topic，因为broker每隔30S都会向集群中所有的nameServer发送同步信息如果这30S中producer没有持续发送消息让所有的broker都接受到消息并且在自己本地创建对应的topic，那么在30S之后等nameServer中的信息更新之后，该topic就只会存在第一次发送的broker中了

#### 手动创建

通过updateTopic命令手动的创建topic，如果指定的队列的数量大于broker数量则会平均分配队列到每个broker中

## 消息生产

### 1. 查找主题路由信息

> 先根据topic信息查找本地缓存中的topic路由信息，如果找不到就向nameServer发送请求找（如果nameServer中没有消息，且开启了自动创建topic，nameServer就会返回一个默认的路由信息）

### 2. 选择消息队列

> 通过第一步已经找到对应的topic路由信息即topicInfo对象，知道该topic一共有多少队列，并且每个队列的borker地址也知道，topicInfo对象里面都维护了一个ThreadLocalIndex类型的sendWhichQueue属性（底层用的是ThreadLocal<Integer>），每次发送成功之后这个属性会increase自增一下，然后每次发送的时候根据这个值取模，算出往哪个queue（这样就确定了往哪个broker发送）发送消息，并且这个queue需要具有写权限，如果没有写权限则继续获取下一个有写权限的queue，这样就实现了producer端的负载均衡，rocketMQ设置队列读写权限的原因是因为可以通过队列读写权限的开关实现负载均衡以及平滑扩容，平滑缩容（先把要下掉的broker中的队列都设置为只读不写，等broker中的消息消费完无积压之后就下掉该机器）

### 3. 消息发送

消息发送之前会为每条消息分配一个全局唯一的**Message Key** （也可以用户自己定义，与之相对应的是**MessageID** ，MessageID是每个消息存储到broker中的时候broker根据该消息的偏移量和brokerId拼起来的一个唯一标识，可以通过messageID直接查到broker和偏移量），如果消息体默认超过 4K(compressMsgBodyOverHowmuch), 会对消息体采用zip压缩，并设置消息的系统标记为 MessageSysFlag.COMPRESSED_FLAG。如果是事务 Prepared消息，则设置消息的系统标记为 MessageSysFlag.TRANSACTION_ PREPARED TYPE。发送的方法有同步，异步，单向三种，异步的话需要指定回掉函数

如果是同步的话会通过配置确定发送失败重试次数，如果是异步或者单向的话只会重试一次

## 消息存储

### RocketMQ存储架构

RocketMQ主要存储的文件包括Commitlog文件、 ConsumeQueue文件、 IndexFile文件还有一个checkpoint文件。 RocketMQ将所有主题的消息存储在同一个文件中，确保消息发送时顺序写文件，尽最大的能力确保消息发送的高性能与高吞吐量。但由于消息中间件一般是基于消息主题的订阅机制，这样便给按照消息主题检索消息带来了极大的不便。为了提高消息消费的效率， RocketMQ 引入了ConsumeQueue消息队列文件，每个消息主题包含多个消息消费队列，每一个消息队列有一个消息文件。IndexFile 索引文件，其主要设计理念就是为了加速消息的检索性能，根据消息的属性快速从Commitlog文件中检索消息。RocketMQ是一款高性能的消息中间件，存储部分的设计是核心，存储的核心是IO访问性能

![RocketMq消息存储架构图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/RocketMq%E6%B6%88%E6%81%AF%E5%AD%98%E5%82%A8%E6%9E%B6%E6%9E%84%E5%9B%BE.png)



* CommitLog

  > 消息存储文件，所有消息主题的消息都存储在CommitLog中

* ConsumeQueue

  > 消费进度文件，consumeQueue把一个CommitLog分成多个不同的分区，存储的是每个队列的消息在commitLog中的offset，size，tagHashCode，一个ConsumerQueue对应的是一个topic的一个队列，所有topic的消息都存储在CommitLog中，但是consumeQueue存储了CommitLog的offSet，相当于CommitLog的索引，消费者只需要顺序的读取ConsumeQueue中的offSet，然后去CommitLog中根据offSet获取对应的消息
  >
  > ![ConsumeQueue结构图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/ConsumeQueue%E7%BB%93%E6%9E%84%E5%9B%BE.png)

* IndexFile

  > ![IndexFile结构](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/IndexFile%E7%BB%93%E6%9E%84.png)
  >
  > IndexFile其实是用文件存储了一个HashMap，key为MessageKey，value为该消息的offSet，具体的存储逻辑是，500W个hash槽存储messageKey作为key，2000W个Index条目存储offSet，如果出现hash冲突，index条目里面的preIndexNo指向下一个条目（链表法），通过获取offSet到CommitLog中获取具体的消息信息，然后拿出来和key比对，就能找到对应的消息信息

### 存储文件组织与内存映射

RocketMQ通过使用内存映射文件来提高 IO访问性能，无论是CommitLog、 ConsumeQueue还是IndexFile，单个文件都被设计为固定长度，如果一个文件写满以后再创建一个新文件，文件名就为该文件第一条消息对应的全局物理偏移量

RocketMQ使用MappedFile、MappedFileQueue来封装存储文件

* MappedFileQueue:对连续物理存储的抽象(存储目录的抽象，就是代指所有的CommitLog文件)，MapedFileQueue可以看作是**${ROCKET_HOME}/store/commitlog**文件夹,此文件夹下有多个MappedFile，每次存储消息的时候都是先获取lastMappedFile，如果没有就新建一个，然后往mappedFile中append数据

* MappedFile:就是每一个磁盘上的CommitLog文件的内存映射，消息字节写入Page Cache缓存区（commit方法），或者原子性地将消息持久化的刷盘（flush方法）

* transientStorePool：TransientStorePool，短暂的存储池。RocketMQ单独创建一个MappedByteBuffer内存缓存池，用来临时存储数据，数据先写入该内存映射中，然后由commit线程定时将数据从该内存复制到与目的物理文件对应的内存映射中 

  > 可以通过配置启用transientStorePool，transientStorePool初始化的时候使用了mlock()方法，将当前堆外内存一直锁定在内存中，避免被进程将内存交换到磁盘 ，同时也提供了一种读写分离的机制，写往transientStorePool中写，读就直接读取MappedFile（即page cache）以空间换时间，减少了并发竞争

#### 刷盘机制

rocketMq提供了三种刷盘策略，一种同步的两种异步的，同步即为每次写入内存之后立即刷入磁盘，异步刷盘其实本质上都是一样的，之所以有两种一部刷盘策略是因为引入了transientStorePool之后，需要先把transientStorePool中的数据批量刷入到mappedFile的内存中，然后再把mappedFile中的内存刷入到磁盘中，如果没有transientStorePool，就直接把mappedFile的内存批量刷入到磁盘中

### 实时更新消息消费队列与索引文件

消息消费队列文件、消息属性索引文件都是基于 CommitLog文件构建的 ， 当消息生产者提交的消息存储在Commitlog文件中 ， ConsumeQueue、 IndexFile需要及时更新，否则消息无法及时被消费，根据消息属性查找消息也会出现较大延迟。 RocketMQ通过开启一个线程 ReputMessageService来准实时转发 CommitLog文件更新事件，相应的任务处理器根据转发的消息及时更新ConsumeQueue、 IndexFile文件，ReputMessageService每次间隔1毫秒，就会去读取CommitLog文件中所有待推送的消息然后然后调用相应方法分别构建consumeQueue中的条目和IndexFile

### 过期文件删除

由于RocketMQ操作 CommitLog、 ConsumeQueue文件是基于内存映射机制并在启动的时候会加载 commitlog、 ConsumeQueue目录下的所有文件，为了避免内存与磁盘的浪费，不可能将消息永久存储在消息服务器上，所以需要引入一种机制来删除己过期的文件。 RocketMQ顺序写Commitlog文件 、 ConsumeQueue文件，所有写操作全部落在最后一个CommitLog或ConsumeQueue文件上，之前的文件在下一个文件创建后将不会再被更新 。 RocketMQ清除过期文件的方法是：如果非当前写文件在一定时间间隔内没有再次被更新， 则认为是过期文件，可以被删除， RocketMQ 不会关注这个文件上的消息是否全部被消费 。 默认每个文件的过期时间为 72 小时 ，通过在Broker配置文件中设置fileReservedTime来改变过期时间，单位为小时。

## 消息消费

消息消费以组的模式开展，一个消费组内可以包含多个消费者，每一个消费组可订阅多个主题，消费组之间有集群模式与广播模式两种消费模式 。集群模式，主题下的同一条 消息只允许被其中一个消费者消费 。 广播模式，主题下的同一条消息将被集群内的所有消费者消费一次。消息服务器与消费者之间的消息传送也有两种方式:推模式、拉模式 。所谓的拉模式，是消费端主动发起拉消息请求，而推模式是消息到达消息服务器后，推送给消息消费者 。 RocketMQ 消息推模式的实现基于拉模式，在拉模式上包装一层，一个拉取任务完成后开始下一个拉取任务。消息队列负载机制遵循一个通用的思想:一个消息队列同一时间只允许被一个消费者消费（实际上在特殊情况下一个队列会被多个消费者消费，这算是个坑），一个消费者可以消费多个消息队列。

RocketMQ 支持局部顺序消息消费，也就是保证同一个消息队列上的消息顺序消费。 不支持消息全局顺序消费， 如果要实现某一主题的全局顺序消息消费， 可以将该主题的队列数设置为1，牺牲吞吐量。

RocketMQ 支持两种消息过滤模式:表达式(TAG、 SQL92)与类过滤模式。

消息拉模式，主要是由客户端手动调用消息拉取API，而消息推模式是消息服务器主动将消息推送到消息消费端，推模式是基于拉模式的所以直接通过推模式研究RocketMQ消息消费实现。

### 消费者启动

1. 构建主题订阅信息

- 订阅目标topic
- 订阅重试主题消息。RocketMQ消息重试是以消费组为单位，而不是主题，消息重试主题名为 %RETRY% + 消费组名。消费者在启动的时候会自动订阅该主题，参与该主题的消息队列负载。

1. 初始化消息进度。如果消息消费是集群模式，那么消息进度保存在Broker上; 如果是广播模式，那么消息消费进度存储在消费端。
2. 根据是否是顺序消费，创建消费端消费线程服务。ConsumeMessageService 主要负责消息消费，内部维护一个线程池。

### 消息拉取

RocketMQ 使用一个单独的线程 PullMessageService 来负责消息的拉取。

```java
public void run() {
    log.info(this.getServiceName() + " service started");
    while (!this.isStopped()) {
        try {
          	//pullRequest是用来负载均衡的，可以理解为是拉取消息的请求，如果这一次拉取消息成功之后consumer会把pullRequest从新放入PullRequestQueue中，pullRequest由RebalanceService生成的，RebalanceService每20S执行一次负载均衡
            PullRequest pullRequest = this.pullRequestQueue.take();
            this.pullMessage(pullRequest);
        } catch (InterruptedException ignored) {
        } catch (Exception e) {
            log.error("Pull Message Service Run Method exception", e);
        }
    }
    log.info(this.getServiceName() + " service end");
}
```

PullMessageService 从服务端拉取到消息后，会根据消息对应的消费组，转给该组对应的 ProcessQueue，而 ProcessQueue 是 MessageQueue 在消费端的重现、快照。 PullMessageService 从消息服务器默认每次拉取 32 条消息，按消息的队列偏移量顺序存放在 ProcessQueue 中，PullMessageService 然后将消息提交到消费者消费线程池，消息成功消费后从 ProcessQueue 中移除。

> ProcessQueue是消费者用来存放拉取到的待消费的消息的队列，一个processQueue对应着broker中的一个consumeQueue

消息拉取分为 3 个主要步骤。

1. 消息拉取客户端消息拉取请求封装。
2. 消息服务器查找并返回消息。
3. 消息拉取客户端处理返回的消息。

### 发送拉取请求

1. 判断队列状态，如果不需要拉取则退出
2. 进行消息拉取流控

- 消息处理总数
- 消息偏移量跨度

1. 查询路由表，找到要发送的目标 Broker 服务器，如果没找到就更新路由信息
2. 如果消息过滤模式为类过滤，则需要根据主题名称、broker地址找到注册在 Broker上的 FilterServer 地址，从 FilterServer 上拉取消息，否则从 Broker 上拉取消息
3. 发送消息

### Broker组装消息

1. 根据订阅信息，构建消息过滤器

- tag 过滤器只会过滤 tag 的 hashcode，为了追求高效率

1. 根据主题名称与队列编号获取消息消费队列
2. 根据拉取消息偏移量，进行校对，如何偏移量不合法，则返回相应的错误码
3. 如果待拉取偏移量大于 minOffset 并且小于 maxOffs 时，从当前 offset 处尝试拉取 32 条消息，根据消息队列偏移量(ConsumeQueue)从 CommitLog 文件中查找消息
4. 根据 PullResult 填充 responseHeader 的 nextBeginOffset、 minOffset、 maxOffset
5. 如果主 Broker 工作繁忙，会设置 flag 建议消费者下次从 Slave 节点拉取消息
6. 如果 CommitLog 标记可用并且当前节点为主节点，则更新消息消费进度

### 客户端处理消息

1. 解码成消息列表，并进行消息过滤

- 这里之所以还要进行过滤，是因为 Broker 为了追求效率只会根据 tag 的 hashcode 进行过滤，真实 key string 的对比，下放到 Consumer 上进行

1. 更新 PullRequest 的下一次拉取偏移量，如果过滤后没有一条消息的话，则立即触发下次拉取
2. 首先将拉取到的消息存入 ProcessQueue，然后将拉取到的消息提交到 ConsumeMessageService 中供消费者消费，该方法是一个异步方法，也就是 PullCallBack 将消息提交到 ConsumeMessageService 中就会立即返回
3. 等待消费者中的线程池消费完这批消息之后，发送消费ACK给broker

```java
        switch (this.defaultMQPushConsumer.getMessageModel()) {
            case BROADCASTING:
                for (int i = ackIndex + 1; i < consumeRequest.getMsgs().size(); i++) {
                    MessageExt msg = consumeRequest.getMsgs().get(i);
                    log.warn("BROADCASTING, the message consume failed, drop it, {}", msg.toString());
                }
                break;
            case CLUSTERING:
                List<MessageExt> msgBackFailed = new ArrayList<MessageExt>(consumeRequest.getMsgs().size());
                for (int i = ackIndex + 1; i < consumeRequest.getMsgs().size(); i++) {
                    MessageExt msg = consumeRequest.getMsgs().get(i);
                  	//这里是每一个消息都会发送一个ack
                    boolean result = this.sendMessageBack(msg, context);
                    //如果消费失败的ack发送失败了就在客户端进行重试
                    if (!result) {
                        msg.setReconsumeTimes(msg.getReconsumeTimes() + 1);
                        msgBackFailed.add(msg);
                    }
                }

                if (!msgBackFailed.isEmpty()) {
                    consumeRequest.getMsgs().removeAll(msgBackFailed);

                    this.submitConsumeRequestLater(msgBackFailed, consumeRequest.getProcessQueue(), consumeRequest.getMessageQueue());
                }
                break;
            default:
                break;
        }
```



1. 根据拉取延时，适时进行下一次拉取

![消费者拉取消息流程图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/%E6%B6%88%E8%B4%B9%E8%80%85%E6%8B%89%E5%8F%96%E6%B6%88%E6%81%AF%E6%B5%81%E7%A8%8B%E5%9B%BE.jpeg)

### 消费Q&A

* 消息重试

  * 生产者重试

    > 要看发送消息的方法，如果是同步的就从TimesWhenSendFailed配置中获取重试次数，如果是异步发送的话就只重试1次

  * 消费者重试

    > 如果消费者消费消息没有返回成功或则没有返回或者超时，broker会把consumeQueue中的消息添加到一个以**%RETRY%+consumerGroup**为topic名称的重试队列，考虑到异常恢复起来需要一些时间，会为重试队列设置多个重试级别，同时消息消费返回的不同的错误信息也会为消息设置不同的重试级别（每个不同的级别都是单独的一个queue），每个重试级别都有与之对应的重新投递延时，重试次数越多投递延时就越大，然后一个job会不断的遍历不同的重试queue，到了时间的queue就会重新添加到consumeQueue中重新消费，如果消息的重试次数达到了最高的次数，就会把改消息移入到死信队列中，死信队列是为了让保存信息让人工处理。同时死信队列和正常的消息的存放时间是一致的。

* 一个consumeQueue对应一个消费者

  > broker里面的consumeQueue和producer以及consumer的对应逻辑是，一个consunmeQueue只对应一个消费者，一个消费者可能对应多个consumeQueue，producer发送消息的时候对于每一个topic，在topicInfo对象里面都维护了一个ThreadLocalIndex类型的sendWhichQueue属性（底层用的是ThreadLocal<Integer>），每次发送成功之后这个属性会increase自增一下，然后每次发送的时候根据这个值取模，算出往哪个queue发送消息，这样就让每一个queue中的消息按照发送顺序排列了，在消费者消费的时候，因为一个queue对应一个消费者，只需要让这个消费者按照顺序消费这个queue里面的消息即可，具体的逻辑是消费者批量拉取到消息之后，如果是顺序消费就让线程池一次只消费一个消息，如果消息消费失败就重试，直到消费成功为止（与之相对应的是并发消费模式下，是一次将所有拉取到的消息放到线程池中消费，而且每一个消息只会消费一次，消费的结果不论成功和失败都会发送给broker）

* 为什么会有重复消费的问题，在那些情况下mq会重复消费，消息会丢失么

  > 消息从生产到被消费有三个阶段
  >
  > * 生产阶段：Producer新建消息，然后通过网路将消息投递给broker
  > * 存储阶段：消息将会存储在broker的磁盘中
  > * 消息阶段：consumer将会从broker中拉取消息
  >
  > 以上任何一个阶段都会发生消息的丢失，rocketMq在这三个阶段分别采用了一些方式来防止消息的丢失（但是在某些配置下还是不能保证消息100%不丢失），但是这也造成了消息的重复的问题（当然还有别的原因也造成了消息的重复）
  >
  > * 生产阶段：生产者每次像broker发送消息的时候，无论是同步还是异步发送，都需要收到broker返回的ack信息才能标志着这次的发送成功，如果没有收到ack消息或者是收到发送失败的消息就需要重试，但是这样就会造成消息重复发送的情况
  > * 存储阶段：因为消息到了broker端根据broker的刷盘配置，如果是异步刷盘，也就是说消息只要被broker记录到内存中之后立即返回给发送者成功的消息，然后通过等缓冲区满了再进行刷盘，当broker突然宕机，内存中还没有来得及持久化到磁盘的数据就会丢失，还有在broker集群部署的时候，如果主从同步的时候，主从同步没有采用同步的方法进行（也就是主节点等待从节点都复制完成的时候才算同步完成，给producer发送确认返回），如果这个时候主节点宕机并且**不可恢复**，从节点变成主节点之后，未来的及同步的那部分数据就算丢失了
  > * 消费阶段：消费者通过pullRequest从broker中拉取数据，然后执行相应业务逻辑。一旦执行成功，将会返回成功的状态给broker，如果broker没有收到消息确认响应或者收到失败的响应，broker就会将这些消息放到重试队列中，等下一次消费者来拉取消息还会拉取到这些消息，进行重试，这样就避免了消费者在消费的过程中发生异常或者网络波动，导致消息消费不到的情况，但是同时因为集群模式下消费进度是由broker管理的，这就涉及到消费者中的消费进度和broker进行同步的问题，因为消费者中的消费进度是做了一个缓存，然后定时向broker进行同步，如果在同步的间隙消费者宕机并且丢失了本地消费进度缓存，该消费者再次启动的时候就会重复拉取以前的消息重复消费，同时由于消费者的负载均衡是在消费者端进行的，即由消费者选择（topic肯定是确定的）从哪个broker的哪个queue中拉取消息，而消费者本地的负载均衡策略完全是依靠nameServ返回的消费者列表和broker列表，如果因为网络的波动导致不同的消费者获取到的消费者列表和broker列表不同，则通过负载均衡计算出来结果就不一样，可能出现多个消费者消费同一个队列的情况（这只是一时的，rockerMq的负载均衡保证了最终一致性），就会出现重复消费的情况。

* 集群模式下消费者拉取message和广播模式下拉取message的区别

  > 集群模式下每个消费者都会订阅所有队列中的内容，实现很容易，而集群模式下需要在client做负载均衡，因为client需要知道请求哪一个broker来获取信息
  >
  > 集群模式下因为负载均衡可能会出现在一段时间内将一个consumeQueue分配给两个消费者
  >
  
* 消息的顺序消费是怎么实现的

  > RocketMq支持局部顺序消费，但是不支持全局的，换句话说针对Topic中的每个queue是可以按照FIFO进行消费的，也就是说如果是要求每一个订单的消息需要顺序消费的场景的话，就需要将这个订单的所有mq都发送到同一个queue中，同时rocketMq也提供了相应的api，在发送的时候可以自己实现一个MessageQueueSelector，支持把订单号通过一个参数传入，把这个参数进行hash，然后取模，计算出应该发送到的queue，MessageQueueSelector就是每次producer在发送消息的时候选择queue的选择器，然后消费者每次通过pullrequest拉取消息放入到processQueue中，然后把processQueue中的消息放到线程池中进行消费，如果是顺序消费的话在把processQueue中的消息放入到线程池之前需要向broker申请consumeQueue的锁，只有申请锁成功了之后才会进行消息的消费，同时线程池中消息的消费也是按顺序的。

* 消费者拉取消息时长轮询的逻辑

  > 长轮询对于请求的发送方来说是没有不同的，对于请求接收方的区别是，一般的请求，如果没有结果就立即返回，但是长轮询是如果没有返回结果就讲请求暂存，然后如果在超时时间只能有结果就将结果返回，这样做的好处是不会阻塞服务方

* 延时消息的实现原理

  >  消息的生产者生产出延迟消息发送到broker中，broker会判断是否是延迟消息，如果是延迟消息，每一个延迟消息的主题都会暂时更改为SCHEDULE_TOPIC_XXXX并且把原来的主题消息放到消息的扩展字段中，并且根据延迟级别延迟消息变更了新的消息id，然后持久化到commit log中，然后定时任务会去读取不同的延迟队列中的消息，如果到了延迟的时间，就从消息的扩展字段中获取到消息原始的主题和队列信息，并且加入到对应consumeQueue中，这样就可以让对应的消费者在延迟之后消费到这个消息
  
* 消费重试实现原理

  > 消费者通过pullrequest从broker中批量的获取到消息，放入到消费者的processQueue中，然后另一个任务不停的从processQueue中构建consumeRequst放到线程池中，线程池的中的线程在消费掉一个之后，会将消费的结果发送给broker（消费成功or消费失败），如果消费失败，broker会将该消息放到重试队列中（一个以%RERTY%开头的topic），也就是说只要broker中的consumeQueue永远只会往前走。而如果是集群模式消费，那消费进度是保存在broker中的，但是每个消费者其实都保存这个每一个topic的gourp的每一个queue的消费进度，每一次线程池中的消费有了结果就会将这个消费进度更新，然后通过消费者中的一个定时任务将这些消费进度同步到broker，这个就是offset的提交更新，如果client宕机，导致offset无法正确提交给borker，重启消费者的时候就会重新拉取已经消费了的信息

* 过滤模式：1：tag过滤模式，2：类过滤模式

  > 基于tag的过滤模式是通过consume中的hashTag来在broker中进行一次过滤然后把消息发送到consume端，然后在sonsume端会再进行一次消息tag的过滤，因为hash会有冲突，不同的tag可能会有同样的hash值
  >
  > 基于类模式过滤是指在 Broker 端运行 1 个或多个消息过滤服务器（ FilterServer ),RocketMQ 允许消息消费者自定义消息过滤实现类并将其代码上传到 FilterServer 上，消息消 费者 向 FilterServer 拉取消息， FilterServer 将消息消费者的拉取命令转发到Broker，然后对返回的消息执行消息过滤逻辑，最终将消息返回给消费端，其实本质傻姑娘filterServer是一个代理，consume在进行pullRequest请求的时候如果是类过滤模式，就会基于topic来查找对应的filterServer的地址，然后把请求的地址从broker的地址改为filterServer的地址



## 高可用

*   从服务器怎么从主服务器拉取数据

  >  从服务器是从主服务器的commit log直接获取拉取数据
  >
  > ![rocketMq主从同步流程](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/rocketMq%E4%B8%BB%E4%BB%8E%E5%90%8C%E6%AD%A5%E6%B5%81%E7%A8%8B.png)

* 主，从服务器都在运行过程中，消息消费者是从主拉取消息还是从从拉取？

  > 默认情况下都从主服务器进行拉取，但是当主服务器的资源不够时（即消息堆积使得内存占用超过了百分之40）就会去从从服务器拉取数据，主从同步引入的主要目的就是消息堆积的内容默认超过物理内存的40%，或则是消息回溯延迟超过内存的百分之40，则消息读取则由从服务器来接管（主服务器返回消息的时候会向消费者返回建议请求的brokerId），实现消息的读写分离，避免主服务IO抖动严重，因为如果消息堆积过多，或则消息回溯延迟过长，这两个场景其实都是一个意思，即消费者只是读取CommitLog和ConsumeQueue的前面部分，而生产者写入的是CommitLog和ConsumeQueue的后面部分，这样的话broker需要将从消费者开始的offSet到CommitLog和ConsumeQueue结尾全部拉入内存，所以这个时候把读的任务分给从服务器，就可以在主服务器只把最新的CommitLog和ConsumeQueue拉入到内存，而从服务器只需要把需要消费的那一部分的CommitLog和ConsumeQueue拉入到内存，这样主从服务器都不需要将中间暂时不需要读写的数据拉入到内存，节省了内存。
  >
  > ![消息堆积读写分离示意图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/%E6%B6%88%E6%81%AF%E5%A0%86%E7%A7%AF%E8%AF%BB%E5%86%99%E5%88%86%E7%A6%BB%E7%A4%BA%E6%84%8F%E5%9B%BE.png)
  >
  > 如上图，如果不进行读写分离，主库需要将mappedFile1到mappedFile5都载入到内存，如果进行读写分离，主只需要载入mappedFile5到内存，从只需要把mappedFile1载入到内存，所以当消息堆积，或则消息回溯等任何可以造成读写的offSet过大的情况下都会进行读写分离。

* RocketMQ主从同步架构中，如果主服务器宕机，从服务器会接管消息消费，此时消息消费进度如何保持，当主服务器恢复后，消息消费者是从主拉取消息还是从从服务器拉取，主从服务器之间的消息消费进度如何同步？

  > 在消费者消费的时候会主动向消费的broker更新自己的消费进度（集群模式下），然后不管这个broker是主还是从，都会向其他的broker更新消息的消费进度

## 消息事务

![消息事务流程图](https://gitee.com/syllr/images/raw/master/uPic/rocketMQ/%E6%B6%88%E6%81%AF%E4%BA%8B%E5%8A%A1%E6%B5%81%E7%A8%8B%E5%9B%BE.png)

###  RocketMQ事务流程概要

RocketMQ实现事务消息主要分为两个阶段：正常事务的发送及提交、事务信息的补偿流程
整体流程为：

- 正常事务发送与提交阶段

1. 生产者发送一个半消息给MQServer（半消息是指消费者暂时不能消费的消息）
2. 服务端响应消息写入结果，半消息发送成功
3. 开始执行本地事务
4. 根据本地事务的执行状态执行Commit或者Rollback操作

- 事务信息的补偿流程

1. 如果MQServer长时间没收到本地事务的执行状态会向生产者发起一个确认回查的操作请求
2. 生产者收到确认回查请求后，检查本地事务的执行状态
3. 根据检查后的结果执行Commit或者Rollback操作
   补偿阶段主要是用于解决生产者在发送Commit或者Rollback操作时发生超时或失败的情况。

### RocketMQ事务流程关键

1. 事务消息在一阶段对用户不可见
   事务消息相对普通消息最大的特点就是一阶段发送的消息对用户是不可见的，也就是说消费者不能直接消费。这里RocketMQ的实现方法是原消息的主题与消息消费队列，然后把主题改成 `RMQ_SYS_TRANS_HALF_TOPIC` ，这样由于消费者没有订阅这个主题，所以不会被消费。
2. 如何处理第二阶段的失败消息？在本地事务执行完成后会向MQServer发送Commit或Rollback操作，此时如果在发送消息的时候生产者出故障了，那么要保证这条消息最终被消费，MQServer会像服务端发送回查请求，确认本地事务的执行状态。当然了rocketmq并不会无休止的的信息事务状态回查，默认回查15次，如果15次回查还是无法得知事务状态，RocketMQ默认回滚该消息。
3. 消息状态
   事务消息有三种状态：

- **TransactionStatus.CommitTransaction**：提交事务消息，消费者可以消费此消息
- **TransactionStatus.RollbackTransaction**：回滚事务，它代表该消息将被删除，不允许被消费。
- **TransactionStatus.Unknown** ：中间状态，它代表需要检查消息队列来确定状态。

## 消息回溯

一般消息在消费完成之后就被处理了，之后再也不能消费到该条消息。消息回溯正好相反，是指消息在消费完成之后，还能消费到之前被消费掉的消息。对于消息而言，经常面临的问题是“消息丢失”，至于是真正由于消息中间件的缺陷丢失还是由于使用方的误用而丢失一般很难追查，如果消息中间件本身具备消息回溯功能的话，可以通过回溯消费复现“丢失的”消息进而查出问题的源头之所在。消息回溯的作用远不止与此，比如还有索引恢复、本地缓存重建，有些业务补偿方案也可以采用回溯的方式来实现。

rocketMQ支持按照时间戳来进行消息回溯，RocketMQ提供了根据时间戳查找消息的功能，具体实现逻辑如下：

1. 首先根据时间戳定位到 ConsumeQueue 物理文件，就是从第一个文件开始找到第一个文件更新时间大于该时间戳的文件。
2. 然后对 ConsumeQueue 中的所有项，使用二分查找，查询每条记录对应的 CommitLog 的最后更新时间和要查询的时间戳（因为consumeQueue中的元素都是固定大小的，所以可以使用二分查找）
3. 最终找到与时间戳对应的 ConsumeQueue 偏移，或者离时间戳最近的消息的 ConsumeQueue 偏移

这样可以查找一个时间戳上的消息也可以查找一个时间范围内的所有消息

## 参考文章

[为什么在一段时间内RocketMQ的队列同时分配给了两个消费者？详细剖析消费者负载均衡中的坑](https://jaskey.github.io/blog/2020/11/26/rocketmq-consumer-allocate/)

[rocketMq消息消费](https://zhuanlan.zhihu.com/p/360911990)

[长轮询实现原理](https://www.jianshu.com/p/70800fe967fd)

[消费offset管理](https://www.jianshu.com/p/b4970f59a8b1)

[ack机制](https://blog.csdn.net/linuxheik/article/details/79579329)

[rocketMq消息事务](https://zhuanlan.zhihu.com/p/159573084)

