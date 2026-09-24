package representation.base;

import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public interface Array<T> {
    public T get(int i);
    public List<T> getList();
    public void setList(List<T> values);

    public void set(int i,T v);
    public void swap(int i, int j);
    public void move(int from, int to);
    public int getLength();
    public boolean exists(T v);
    public int firstOf(T v);
    public Array<T> cloneArray();


}
