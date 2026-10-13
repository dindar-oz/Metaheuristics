package metaheuristic.ea.crossover;

import metaheuristic.ea.base.CrossOverOperator;
import representation.IntegerPermutation;
import representation.base.Array;
import representation.base.Permutation;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */

/**
 * return a Solution obtained by PTL crossover
 * See: Tasgetiren, Pan, Suganthan, Liang, A Discrete DE algorithm for the
 * No-wait Flowshop Scheduling Problem with Total Flowtime Criterion,p.253 2007, (conf)
 * See: Pan, Tasgetiren, Liang, A discrete PSO algorithm for the no-wait
 * flowshop scheduling problem, p.2811, 2008, (journal)
 *
 * Receives one Solution V and makes a crossover with V and this solution
 * A block is determined in this solution
 * The V\block is determined in the same order as in V
 * Then a new solution like either [block, V\block] or [V\block, block]
 * is built with equal probability
 * Ex       _ _
 * this:  5,1,4,2,3
 *            _   _
 * V:     3,5,4,2,1
 * O1:    3,5,2,1,4
 * O2:    1,4,3,5,2
  */
public class PTLCutCrossover<E, S extends Representation<S> & Permutation<E>> implements CrossOverOperator<S> {
    @Override
    public List<S> apply(S i1, S i2, RNG rng) {

        S p1 = i1;
        S p2 = i2;

        S p3 = i2.copy();
        S p4 = i2.copy();

        int  r1= rng.randInt(p3.getLength());
        int  r2= rng.randInt(p3.getLength());
        while (r2 == r1)
            r2 = rng.randInt(p3.getLength());

        if (r1>r2)
        {
            r1 = r1+r2;
            r2 = r1-r2;
            r1 = r1-r2;
        }

        int offset = (rng.rollDice(0.5)) ? 0:(p3.getLength()-(r2-r1+1));
        for (int i=r1;i<=r2;i++)
        {
            p3.set(p3.firstOf(p1.get(i)), null);
            p4.set(offset++, p1.get(i));
        }
        offset = (offset == r2-r1+1) ? offset:0;
        for (int i=0;i<p3.getLength();i++)
        {
            if (p3.get(i) !=null)
            {
                p4.set(offset++,p3.get(i));
            }
        }

        List<S> offsprings = new ArrayList<>();
        offsprings.add(p4);

        return offsprings;
    }
}
