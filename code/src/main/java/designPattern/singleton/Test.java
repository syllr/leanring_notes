package designPattern.singleton;

/**
 * @author yutao
 * @date 2019-05-26 21:57
 */
public class Test {
    public static void main(String[] args) {
        Thread t1 = new Thread(new T());
        Thread t2 = new Thread(new T());
        t1.start();
        t2.start();
        System.out.println("end");
    }
}
