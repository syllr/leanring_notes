# 什么是ZooKeeper

Zookeeper是一个开源的分布式协同系统，Zookeeper的设计目标是将那些复杂且容易出错的分布式协同服务封装起来，抽象出一个高效可靠的原语集，并以一系列简单的接口提供给用户使用

## ZooKeeper应用场景

* 配置管理
* DNS服务
* 组成员管理
* 各种分布式锁

ZooKeeper适用于存储和系统相关的关键数据，比如元数据信息，不适合用于大数据量存储（因为ZooKeeper采用前领导模型，写性能相当于一个单机）

## 特性

- 顺序一致性（Sequential Consistency）
- 原子性（Atomicity）
- 单一视图（Single System Image）：同一客户端无论在什么时候连接哪个节点，都不会看到比自己之前看到的数据更早版本的数据
- 可靠性（Reliability ）
- 实时性（Timeliness ）

# Zookeeper数据模型

ZooKeeper的数据模型是层次模型（树行模型）。层次模型常见于文件系统，层次模型和key-value模型是两种主流的数据模型。ZooKeeper使用文件系统模型主要基于以下两点考虑：

1. 文件系统的树形结构便于表达数据之间的层次关系
2. 文件系统的树行结构便于为不同的应用分配独立的命名空间（namespace）

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20211008211639u0j4yl.png" alt="image-20211008211633207" style="zoom:50%;" />

ZooKeeper的层次模型称作data tree。Data tree的每个节点叫做znode。**不同于文件系统，每个节点都可以保存数据**。每个节点都有一个版本号（version）。版本从0开始计数。

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20211008211909RK0mVw.png" alt="image-20211008211859555" style="zoom:50%;" />

上图的data tree中有两个子树，一个用于应用1(/app1)和另一个用于应用2(/app2)。应用1的子树实现了一个简单的组成员协议：每个客户端进程pi创建一个znode p_i 在/app1下，只要/app1/p_i存在就代表进程pi在正常运行

## data tree接口

Zookeeper对外提供一个用来访问data tree的简化文件系统API：

* 使用UNIX风格的路径名来定位znode，例如/A/X表示znode A的子节点X。
* znode的数据只支持全量写入和读取，没有像通用文件系统那样支持部分写入和读取（就是说对于一个节点的更新是全量更新，相当于删除后重新赋值）
* data tree的API都是wait-free的，正在执行中的API调用不会影响其他API的完成
* data tree的API都是对文件系统的wait-free操作，**不直接提供锁这样的分布式协同机制。但是data tree的API非常强大，可以用来实现多种分布式协同机制。**

## znode分类

一个znode可以是持久性的，也可以是临时性的

1. 持久性的znode(persistent)：这样的znode在创建之后即使发生ZooKeeper集群宕机或者client宕机也不会丢失。
2. 临时性的znode(ephemeral)：client宕机或者client在指定的timeout时间内没有给ZooKeeper集群发消息，这样的znode就会消失。

znode可以是顺序性的，当生成一个顺序性节点之后，这个节点的子节点会关联一个唯一的单调递增整数，这个单调递增整数是znode名字的后缀，也就是说这个节点的所有子节点的后缀都是有序的。

3. 持久顺序性的znode(persistent_sequential)：znode除了具备持久性znode的特点之外，znode的名字具有顺序性，顺序就是在后面追加一个序列号, 序列号是由父节点管理的自增
4. 临时顺序性的znode(ephemeral_sequential)：znode除了具备临时性znode的特点之外，znode的名字具有顺序性，顺序就是在后面追加一个序列号, 序列号是由父节点管理的自增

# ZooKeeper架构

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20210930222211Oa3xtd.png" alt="image-20210930222209685" style="zoom: 33%;" />

应用使用ZooKeeper客户端库来使用ZooKeeper服务。ZooKeeper客户端负责和ZooKeeper集群的交互。ZooKeeper集群可以有两种模式

* standalone模式：只有一个节点
* quorum模式：包含多个节点

## Session

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20210930222526pdiqwV.png" alt="image-20210930222525063" style="zoom: 33%;" />

