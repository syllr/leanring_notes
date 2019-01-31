package designPattern.mediatorPattern;

public class ConcreteMediator extends Mediator {
    public Button addButton;
    public List list;
    public TextBox userNameTextBox;
    public ComboBox cb;

    /**
     * 同事之间的交互
     */
    @Override
    public void componentChanged(Component c) {
        if (c == addButton) {
            //单击按钮
            System.out.println("--单击增加按钮--");
            list.update();
            cb.update();
            userNameTextBox.update();
        } else if (c == list) {
            //从列表框中选择客户
            System.out.println("--从列表框中选择客户--");
            cb.select();
            userNameTextBox.setText();
        } else if (c == cb) {
            //从组合框中选择客户
            System.out.println("--从组合框选择客户--");
            cb.select();
            userNameTextBox.setText();
        }
    }
}
