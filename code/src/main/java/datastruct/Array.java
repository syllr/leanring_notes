package datastruct;

/**
 * @author yutao
 * @date 2019-06-07 09:23
 */
public class Array {
    private int[] data;

    private int size;

    /**
     * 构造函数，传入数组的容量capacity构造Array
     *
     * @param capacity 数组的容量
     */
    public Array(int capacity) {
        data = new int[capacity];
        size = 0;
    }

    public Array() {
        this(10);
    }

    /**
     * 获取index索引位置的元素
     */
    int get(int index) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Get failed. Index is illegal.");
        }
        return data[index];
    }

    void set(int index, int e) {
        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Get failed. Index is illegal.");
        }
        data[index] = e;
    }

    /**
     * 获取数组中的元素个数
     */
    public int getSize() {
        return size;
    }

    /**
     * 获取数组的容量
     */
    public int getCapacity() {
        return data.length;
    }

    /**
     * 判断数组是否为空
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * 向所有元素后面添加一个新元素
     */
    public void addLast(int e) {
        add(size, e);
    }

    /**
     * 向所有的元素前添加一个新元素
     */
    public void addFirst(int e) {
        add(0, e);
    }

    /**
     * 在第index个位置插入一个新元素e
     */
    public void add(int index, int e) {
        if (size == data.length) {
            throw new IllegalArgumentException("Add failed. Array is full");
        }

        if (index < 0 || index > size) {
            throw new IllegalArgumentException("Add failed. Require index >= 0 index <= size");
        }

        System.arraycopy(data, index, data, index + 1, size - index);

        data[index] = e;
        size++;
    }

    /**
     * 从数组中删除index位置的元素，返回删除的元素
     */
    public int remove(int index) {

        if (index < 0 || index >= size) {
            throw new IllegalArgumentException("Remove failed. Index is illegal");
        }

        int ret = data[index];
        if (size - index + 1 >= 0) {
            System. arraycopy(data, index + 1, data, index + 1 - 1, size - index + 1);
        }
        size--;

        return ret;
    }

    public int removeFirst() {
        return remove(0);
    }

    public int removeLast() {
        return remove(size - 1);
    }

    public boolean contains(int e) {
        for (int i = 0; i < size; i++) {
            return data[i] == e;
        }
        return false;
    }

    /**
     * 删除数组中的第一个元素e
     */
    public void removeElement(int e) {
        int index = find(e);
        if (index != -1) {
            remove(index);
        }
    }

    private int find(int e) {
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder res = new StringBuilder();
        res.append(String.format("Array: size = %d, capacity = %d\n", size, data.length));

        res.append('[');

        for (int i = 0; i < size; i++) {
            res.append(data[i]);
            if (i != size - 1) {
                res.append(", ");
            }
            res.append(']');
        }
        return res.toString();
    }

}