ZooKeeper客户端和ZooKeeper集群中的某个节点创建一个session。客户端可以主动关闭session。另外如果ZooKeeper节点没有在Session关联的timeout时间内收到客户端消息的话，ZooKeeper节点也会关闭session。另外ZooKeeper客户端库如果发现连接的ZooKeeper出错，会自动的和其他ZooKeeper节点建立连接

## Quorum模式

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20210930222815w5FnuM.png" alt="image-20210930222813653" style="zoom:50%;" />

处于quorum模式的ZooKeeper集群包含多个ZooKeeper节点。上图的ZooKeeper集群有3个节点，其中节点1是leader节点，节点2和节点3是follower节点。leader节点可以处理读写请求，follower只可以处理读请求。follower在接受到写请求时会把写请求转发给leader来处理。

## ZooKeeper保证的数据一致性

* 全局线性化写入：先到达leader的写请求会被先处理，leader决定写请求的执行顺序
* 客户端fifo顺序：来自给定的客户端的请求按照发送顺序执行（同一个客户端的命令是按照顺序执行的）

## Watch

Watch提供一个让客户端获取最新数据的机制。如果没有Watch机制，客户端需要不断的轮询ZooKeeper来查看是否有数据更新，这在分布式环境中是非常耗时的。客户端可以在读取数据的时候设置一个watcher，这样在数据更新时，客户端就会收到通知

![image-20211001091536637](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001091537INyvbx.png)

## 条件更新

设想用znode /c实现一个counter，使用set命令来实现自增1操作。条件更新场景：

1. 客户端1把/c更新到版本1，实现/c的自增1.
2. 客户端2把/c更新到版本2，实现/c的自增1.
3. 客户端1不知道/c已经被客户端2更新过了，还用过时的版本1去更新/c，更新失败。如果客户端1使用的是无条件更新，/c就会更新为2，没有实现自增1

![image-20211001092038758](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001092040wsAvAy.png)

# ZooKeeper角色

## Leader

 一个集群中只有一个Leader节点，Leader作为整个ZooKeeper集群的主节点，负责响应所有对ZooKeeper状态变更的请求。它会将每个状态更新请求进行排序和编号，以便保证整个集群内部消息处理的FIFO。

Leader会对所有写请求进行编号，并会同步给所有follower，最后决定是否commit，这个过程被称为一个事务。

## Follwer

除了响应本服务器上的读请求外，follower还要处理leader的提议，并在leader提交该提议时在本地也进行提交。Follower处理提议的过程已经在ZAB一章中描述过了。

另外需要注意的是，leader和follower构成ZooKeeper集群的法定人数，也就是说，只有他们才参与新leader的选举、响应leader的提议。

## Observer

如果ZooKeeper集群的读取负载很高，或者客户端多到跨机房，可以设置一些observer服务器，以提高读取的吞吐量。Observer和Follower比较相似，只有一些小区别：

* 首先observer不属于法定人数，即**不参加选举也不响应提议**；
* 其次是observer不需要将事务持久化到磁盘，一旦observer被重启，需要从leader重新同步整个名字空间。

不参与选举，不需要经过propose，ack，accept这些过程，只需要接收Leader的结果

Observer是ZooKeeper用来提升集群吞吐量的，同时在跨机房部署的时候可以把其中一个机房都部署成为Observer，来降低异地机房之间的延迟。

<img src="/Users/yutao/Library/Application Support/typora-user-images/image-20211001111629195.png" alt="image-20211001111629195" style="zoom:67%;" />

比如上图：我们需要部署一个北京和香港两地都可以使用的ZooKeeper服务。我们要求北京和香港的客户端延迟都低。因此，我们需要在北京和香港都部署ZooKeeper节点。我们假设leader节点在北京。如果不使用observer，那么每个来自香港的写请求都要涉及leader和每个香港follower节点之间的propose，ack和commit三个跨区域消息。解决的方案就是把香港的节点都设置成observer。上面提到的propose，ack和commit三个消息就变成了一个inform消息。

# ZooKeeper动态配置

## 手动调整集群成员

1. 停止整个ZooKeeper现有集群
2. 更改配置文件zoo.cfg的server.n项
3. 启动新集群的ZooKeeper节点

手动需要停止整个集群的服务，然后重新启动，这样不仅仅是服务被停止，还有可能会导致**已经提交的数据写入被覆盖**

![image-20211001112515298](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001112518yS6ktl.png)

