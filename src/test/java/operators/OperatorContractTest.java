package operators;

import base.MutationOperator;
import metaheuristic.ea.base.CrossOverOperator;
import metaheuristic.ea.crossover.OneCutCrossover;
import metaheuristic.ea.crossover.PTLCutCrossover;
import metaheuristic.ea.crossover.SimplePermutationCrossover;
import metaheuristic.ea.crossover.SinglePointCrossover;
import org.junit.jupiter.api.Test;
import representation.IntegerPermutation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static testsupport.Fixtures.isPermutation;

/**
 * Operators must return valid permutations and must never modify their inputs.
 */
class OperatorContractTest {

    private static final int N = 30;

    private static IntegerPermutation randomPermutation(RNG rng) {
        return new base.RandomPermutationSG(N).generate(null, rng);
    }

    @Test
    void mutationsKeepPermutationsAndInputs() {
        List<MutationOperator<IntegerPermutation>> ops = Arrays.asList(
                new TwoOpt(), new RandomRemoveReinsert<>(), new RandomInterchange<>(), new AdjacentInterchange<>(),
                new InsertMutation<>(3), new MoveSingleTerm<>(), new MoveSubSequence<>(), new ReverseSubSequence<>(),
                new ReverseAndOrMoveSubSequence<>(), new SwapSubSequences<>(false), new SwapSubSequences<>(true));
        RNG rng = new MersenneTwisterRNG(1);
        for (MutationOperator<IntegerPermutation> op : ops)
            for (int i = 0; i < 200; i++) {
                IntegerPermutation p = randomPermutation(rng);
                IntegerPermutation before = p.copy();
                IntegerPermutation q = op.apply(p, rng);
                assertEquals(before, p, op + " modified its input");
                assertTrue(isPermutation(q, N), op + " produced an invalid permutation " + q);
            }
    }

    @Test
    void crossoversKeepPermutationsAndInputs() {
        List<CrossOverOperator<IntegerPermutation>> ops = Arrays.asList(
                new SimplePermutationCrossover<>(), new OneCutCrossover<>(), new PTLCutCrossover<>());
        RNG rng = new MersenneTwisterRNG(2);
        for (CrossOverOperator<IntegerPermutation> op : ops)
            for (int i = 0; i < 200; i++) {
                IntegerPermutation a = randomPermutation(rng), b = randomPermutation(rng);
                IntegerPermutation a0 = a.copy(), b0 = b.copy();
                for (IntegerPermutation child : op.apply(a, b, rng))
                    assertTrue(isPermutation(child, N), op.getClass().getSimpleName() + " produced " + child);
                assertEquals(a0, a);
                assertEquals(b0, b);
            }
    }

    // SinglePointCrossover is for variable-length sequences: children have no duplicates and only parental elements
    @Test
    void singlePointCrossoverKeepsSequencesDuplicateFree() {
        RNG rng = new MersenneTwisterRNG(4);
        for (int i = 0; i < 200; i++) {
            IntegerPermutation a = randomPermutation(rng), b = randomPermutation(rng);
            for (IntegerPermutation child : new SinglePointCrossover<Integer, IntegerPermutation>().apply(a, b, rng)) {
                assertEquals(child.getLength(), new java.util.HashSet<>(child.getList()).size());
                assertTrue(child.getLength() <= N);
            }
        }
    }

    @Test
    void adjacentInterchangeSwapsNeighbours() {
        RNG rng = new MersenneTwisterRNG(3);
        IntegerPermutation p = randomPermutation(rng);
        IntegerPermutation q = new AdjacentInterchange<Integer, IntegerPermutation>().apply(p, rng);
        int differences = 0, first = -1;
        for (int i = 0; i < N; i++)
            if (!p.get(i).equals(q.get(i))) {
                differences++;
                if (first < 0)
                    first = i;
            }
        assertEquals(2, differences);
    }
}
