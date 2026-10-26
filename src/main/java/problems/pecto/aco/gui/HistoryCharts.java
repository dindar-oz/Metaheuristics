package problems.pecto.aco.gui;

import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.List;

/**
 * The run so far, per iteration: the costs (best so far, the iteration's best after improvement, the mean ant) and
 * the colony's convergence (branching factor, and the diversity of the ants on its own axis). A vertical line marks
 * the iteration of the current event.
 */
final class HistoryCharts extends JPanel {

    private final XYChart cost = chart("Cost");
    private final XYChart convergence = chart("Branching factor");
    private final XChartPanel<XYChart> costPanel = new XChartPanel<>(cost);
    private final XChartPanel<XYChart> convergencePanel = new XChartPanel<>(convergence);
    private int shownSize = -1;
    private int shownIteration = Integer.MIN_VALUE;
    private double shownLast = Double.NaN;

    HistoryCharts() {
        super(new GridLayout(1, 2, 6, 0));
        add(costPanel);
        add(convergencePanel);
        convergence.getStyler().setYAxisGroupPosition(1, Styler.YAxisPosition.Right);
        clear();
    }

    private static XYChart chart(String yTitle) {
        XYChart c = new XYChartBuilder().width(500).height(220).xAxisTitle("Iteration").yAxisTitle(yTitle).build();
        c.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Line);
        c.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        c.getStyler().setMarkerSize(0);
        c.getStyler().setChartBackgroundColor(UIManager.getColor("Panel.background"));
        c.getStyler().setLegendBackgroundColor(new Color(255, 255, 255, 210));
        c.getStyler().setPlotGridLinesColor(new Color(225, 225, 225));
        c.getStyler().setToolTipsEnabled(false);
        c.getStyler().setChartTitleVisible(false);
        c.getStyler().setLegendFont(c.getStyler().getLegendFont().deriveFont(10f));
        return c;
    }

    void clear() {
        for (XYChart c : List.of(cost, convergence))
            for (String name : List.copyOf(c.getSeriesMap().keySet()))
                c.removeSeries(name);
        shownSize = -1;
        shownIteration = Integer.MIN_VALUE;
        costPanel.repaint();
        convergencePanel.repaint();
    }

    void show(Frame f) {
        List<Frame.IterationStats> h = f.history();
        double last = h.isEmpty() ? Double.NaN : h.get(h.size() - 1).best();
        if (h.size() == shownSize && f.iteration() == shownIteration && (Double.compare(last, shownLast) == 0))
            return;
        shownSize = h.size();
        shownIteration = f.iteration();
        shownLast = last;
        if (h.isEmpty()) {
            clear();
            shownSize = 0;
            return;
        }
        int n = h.size();
        double[] x = new double[n], best = new double[n], itBest = new double[n], mean = new double[n],
                branching = new double[n], diversity = new double[n];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE, bHi = 0;
        for (int i = 0; i < n; i++) {
            Frame.IterationStats s = h.get(i);
            x[i] = s.iteration();
            best[i] = s.best();
            itBest[i] = s.iterationBest();
            mean[i] = s.meanAnt();
            branching[i] = s.branchingFactor();
            diversity[i] = Double.isNaN(s.diversity()) ? 0 : s.diversity();
            lo = Math.min(lo, Math.min(best[i], itBest[i]));
            hi = Math.max(hi, mean[i]);
            bHi = Math.max(bHi, branching[i]);
        }
        double now = f.type() == EventType.ITERATION_DONE ? f.iteration() : f.iteration() - 0.5;
        series(cost, "best so far", x, best, new Color(0x1F6FEB), 2f, 0);
        series(cost, "iteration best", x, itBest, new Color(0x0D8A7F), 1.2f, 0);
        series(cost, "mean ant", x, mean, new Color(0xB0B8C2), 1.2f, 0);
        series(cost, "now", new double[]{now, now}, new double[]{lo, hi}, new Color(0xFFB300), 1.5f, 0);
        series(convergence, "branching factor", x, branching, new Color(0x8A4FD6), 1.8f, 0);
        series(convergence, "diversity", x, diversity, new Color(0xD98200), 1.4f, 1);
        series(convergence, "now", new double[]{now, now}, new double[]{0, Math.max(1, bHi)}, new Color(0xFFB300), 1.5f, 0);
        convergence.setYAxisGroupTitle(1, "Diversity");
        costPanel.revalidate();
        costPanel.repaint();
        convergencePanel.revalidate();
        convergencePanel.repaint();
    }

    private static void series(XYChart c, String name, double[] x, double[] y, Color color, float width, int axis) {
        XYSeries s = c.getSeriesMap().containsKey(name) ? c.updateXYSeries(name, x, y, null) : c.addSeries(name, x, y);
        s.setLineColor(color);
        s.setLineStyle(new BasicStroke(width));
        s.setMarker(SeriesMarkers.NONE);
        s.setYAxisGroup(axis);
        if (name.equals("now"))
            s.setShowInLegend(false);
    }
}