* 最开始的时候节点2是leader，节点1和节点3都是follwer，节点3中的数据比节点1和节点2旧一点（没有<1, 3>这个数据），在这种情况下我们准备手动调整集群成员，加入节点4和节点5
* 我们需要把原来的1，2，3节点都停掉，然后开始启动节点，启动的顺序是先启动节点-4和节点-5，重启节点-1，节点-2和节点-3，这种时候因为每个节点的启动顺序都是不可控，有可能3，4，5节点先启动，形成了一个quorum。经过选举把节点3（因为节点4，5中都没有数据，节点3的数据完整性最高）选举成了leader。
* 节点1和节点2加入quorum之后，会和leader也就是节点3同步状态，这个时候节点1，2发现自己的<1, 3>这个数据是leader中没有的，就会被删除

这种删除已经提交的数据的情况是非常严重的情况。

## 3.5.0新特性-dynamic reconfiguration

可以在不停止ZooKeeper服务的前提下，调整集群成员，也不会发生已经提交的事务被覆盖的情况，可以借助这个新特性在集群的成员发生变更的时候采用单节点变更的方式增加或者删除成员，既防止了要节点重启顺序不一致出现事务丢失的情况，也防止了出现分区选出两个leader的情况。

# 请求处理链

![img](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001144801ylvC07.jpg)

不同的角色对于请求做出的处理是不同的，ZooKeeper中采用了责任链模式，让不同的角色对请求进行处理

## Leader

![img](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001144945IH2uBY.jpg)

* PrepRequestProcessor：请求预处理器。在Zookeeper中，那些会改变服务器状态的请求称为事务请求（创建节点、更新数据、删除节点、创建会话等），PrepRequestProcessor能够识别出当前客户端请求是否是事务请求。对于事务请求，PrepRequestProcessor处理器会对其进行一系列预处理，如创建请求事务头、事务体、会话检查、ACL检查和版本检查等。
* ProposalRequestProcessor：事务投票处理器。Leader服务器事务处理流程的发起者，对于非事务性请求，ProposalRequestProcessor会直接将请求转发到CommitProcessor处理器，不再做任何处理，而对于事务性请求，除了将请求转发到CommitProcessor外，还会根据请求类型创建对应的Proposal提议，并发送给所有的Follower服务器来发起一次集群内的事务投票。同时，ProposalRequestProcessor还会将事务请求交付给SyncRequestProcessor进行事务日志的记录。
* SyncRequestProcessor：事务日志记录处理器。用来将事务请求记录到事务日志文件中，同时会触发Zookeeper进行数据快照。
* AckRequestProcessor：负责在SyncRequestProcessor完成事务日志记录后，向Proposal的投票收集器发送ACK反馈，以通知投票收集器当前服务器已经完成了对该Proposal的事务日志记录。
* CommitProcessor：事务提交处理器。对于非事务请求，该处理器会直接将其交付给下一级处理器处理；对于事务请求，其会等待集群内针对Proposal的投票直到该Proposal可被提交，利用CommitProcessor，每个服务器都可以很好地控制对事务请求的顺序处理。
* ToBeAppliedRequestProcessor：该处理器有一个toBeApplied队列，用来存储那些已经被CommitProcessor处理过的可被提交的Proposal。其会将这些请求交付给FinalRequestProcessor处理器处理，待其处理完后，再将其从toBeApplied队列中移除。
* FinalRequestProcessor：用来进行客户端请求返回之前的操作，包括创建客户端请求的响应，针对事务请求，该处理还会负责将事务应用到内存数据库中去。

## Follwer

Follower也采用了责任链模式组装的请求处理链来处理每一个客户端请求，由于不需要对事务请求的投票处理，因此Follower的请求处理链会相对简单，其处理链如下

![img](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001145350GafwI3.jpg)

* FollowerRequestProcessor：其用作识别当前请求是否是事务请求，若是，那么Follower就会将该请求转发给Leader服务器，Leader服务器是在接收到这个事务请求后，就会将其提交到请求处理链，按照正常事务请求进行处理。
* SendAckRequestProcessor：其承担了事务日志记录反馈的角色，在完成事务日志记录后，会向Leader服务器发送ACK消息以表明自身完成了事务日志的记录工作。

## Observer

