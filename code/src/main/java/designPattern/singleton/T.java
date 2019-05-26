package designPattern.singleton;

/**
 * @author yutao
 * @date 2019-05-26 21:59
 */
public class T implements  Runnable {

    @Override
    public void run() {
        LazySingleton1 lazySingleton1 = LazySingleton1.getInstance();
        System.out.println(Thread.currentThread().getName() + " " + lazySingleton1);
    }
}
