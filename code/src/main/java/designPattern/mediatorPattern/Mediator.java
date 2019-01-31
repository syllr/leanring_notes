package designPattern.mediatorPattern;

public abstract class Mediator {
    /**
     * 相当于一个通知中心，一旦其中的一个组件有变更，就回调用这个方法，就像实现了回调一样
     */
    public abstract void componentChanged(Component c);
}