Observer充当观察者角色，观察Zookeeper集群的最新状态变化并将这些状态同步过来，其对于非事务请求可以进行独立处理，对于事务请求，则会转发给Leader服务器进行处理。Observer不会参与任何形式的投票，包括事务请求Proposal的投票和Leader选举投票。其处理链如下

![img](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001145459ZEGViu.jpg)

# 内存模型

## 内存数据

Zookeeper的数据模型是树结构，在内存数据库中，存储了整棵树的内容，Zookeeper会定时将这个数据存储到磁盘上。整个内存对象在 ZK 中对应的对象其实就是 `DataTree`

- 其实整个 ZK 的数据最终是存在一个哈希表（`ConcurrentHashMap`）中，key 是路径，而 value 则是对应的节点
- 节点包含了之前图中的：数据、**子节点列表**、权限、统计
- 子节点列表，**每一个节点都会维护一个子节点的列表，只记录儿子节点。孙子节点及以下都不记录**
- 统计数据是给客户端查询的，统计中的数据版本会被用在删除以及更新时作为乐观锁的版本号使用

![img](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001151334XogFy4.jpg)

* DataTree：DataTree是内存数据存储的核心，是一个树结构，代表了内存中一份完整的数据。DataTree不包含任何与网络、客户端连接及请求处理相关的业务逻辑，是一个独立的组件。
* DataNode：DataNode是数据存储的最小单元，其内部除了保存了结点的数据内容、ACL列表、节点状态之外，还记录了父节点的引用子节点列表两个属性，其他提供了对子节点列表进行操作的接口。
* ZKDatabase：Zookeeper的内存数据库，管理Zookeeper的所有会话、DataTree存储和事务日志。ZKDatabase会定进向磁盘dump快照数据，同时在Zookeeper启动时，会通过磁盘的事务日志和快照文件恢复一个完整的内存数据库。

## WatchManager（订阅功能实现逻辑）

### 普通watcher

在DataTree中存在一个WatchManager保存客户端对节点的订阅信息

* 订阅：客户端尝试订阅某一个路径的时候，只会在请求中告诉服务端，当前这个路径需要订阅，其实就是请求中的一个布尔值。服务端获取这个请求后，得知这个路径需要订阅就会把这个客户端和路径分别存在WatchManager中的两个哈希表中（其实发布订阅的功能只需要保存，节点->订阅客户端的信息就够了，保存订阅客户端->节点的信息是因为别的原因）。

  ![图片](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001152324bfoOnc.jpg)

* 触发：服务端在处理完一些事务方法后，比如：`setData`、`create`、`delete` 等，都会去检查下是否有回调通知需要触发，有的话取出需要通知的所有客户端，并逐个对他们发起通知（如果是普通的Watcher还会把订阅信息从映射中删除）。

  ![图片](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001152422tPWspG.jpg)

### 持久化Watcher

和普通Watcher不同的是持久化Watcher订阅的时候会在hash表中用clientID+节点路径作为key，多存储一条信息，当在发布完消息之后，要删除订阅信息的时候会通过clientID+节点路径去hash表中查找是否有持久化信息，如果有，就不删除订阅信息，没有就删除。

### Watcher通知

* Watcher通知的触发是实时的，和redis的发布订阅功能一样，客户端如果下线，在上线之后是收不到下线期间的通知的
* 因为ZooKeeper中写操作是顺序的，而通知的逻辑是在数据变更逻辑之后在同一个线程进行的，所以Watcher的通知顺序也就是写操作的顺序，在前一个通知发完之后才会发第二个通知

# 持久化

ZooKeeper使用了两种文件来进行持久化快照文件(snapshot)和日志文件(log)

* snapshot负责对当前整个内存数据进行快照

  > * snapCount：配置项指定ZooKeeper在将内存数据库序列化为快照之前，需要先写多少次事务日志。也就是说，每写几次事务日志，就快照一次。默认值为100000。为了防止所有的ZooKeeper服务器节点同时生成快照(一般情况下，所有实例的配置文件是完全相同的)，当某节点的先写事务数量在(snapCount/2+1,snapCount)范围内时(挑选一个随机值)，这个值就是该节点拍快照的时机。
  > * autopurge.snapRetainCount：该配置项指定开启了ZooKeeper的自动清理功能后(见下一个配置项)，每次自动清理时要保留的版本数量。默认值为3，最小值也为3。它表示在自动清理时，会保留最近3个快照以及这3个快照对应的事务日志。其它的所有快照和日志都清理。

