package dependenceinversion;

/**
 * @author yutao
 * @date 2019-06-01 11:32
 */
public class Custom {

    private ICourse iCourse;

    public void studyCourse() {
        iCourse.studyCourse();
    }
}
