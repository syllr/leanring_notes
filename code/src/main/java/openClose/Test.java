package openClose;

/**
 * @author yutao
 * @date 2019-05-29 22:26
 */
public class Test {
    public static void main(String[] args) {
        ICourse javaCourse = new JavaCourse(96, "javaCourse", 348d);
        System.out.println("课程ID：" + javaCourse.getId() + "课程名称" + javaCourse.getName() + "课程价格" + javaCourse.getPrice() + "元");
        "".toString();
    }
}
