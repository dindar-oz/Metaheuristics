package gui;


import metaheuristic.IterationEvent;
import metaheuristic.MetaHeuristicListener;
import org.jfree.data.xy.XYSeries;

import javax.swing.SwingUtilities;

/**
 * Created by dindar.oz on 26.06.2015.
 */
public class MHListener implements MetaHeuristicListener {
    private MHViewer viewer= null;
    private long updatePeriod;
    private XYSeries xySeries;


    long lastUpdateIteration =0;

    public MHListener(long updatePeriod, XYSeries xySeries) {
        this.updatePeriod = updatePeriod;
        this.xySeries = xySeries;
    }

    public MHListener(MHViewer viewer) {
        this.viewer = viewer;
    }

    @Override
    public void onIterationEvent(IterationEvent event) {
        if (event.getNeighboringCount()-lastUpdateIteration >updatePeriod) {
            if (viewer!=null)
                viewer.updateViewer(event);
            if (xySeries!= null)
                SwingUtilities.invokeLater(() -> xySeries.add(event.getNeighboringCount(), event.getBestCost()));
            lastUpdateIteration = event.getNeighboringCount();
        }


    }
}
