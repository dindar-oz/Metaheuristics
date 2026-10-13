package lab.ui;

import metaheuristic.run.ProgressSnapshot;
import org.knowm.xchart.XChartPanel;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Two live charts: best cost (step curves of all improvements) and one selectable algorithm metric
 * (temperature, mean cost, ...), both against evaluations or time. Must be used on the Swing thread.
 */
final class ConvergenceView extends JPanel {

    private static final double LOG_FLOOR = 1e-12;

    private final XYChart costChart = newChart("Best cost", XYSeries.XYSeriesRenderStyle.Step);
    private final XYChart metricChart = newChart("Value", XYSeries.XYSeriesRenderStyle.Line);
    private final XChartPanel<XYChart> costPanel = new XChartPanel<>(costChart);
    private final XChartPanel<XYChart> metricPanel = new XChartPanel<>(metricChart);
    private final JComboBox<String> xAxis = new JComboBox<>(new String[]{"Evaluations", "Time (s)"});
    private final JCheckBox logScale = new JCheckBox("Log scale");
    private final JComboBox<String> metricChoice = new JComboBox<>();
    private final Set<String> costSeries = new HashSet<>();
    private final Set<String> metricSeries = new HashSet<>();

    private List<RunTracker> runs = Collections.emptyList();

    ConvergenceView() {
        super(new BorderLayout());
        JPanel options = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        options.add(new JLabel("X axis:"));
        options.add(xAxis);
        options.add(logScale);

        JPanel metricTab = new JPanel(new BorderLayout());
        JPanel metricTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        metricTop.add(new JLabel("Metric:"));
        metricChoice.setPrototypeDisplayValue("temperature------");
        metricTop.add(metricChoice);
        metricTab.add(metricTop, BorderLayout.NORTH);
        metricTab.add(metricPanel, BorderLayout.CENTER);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Convergence", costPanel);
        tabs.addTab("Metrics", metricTab);

        add(options, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        xAxis.addActionListener(e -> rebuild());
        logScale.addActionListener(e -> rebuild());
        metricChoice.addActionListener(e -> rebuild());
    }

    private static XYChart newChart(String yTitle, XYSeries.XYSeriesRenderStyle style) {
        XYChart chart = new XYChartBuilder().width(800).height(450).xAxisTitle("Evaluations").yAxisTitle(yTitle).build();
        chart.getStyler().setDefaultSeriesRenderStyle(style);
        chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNE);
        chart.getStyler().setMarkerSize(0);
        chart.getStyler().setChartBackgroundColor(UIManager.getColor("Panel.background"));
        chart.getStyler().setLegendBackgroundColor(new Color(255, 255, 255, 200));
        chart.getStyler().setPlotGridLinesColor(new Color(220, 220, 220));
        chart.getStyler().setToolTipsEnabled(false);
        chart.getStyler().setYAxisLogarithmicDecadeOnly(false);
        return chart;
    }

    void setRuns(List<RunTracker> runs) {
        this.runs = runs;
        metricChoice.removeAllItems();
        rebuild();
    }

    // Starts again from empty charts (axes/scale changed or new runs)
    private void rebuild() {
        for (String s : costSeries)
            costChart.removeSeries(s);
        for (String s : metricSeries)
            metricChart.removeSeries(s);
        costSeries.clear();
        metricSeries.clear();
        String x = (String) xAxis.getSelectedItem();
        costChart.setXAxisTitle(x);
        metricChart.setXAxisTitle(x);
        refresh();
    }

    // Pushes the latest data of every run into the charts
    void refresh() {
        boolean time = xAxis.getSelectedIndex() == 1;
        boolean log = logScale.isSelected();
        costChart.getStyler().setYAxisLogarithmic(log);
        metricChart.getStyler().setYAxisLogarithmic(log);

        for (RunTracker t : runs) {
            if (t.costs.isEmpty())
                continue;
            List<Double> xs = new ArrayList<>(time ? t.seconds : t.evaluations);
            List<Double> ys = new ArrayList<>(t.costs);
            // extend the last level to the run's current position so stagnation is visible
            ProgressSnapshot p = t.latestProgress();
            if (p != null) {
                double now = time ? p.elapsedMillis() / 1000.0 : p.evaluations();
                if (now > xs.get(xs.size() - 1)) {
                    xs.add(now);
                    ys.add(ys.get(ys.size() - 1));
                }
            }
            put(costChart, costSeries, name(t), xs, log ? floor(ys) : ys);
        }
        updateMetricChoices();
        String metric = (String) metricChoice.getSelectedItem();
        if (metric != null)
            for (RunTracker t : runs) {
                RunTracker.Series s = t.metrics.get(metric);
                if (s != null && !s.values.isEmpty())
                    put(metricChart, metricSeries, name(t), time ? s.seconds : s.evaluations, log ? floor(s.values) : s.values);
            }
        costPanel.repaint();
        metricPanel.repaint();
    }

    private void updateMetricChoices() {
        Set<String> names = new TreeSet<>();
        for (RunTracker t : runs)
            names.addAll(t.metrics.keySet());
        for (String n : names) {
            boolean present = false;
            for (int i = 0; i < metricChoice.getItemCount(); i++)
                present |= metricChoice.getItemAt(i).equals(n);
            if (!present)
                metricChoice.addItem(n);
        }
    }

    private static void put(XYChart chart, Set<String> existing, String name, List<Double> xs, List<Double> ys) {
        if (existing.add(name)) {
            XYSeries s = chart.addSeries(name, xs, ys);
            s.setMarker(SeriesMarkers.NONE);
        } else
            chart.updateXYSeries(name, xs, ys, null);
    }

    private static List<Double> floor(List<Double> values) {
        List<Double> out = new ArrayList<>(values.size());
        for (double v : values)
            out.add(Math.max(v, LOG_FLOOR));
        return out;
    }

    private static String name(RunTracker t) {
        return "#" + t.index + " " + t.label;
    }
}
