package designPattern.dynamicProxy;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

public class Client {
    public static void main(String[] args) {
        Subject realSubject = new RealSubject();

        InvocationHandler handler = new DynamicProxy(realSubject);

    }
}
