package singleresponsibility;

/**
 * @author yutao
 * @date 2019-06-01 19:10
 */
public class FlyBird implements Bird {
    @Override
    public void mainMoveMode(String birdName) {
        System.out.println(birdName + "用翅膀飞");
    }
}
