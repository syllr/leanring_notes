package designPattern.singleton;

/**
 * @author yutao
 * @date 2019-05-26 22:08
 */
public class LazyDoubleCheckSingleton {
    private static volatile LazyDoubleCheckSingleton lazyDoubleCheckSingleton = null;

    private LazyDoubleCheckSingleton() {

    }

    public static LazyDoubleCheckSingleton getInstance() {
        if (lazyDoubleCheckSingleton == null) {
            synchronized (LazyDoubleCheckSingleton.class) {
                if (lazyDoubleCheckSingleton == null) {

                    lazyDoubleCheckSingleton = new LazyDoubleCheckSingleton();
                    //1.分配内存给这个对象
                    //2.初始化对象
                    //3.设置lazyDoubleCheckSingleton，指向刚分配的内存地址
                }
            }
        }
        return lazyDoubleCheckSingleton;
    }
}
