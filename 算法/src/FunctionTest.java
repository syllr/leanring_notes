package src;

import java.util.function.Function;

/**
 * @author yutao create on 2019-08-07 22:25
 */
public class FunctionTest {
    public static void main(String[] args) {
        Function<Integer, Integer> times2 = i -> i * 2;
        Function<Integer, Integer> squared = i -> i * i;
        System.out.println(times2.apply(4));
        System.out.println(squared.apply(4));
        System.out.println(times2.compose(squared).apply(4));
        System.out.println(times2.andThen(squared).apply(4));
        System.out.println(Function.identity().compose(squared).apply(4));
    }
}
