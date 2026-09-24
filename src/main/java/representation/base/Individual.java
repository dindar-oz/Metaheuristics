package representation.base;

/**
 * Created by dindar.oz on 22.04.2015.
 */
public interface Individual {
    double getCost();
    Representation getRepresentation();

    Individual clone();

    void update(Representation rep,double cost);

    default int getAge() {return 0;} // By default we ageing
    default void age(){};
}