* log 负责记录每一个写请求，先记录日志，再修改内存数据（ZooKeeper每一个事务都会刷盘）

* 恢复数据的时候，会先读取最新的 snapshot 文件

* 然后在根据 snapshot 最大的 zxid 去搜索符合条件的 log 文件，再通过逐条读取写请求来恢复剩余的数据

# ZAB协议

ZAB协议是专门为zookeeper实现分布式协调功能而设计。zookeeper主要是根据ZAB协议是实现分布式系统数据一致性

ZAB协议负责到ZooKeeper的选举和主从复制逻辑，详见《分布式-ZAB》章节

# ZooKeeper实践

## 实现一个分布式队列

使用路径为/queue的znode下的节点表示队列中的元素。/queue下的节点都是顺序持久化znode。这些znode名字的后缀数字表示来对应队列元素在队列中的位置。znode名字后缀数字越小，对应队列元素在对立中的位置越靠前

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/202110082127072wNB2v.png" alt="image-20211008212702501" style="zoom:50%;" />

### 队列的offer方法

offer方法在/queue下面创建一个顺序znode。因为znode的后缀数字是/queue下面现有znode最大后缀数字加1，所以该znode对应的队列元素处于队尾

### 队列的pop方法

获取到/queue中所有的子节点，然后对这个所有的子节点进行排序，删除后缀最小的子节点，返回这个节点，（在获取后缀最小的子节点的时候，可能其他客户端也会执行同样的操作，删除了对应子节点，这个使用应该进行重试，重新pop出当前队列最小的子节点）

## 实现分布式锁

### 设计

使用临时顺序znode来表示获取锁的请求，创建最小后缀数字znode的用户成功拿到锁

![image-20211001093910663](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001093912Kaw1iZ.png)

### 羊群效应

把锁请求者按照后缀数字进行排队，后缀数字小的锁请求者先获取锁。如果所有的锁请求者都watch锁持有者，当代表锁请求者的znode被删除之后，所有的锁请求者都会收到通知，但是只有一个锁请求者能拿到锁，这就是羊群效应。

![image-20211001094353108](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001094354igTSSY.png)

为了避免羊群效应，每个锁请求者watch它前面的锁请求者。每次锁被释放，只会有一个锁请求者被通知到。这样做还让锁的分配具有公平性，锁的分配遵循先到先得的原则

## 使用ZooKeeper实现选举

### 设计

使用临时顺序znode来表示选举请求，创建最小后缀数字znode的选举请求成功。在协同设计上和分布式锁是一样的，不同之处在于具体实现。不同于分布式锁，选举的具体实现对选举的各个阶段做了监控

![image-20211001100550535](https://raw.githubusercontent.com/syllr/image/main/uPic/20211001100552F5TOol.png)

## 服务发现

服务发现主要应用于为服务架构和分布式架构场景下。在这些场景下，一个服务通常需要送耦合的多个组件的协同才能完成。服务发现就是让组件发现祥光的组件。服务发现要提供的功能有以下3点：

* 服务注册
* 服务实例的获取
* 服务变化的通知机制

Curator有一个扩展叫做curator-x-discovery。curator-x-discovery基于ZooKeeper实现了服务发现，我们可以借鉴这个设计来看下怎么用ZooKeeper进行服务发现

### curator-x-discovery设计

使用一个base path作为整个服务发现的根目录。在这个根目录下是各个服务的目录。服务目录下面是服务实例。实例是服务实例的json序列化数据。服务实例对应的znode节点可以根据需要设置成持久性，临时性和顺序性

* 如果我们认为我们的服务实例从集群中断掉就不可用了，我们就应该创建临时性的znode
* 否则创建持久性znode

<img src="https://raw.githubusercontent.com/syllr/image/main/uPic/20211008213005lJWjiR.png" alt="image-20211008212959969" style="zoom:50%;" />

## 使用ZooKeeper进行分布式任务分发

![任务分发](https://gitee.com/syllr/images/raw/master/uPic/20210826162704iYaR8Z.png)
