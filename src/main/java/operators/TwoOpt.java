/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package operators;

import util.random.RNG;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author Z420
 */
public class TwoOpt extends RandomSubListMutation{
    @Override
    protected void mutateSubList(List<Integer> subList)
    {
        Collections.reverse(subList);
    }
}
