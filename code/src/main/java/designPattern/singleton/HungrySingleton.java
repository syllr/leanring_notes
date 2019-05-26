package designPattern.singleton;

/**
 * @author yutao
 * 饿汉模式
 * @date 2019-05-26 23:17
 */
public class HungrySingleton {
    private static HungrySingleton hungrySingleton = new HungrySingleton();

    private HungrySingleton() {

    }

    public static HungrySingleton getInstance() {
        return hungrySingleton;
    }

    //Class对象的初始化锁获取条件
    //1.有一个A类型的实例被创建
    //2.A类型的一个静态方法被调用
    //3.A类型中声明的一个静态成员被赋值
    //4.A类型中的一个静态成员被使用(并且这个成员不是一个常量成员)
    //5.A类是一个顶级类，并且在A类中有嵌套的断言语句
}
