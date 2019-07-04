package dependenceinversion;

/**
 * @author yutao
 * @date 2019-06-01 11:35
 */
public class FECourse implements ICourse {
    @Override
    public void studyCourse() {
        System.out.println("学习前端课程");
    }
}
