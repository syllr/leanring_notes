package designPattern.singleton;

/**
 * @author yutao
 * 懒汉单例模式
 * @date 2019-05-26 21:43
 */
public class LazySingleton1 {
    private static LazySingleton1 lazySingleton1 = null;

    /**
     * 私有的构造器
     */
    private LazySingleton1(){
    }

    public static LazySingleton1 getInstance() {
        if (lazySingleton1 == null) {
            lazySingleton1 = new LazySingleton1();
        }

        return lazySingleton1;
    }
}
