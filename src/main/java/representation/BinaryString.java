package representation;

import representation.base.Array;
import representation.base.Representation;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BinaryString implements Representation, Array<Character> {

    String code;

    public BinaryString(String s) {
        code = s;
    }

    public static BinaryString of(String st) {
        return new BinaryString(st);
    }

    @Override
    public Representation clone() {
        return new BinaryString(code);
    }

    @Override
    public double distanceTo(Representation r) {
        if (!(r instanceof BinaryString))
            return Double.NEGATIVE_INFINITY;
        String oCode = ((BinaryString)r).code;
        return hammingDistance(code,oCode);
    }

    private int hammingDistance(String code, String oCode) {
        int d=0;
        for (int i = 0; i < code.length(); i++) {
            d += code.charAt(i)== oCode.charAt(i) ? 0:1;
        }
        return d;
    }

    @Override
    public String toString() {
        return code;
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BinaryString))
            return false;
        return code.equals(((BinaryString)obj).code);
    }

    public int length() {
        return code.length();
    }

    public void flip(int i) {
        char c = code.charAt(i)=='0' ? '1':'0';
        code = code.substring(0,i)+ c + code.substring(i+1);
    }
@Override
    public Character get(int i) {
        return code.charAt(i);
    }

    @Override
    public List<Character> getList() {

        List<Character> charList =
                code
                        .chars()
                        .mapToObj(c -> (char) c)
                        .collect(Collectors.toList());

        return charList;
    }

    @Override
    public void setList(List<Character> values) {

        char[] charArray = new char[values.size()];

        for (int i = 0; i < values.size(); i++) {
            charArray[i] = values.get(i);
        }
        code = new String(charArray);

    }

    @Override
    public void set(int i, Character v) {
        code = code.substring(0, i) + v + code.substring(i + 1);
    }

    @Override
    public void swap(int i, int j) {
        char c= code.charAt(i);
        set(i,code.charAt(j));
        set(j,c);
    }

    @Override
    public void move(int from, int to) {
        if (from == to)
            return;
        if (from >to )
        {
            for (int i=from ;i>to;i--)
            {
                swap(i,i-1);
            }
        }
        else
        {
            for (int i=from ;i<to;i++)
            {
                swap(i,i+1);
            }
        }
    }

    @Override
    public int getLength() {
        return length();
    }

    @Override
    public boolean exists(Character v) {
        return code.contains(""+v);
    }

    @Override
    public int firstOf(Character v) {
        return code.indexOf(""+v);
    }

    @Override
    public Array<Character> cloneArray() {
        return (Array<Character>) clone();
    }

    public String toBinaryString() {
        return code;
    }
}
