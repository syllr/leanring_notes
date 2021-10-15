# 网络编程关注的问题

1. 连接的建⽴
2. 连接的断开
3. 消息的到达（缓冲区）
4. 消息发送完毕 （缓冲区）

**网络编程只能做到从读内核缓冲区读取数据，或则把数据写到写内核缓存区，那么具体什么时候数据发送到内核读缓冲区，或数据什么时候从内核写缓冲区发送数据都是内核所控制的，所以无论是消息的到达还是消息的发送完毕都指的是和缓冲区的交互**

![网络编程内核缓冲区](https://gitee.com/syllr/images/raw/master/uPic/20210903100750Q3FN0f.jpg)

## 网络编程连接流程

![网络编程连接流程](https://gitee.com/syllr/images/raw/master/uPic/20210903100854f7WEVY.jpg)

# 中断

中断是硬件和软件交互的一种机制，可以说整个操作系统，整个架构都是由中断来驱动的。中断的机制分为两种，中断和异常，中断通常为 ![[公式]](https://www.zhihu.com/equation?tex=IO) 设备触发的异步事件，而异常是 ![[公式]](https://www.zhihu.com/equation?tex=CPU) 执行指令时发生的同步事件。主要说明下 ![[公式]](https://www.zhihu.com/equation?tex=IO) 外设触发的中断，总的来说**一个中断的流程会经历设备，中断控制器，CPU 三个阶段：设备产生中断信号，中断控制器翻译信号，CPU 来实际处理信号**。

## 中断控制器

中断控制器可以看作是中断的代理，外设是很多的，如果没有一个中断代理，外设想要给 ![[公式]](https://www.zhihu.com/equation?tex=CPU) 发送中断信号来处理中断，那只能是外设连接在 ![[公式]](https://www.zhihu.com/equation?tex=CPU) 的管脚上，![[公式]](https://www.zhihu.com/equation?tex=CPU) 的管脚是很宝贵的，不可能拿出那么多管脚去连接外设。所以就有了中断控制器这个中断代言人，所有的 ![[公式]](https://www.zhihu.com/equation?tex=IO) 外设连接其上，发送中断请求时就向中断控制器发送信号，**中断控制器再通知 CPU**，如此便解决了上述问题。

当硬件设备发送中断信息给中断控制器，中断控制器翻译信号信息之后把中断类型存到中断寄存器，同时把相应的参数存到对应的寄存器中（根据中断类型不同，存的寄存器也不同，比如int 80中断会触发程序从用户态到内核态的切换，调用内核函数，就会把要调用内核函数的参数存到对应的寄存器中）

![int 80中断事件处理流程图](https://gitee.com/syllr/images/raw/master/uPic/20210903153411iay2ZI.jpg)

>  中断向量表存储了各种类型的中断的处理方式

## CPU是如何知道中断的

cpu执行完每一条指令检查中断寄存器，如果有就会保存放前寄存器然后修改pc寄存器去执行中断指令

![preview](https://gitee.com/syllr/images/raw/master/uPic/20210903155145RG62Zp.jpg)

* 指令周期：指令周期是CPU从内存取出一条指令并执行这条指令的时间总和，一般由若干个机器周期组成，是从取指令、分析指令到执行完所需的全部时间。

## 机器周期

机器周期也叫CPU周期，由于CPU访问一次内存所花的时间较长，因此用从内存读取一条指令字的最短时间来定义。在计算机中，为了便于管理，常把一条指令的执行过程划分为若干个阶段，每一阶段完成一项工作。如，取指令、存储器读、存储器写等，这每一项工作称为一个基本操作。完成一个基本操作所需要的时间称为机器周期。

# 阻塞**io**模型和⾮阻塞**io**模型

* 阻塞在哪里：阻塞发生在调用的线程中

* 什么来决定：创建fd的时候可以设置是否阻塞

  > ```c
  > //连接的fd 
  > fcntl(c->fd, F_SETFL, O_NONBLOCK); 
  > //io函数 read write io函数
  > ```

* 具体的差异

  > io函数在数据未到达时是否立刻返回
  > 如果是立刻返回:非阻塞的io
  > 相反:阻塞的io

  用户数据空间和内核空间交互的时候会经过两个阶段

  * 数据准备阶段：服务器和客户端建立了连接，通过网络发送数据，但是数据还没有完全发送到内核空间
  * 数据拷贝阶段：数据发送到内核空间之后，程序把数据从内核空间拷贝到用户空间的过程

<img src="https://gitee.com/syllr/images/raw/master/uPic/20210903101548RrlkTb.jpg" alt="用户空间和内核空间交互流程" style="zoom: 150%;" />

## 阻塞io模型

当线程调用read函数的时候，线程发生切换，从用户态进入到内核态，读取内核缓冲区，发现内核缓冲区处于数据准备阶段，则发生阻塞，一直等到内核缓冲区数据就绪，然后开始把内核缓存区的数据拷贝到用户空间（这个过程也是线程阻塞的）。总结一下就是从客户端和服务端建立建立开始，到数据拷贝到用户态，线程会一直阻塞，就算客户端和服务端建立起连接之后一直不发送消息，线程也会一直阻塞，因为一直处在数据准备阶段。

## 非阻塞io模型

当线程调用read函数的时候，线程发生切换，从用户态进入到内核态，读取内核缓冲区，发现内核缓冲区处于数据准备阶段，则立即返回，可以重复调用read函数，等待内核数据准备完成，再次调用的时候会把内核缓存区的数据拷贝到用户空间（这个过程也是线程阻塞的）。

## 阻塞**io**模型 **+** 多线程

> 每一个线程处理一个 fd 连接 bio
> 优点：处理及时（处理数据最及时，延迟最低）
> 缺点：线程利⽤率很低，线程的数量是有限的（客户端和服务端建立连接之后不会一直发送数据，但是这种模式下只要有连接就会一直占用线程）

![多线程阻塞io](https://gitee.com/syllr/images/raw/master/uPic/20210903113804ZyHjgz.jpg)

# **io**多路复用（网络线程）

> 用一个线程来检测多个io事件

<img src="https://gitee.com/syllr/images/raw/master/uPic/20210903155403zOMmwD.jpg" alt="preview" style="zoom:150%;" />

* 调用select/poll/epoll多路复用函数时**调用线程是阻塞的**，不过阻塞的过程不是等待数据到达内核缓冲区（这个过程其实对于多路复用函数是异步的）而阻塞的是多路复用函数去遍历存放已经数据就绪的fd集合，所以这个阻塞的过程其实就是遍历一个数据或则链表的过程，速度非常快（select的是数组，poll，epoll是链表）
* 调用io多路复用函数可以获取到数据已经就绪的fd列表，然后使用多线程，线程池，单线程并行处理等方式（看具体的实现，redis就是单线程处理）

### io多路复用的函数

* select：select把要监控的fd放到一个数组中，在调用select的时候一次性遍历整个数组中的所有fd，然后把数据就绪的fd返回

* poll：poll和selct差不多，只不过select用的是数组，有数量的限制，而poll用的是链表，没有数量的限制

* epoll：内部使用一个红黑树存放需要监听的fd，然后和网卡的硬件驱动建立联系，当对应的socket产生事件时（连接事件，可读事件，可写事件），内核会把socket对应的fd放入到一个链表中，程序调用epoll函数的时候，会去遍历这个链表，如果链表有数据，则会返回。

#### epoll

##### 关键函数

- `epoll_create`: 创建一个epoll实例，文件描述符
- `epoll_ctl`: 将监听的文件描述符添加到epoll实例中的红黑树，实例代码为将标准输入文件描述符添加到epoll中
- `epoll_wait`: 等待epoll事件从epoll实例中发生（遍历epoll实例中的就绪链表）， 并返回事件以及对应文件描述符

##### 边沿触发vs水平触发

`epoll`事件有两种模型，边沿触发：edge-triggered (ET)， 水平触发：level-triggered (LT)

**水平触发(level-triggered)**

- socket接收缓冲区不为空 有数据可读 读事件一直触发

**边沿触发(edge-triggered)**

- 有新的数据来了才触发，如果缓冲区剩下有上次没有读完的数据，不会触发（内核缓冲区要有新的事件来才会触发，就算内核缓冲区有数据，但是没有新的事件也不会触发）

边沿触发事件来一次仅触发一次，水平触发只要有数据就会一直触发。

![R-C](https://gitee.com/syllr/images/raw/master/uPic/20210903184855SMmVgx.png)

```c
struct eventpoll { 
 ... 
 struct rb_root rbr; // 管理epoll监听的事件 
 struct list_head rdllist; // 保存着 epoll_wait 返回满⾜条件的事件 
 ... 
}; 
 
struct epitem { 
 ... 
 struct rb_node rbn; // 红⿊树节点 
 struct list_head rdllist; // 双向链表节点 
 struct epoll_filefd ffd; // 事件句柄信息 
 struct eventpoll 
*
ep; // 指向所属的eventpoll对象 
 struct epoll_event event; // 注册的事件类型 
 ... 
}; 
struct epoll_event { 
 __uint32_t events; 
 epoll_data_t data; // 保存 关联数据 
}; 
typedef union epoll_data { 
 void 
*
ptr; 
 int fd; 
 uint32_t u32; 
 uint64_t u64; 
}epoll_data_t;
```

eventpoll结构中rbr就是管理epoll监听事件的红黑树使用红黑树的好处有两点，一是因为红黑树是一个有序的结构所以可以去重，排除重复添加事件，二是红黑树查找效率很高，rdllist就是保存着满足条件事件的链表

<img src="https://gitee.com/syllr/images/raw/master/uPic/20210903185445PqKpFs.jpg" alt="epoll原理图" style="zoom:150%;" />

# **Reactor**模型

- 组成：非阻塞的io + io多路复用；
- 特征：基于事件循环，以事件驱动或者事件回调的⽅式来实现业务逻辑，可以针对不同的事件类型，进行不同的处理
- 理解：将网络io中连接的建立，消息的读取，消息的发送完毕，连接的断开都转化成事件的处理

单Reactor模型

![图片](https://gitee.com/syllr/images/raw/master/uPic/20210903205739BXLHsF.jpg)

方案说明：

- Reactor对象通过select/poll/epoll io多路复用函数监控客户端请求事件，收到事件后通过dispatch进行分发（对应到epoll的api就是在事件循环中调用epoll_wait()函数）

- 如果是建立连接请求事件，则由Acceptor通过accept处理连接请求，然后创建一个Handler对象处理连接完成后的后续业务处理

- 如果不是建立连接事件，则Reactor会分发调用连接对应的Handler来响应

- Handler会完成read->业务处理->send的完整业务流程

  优点：

  * 模型简单，没有多线程、进程通信、竞争的问题，全部都在一个线程中完成

  缺点：
  - 性能问题：只有一个线程，无法完全发挥多核CPU的性能
    Handler在处理某个连接上的业务时，整个进程无法处理其他连接事件，很容易导致性能瓶颈
  - 可靠性问题：线程意外跑飞，或者进入死循环，会导致整个系统通信模块不可用，不能接收和处理外部消息，造成节点故障

  

实例：Redis通信模型（并高发，高吞吐量）

单Reactor多线程模型

![图片](https://gitee.com/syllr/images/raw/master/uPic/20210903205800DLNzV9.jpg)

方案说明

- Reactor对象通过select监控客户端请求事件，收到事件后通过dispatch进行分发

- 如果是建立连接请求事件，则由Acceptor通过accept处理连接请求，然后创建一个Handler对象处理连接完成后的续各种事件

- 如果不是建立连接事件，则Reactor会分发调用连接对应的Handler来响应

- Handler只负责响应事件，不做具体业务处理，通过read读取数据后，会分发给后面的Worker线程池进行业务处理

- Worker线程池会分配独立的线程完成真正的业务处理，如何将响应结果发给Handler进行处理

- Handler收到响应结果后通过send将响应结果返回给client

  优点

  - 可以充分利用多核CPU的处理能力

  缺点

  - 多线程数据共享和访问比较复杂。如果子线程完成业务处理后，把结果传递给主线程Reactor进行发送，就会涉及共享数据的互斥和保护机制。
  - Reactor承担所有事件的监听和响应，只在主线程中运行，可能会存在性能问题。例如并发百万客户端连接，或者服务端需要对客户端握手进行安全认证，但是认证本身非常损耗性能

主从多Reactor模型

![图片](https://gitee.com/syllr/images/raw/master/uPic/20210903210250Q8Xd6Q.jpg)

方案说明

- Reactor主线程MainReactor对象通过select监控建立连接事件，收到事件后通过Acceptor接收，处理建立连接事件

- Acceptor处理建立连接事件后，MainReactor将连接分配Reactor子线程给SubReactor进行处理

- SubReactor将连接加入连接队列进行监听，并创建一个Handler用于处理各种连接事件

- 当有新的事件发生时，SubReactor会调用连接对应的Handler进行响应

- Handler通过read读取数据后，会分发给后面的Worker线程池进行业务处理

- Worker线程池会分配独立的线程完成真正的业务处理，如何将响应结果发给Handler进行处理

- Handler收到响应结果后通过send将响应结果返回给client

  

优点

- 父线程与子线程的数据交互简单职责明确，父线程只需要接收新连接，子线程完成后续的业务处理

- 父线程与子线程的数据交互简单，Reactor主线程只需要把新连接传给子线程，子线程无需返回数据

  

实例：Nginx通信模型（并高发，高吞吐量）

![图片](https://mmbiz.qpic.cn/mmbiz_png/teW9iaNn6XTTTLnw3908BcgHR1g46rXsEibHGe1U1Qr0hcUPlqVK5xOBzDA0BH2w6GtP5aOQrG7vNE0gxTXgFHicw/640?wx_fmt=png&tp=webp&wxfrom=5&wx_lazy=1&wx_co=1)

多路复用，多进程处理，避免单个IO阻塞耗时，引起全局所有IO等待 。

注意Nginx使用的是多进程而不是多线程

![图片](https://gitee.com/syllr/images/raw/master/uPic/20210903210735PGQhx5.jpg)

相同点：基于与nginx类似，多任务（nginx多进程，netty线程池）、多路复用

不同点：nginx Work进程主动式抢占客户端socket处理，netty则是accept线程池基于负载均衡策略向IO线程池分发任务

## 简单的Reactor监听流程

```c
//建立监听套接字
int listenfd = socket(); 
//bind端口
bind(listenfd, addr, sizeof(addr)); 
//监听端口
listen(listenfd); 
//创建epoll fd
int epfd = epoll_create(0); 
//使用epoll_ctl监听 监听套接字，监听连接建立事件 即acceptor
epoll_ctl(epfd, EPOLL_CTL_ADD, listenfd);
//while开启事件循环
while(1) {
      //定义用户态数组接收就绪事件
     struct epoll_event events[1024]; 
      //调用epoll_wait查询就绪事件
     int nevents = epoll_wait(epfd, events, 1024, 1); // 数据准备阶段 
     for (int i = 0; i<nevents; i++) { 
     epoll_event *e = events[i]; 
     if (e->data.fd === listenfd) { 
       //如果是监听套接字有事件，那就一定是建立连接事件，建立已连接套接字
      fd = socket () //建立已连接套接字，这个地方是伪代码
        //将已连接套接字放入epoll
      epoll_ctl(epfd, EPOLL_CTL_ADD, fd); 
     } else { 
     if (e->events & EPOLLIN) { 
     // FIN包 客户端主动断开连接 
     // 数据的到达 
     read(e->data.fd, buff); 
     if (buff == FIN包) { 
     close(fd) 
     return 
    //分发事件，这个地方可以用线程池来处理
     decode(); 
     compute(); 
     encode(); 
     epoll_ctl(epfd, EPOLL_CTL_MOD, events | EPOLLOUT); 
     } 

     if (e->events & EPOLLOUT) { 
     // 数据的发送完毕 
     write(e->data.fd, buff, size); 
     } 

     if (e->events & EPOLLERR) { 
     // close 连接断开 
     } 
 	} 
} 
```

* 监听套接字：服务器端套接字并不指定具体的客户端套接字，而是一直处于等待连接的状态，实时监控网络状态。
* 已连接套接字（客户端套接字）：由客户端的套接字提出连接请求，要连接的目标是服务器端的套接字。为此，客户端的套接字必须首先描述它要连接的服务器的套接字，指出服务器端套接字的地址和端口号，然后就向服务器端套接字提出连接请求，当服务器端套接字监听到或者接收到客户端套接字的连接请求，它就响应该请求，把服务器端套接字的描述发给客户端，一旦客户端确认此描述，连接就建立好了。注意：此时，监听套接字继续处于监听状态，继续接收其他客户端套接字的连接请求。