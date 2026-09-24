package gui;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.ApplicationFrame;
import org.jfree.ui.RectangleInsets;
import org.jfree.ui.RefineryUtilities;

import java.awt.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs several metaheuristics on the same problem in parallel and plots best cost vs. evaluation count.
 */
public class MHRunnerPanel extends ApplicationFrame {

    private static final long UPDATE_PERIOD = 1;

    private final XYSeriesCollection dataset = new XYSeriesCollection();

    public MHRunnerPanel(String title, List<AbstractMetaheuristic> algs,
                         List<InitialSolutionGenerator> solutionGenerators, OptimizationProblem problem) {
        super(title);
        ChartPanel chartPanel = createChartPanel();
        chartPanel.setPreferredSize(new Dimension(800, 450));
        setContentPane(chartPanel);
        runAll(algs, solutionGenerators, problem);
    }

    private void runAll(List<AbstractMetaheuristic> algs, List<InitialSolutionGenerator> solutionGenerators,
                        OptimizationProblem problem) {
        ExecutorService threadPool = Executors.newFixedThreadPool(algs.size());
        for (int i = 0; i < algs.size(); i++) {
            XYSeries series = new XYSeries(algs.get(i).getName());
            dataset.addSeries(series);
            threadPool.execute(new MHThread(algs.get(i), solutionGenerators.get(i), problem,
                    new MHListener(UPDATE_PERIOD, series)));
        }
        threadPool.shutdown();
    }

    private ChartPanel createChartPanel() {
        JFreeChart chart = ChartFactory.createXYLineChart("", "Evaluation Count", "Best Cost", dataset);
        chart.setBackgroundPaint(Color.white);
        XYPlot plot = (XYPlot) chart.getPlot();
        plot.setBackgroundPaint(Color.lightGray);
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.setAxisOffset(new RectangleInsets(5.0D, 5.0D, 5.0D, 5.0D));
        XYItemRenderer r = plot.getRenderer();
        if (r instanceof XYLineAndShapeRenderer) {
            XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) r;
            renderer.setBaseShapesVisible(false);
            renderer.setDrawSeriesLineAsPath(true);
        }

        ChartPanel panel = new ChartPanel(chart);
        panel.setFillZoomRectangle(true);
        panel.setMouseWheelEnabled(true);
        return panel;
    }

    public static void showComparison(String title, List<AbstractMetaheuristic> algs,
                                      List<InitialSolutionGenerator> solutionGenerators, OptimizationProblem problem) {
        MHRunnerPanel panel = new MHRunnerPanel(title, algs, solutionGenerators, problem);
        panel.pack();
        RefineryUtilities.centerFrameOnScreen(panel);
        panel.setVisible(true);
    }
}
