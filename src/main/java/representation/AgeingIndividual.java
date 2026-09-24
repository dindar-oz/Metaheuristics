package representation;

import representation.base.Individual;
import representation.base.Representation;

public class AgeingIndividual extends SimpleIndividual{
    private int age;

    public AgeingIndividual(Individual i) {
        this(i.getRepresentation(), i.getCost(), i.getAge());
    }

    public AgeingIndividual(Representation r, double f) {
        this(r,f,0);
    }

    public AgeingIndividual(Representation rep, double cost, int age) {
        super(rep,cost);
        this.age=age;
    }

    @Override
    public void age() {
        super.age();
        age++;
    }

    @Override
    public int getAge() {
        return age;
    }

    @Override
    public Individual clone() {
        return new AgeingIndividual(representation.clone(),cost,age);
    }
}
