package openClose;

/**
 * @author yutao
 * @date 2019-05-29 22:45
 */
public class JavaDiscountCourse extends  JavaCourse {
    public JavaDiscountCourse(Integer id, String name, Double price) {
        super(id, name, price);
    }

    public Double getOriginPrice() {
        return super.getPrice();
    }

    @Override
    public Double getPrice() {
        return super.getPrice() * 0.8;
    }
}
