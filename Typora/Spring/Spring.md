# 应用事件（ApplicationEvent）

![Srping事件通知机制UML类图](https://raw.githubusercontent.com/syllr/image/main/uPic/20211114204518zZfbo1.svg)

## Java的事件机制

Java的事件机制一般包括三个部分：EventObject，EventListener和Source。

* EventObject：java.util.EventObject是事件状态对象的基类，它封装了事件源对象以及和事件相关的信息。所有java的事件类都需要继承该类。
* EventListener：java.util.EventListener是一个标记接口，就是说该接口内是没有任何方法的。所有事件监听器都需要实现该接口。事件监听器注册在事件源上，当事件源的属性或状态改变的时候，调用相应监听器内的回调方法。
* Source：事件源不需要实现或继承任何接口或类，它是事件最初发生的地方。因为事件源需要注册事件监听器，所以事件源内需要有相应的盛放事件监听器的容器。

## Spring的事件机制

Spring在Java事件机制的基础上，用ApplicationEvent继承EventObject，用EventListener继承ApplicationListener，利用ApplicationContext.publishEvent()方法发送事件通知，

这其实是观察者模式的实现，观察者ApplicationListener和被观察者ApplicationEvent是解耦和的

ApplicationEventMulticaster中用了一个Executor接口来执行ApplicationListener的onApplicationEvent方法，Spring默认的Executor是同步的SyncTaskExecutor

> 如果需要多线程异步执行Listener中的通知逻辑，需要定义applicationEventMulticaster，注入线程池和errorHandler
>
> ```xml
>     <!-- 定义applicationEventMulticaster，注入线程池和errorHandler,此处使用系统自带的广播器，也可以注入其他广播器， -->
>     <bean name="applicationEventMulticaster" class="org.springframework.context.event.SimpleApplicationEventMulticaster">
>         <property name="taskExecutor" ref="executor"></property>
>         <property name="errorHandler" ref="errorHandler"></property>
>     </bean>
> ```

