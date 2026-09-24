package exceptions;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class WrongIndividualType extends RuntimeException {
    public WrongIndividualType(String s) {
        super(s);
    }
}
