package representation;

import representation.base.Individual;
import representation.base.Representation;

/**
 * Created by dindar.oz on 02.06.2015.
 */
public class SimpleIndividual implements Individual {

    Representation representation;
    double cost =0;

    public SimpleIndividual(Representation r, double f)
    {
        representation = r;
        cost = f;
    }

    @Override
    public double getCost() {
        return cost;
    }



    @Override
    public Representation getRepresentation() {
        return representation;
    }

    public Individual clone()
    {
        return new SimpleIndividual(representation.clone(), cost);

    }

    @Override
    public void update(Representation rep, double cost) {
        this.representation = rep;
        this.cost = cost;

    }

    ;

    @Override
    public String toString() {
        return "Cost: " + cost + "  "+representation.toString();
    }
}
