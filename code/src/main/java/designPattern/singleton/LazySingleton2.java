package designPattern.singleton;

/**
 * @author yutao
 * @date 2019-05-26 22:08
 */
public class LazySingleton2 {
    private static LazySingleton2 lazySingleton2 = null;

    /**
     * 私有的构造器
     */
    private LazySingleton2(){
    }

    public synchronized static LazySingleton2 getInstance() {
        if (lazySingleton2 == null) {
            lazySingleton2 = new LazySingleton2();
        }

        return lazySingleton2;
    }
}
