package problems.pecto.aco.gui;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * The iteration: its ants with their costs (explorers marked), how the best of them was improved (local search, then
 * the optimal hospitals), and the pheromone update (who deposited, the new bounds, the branching factor).
 */
final class IterationPanel extends JPanel {

    private final Html header = new Html();
    private final RowTable ants = new RowTable("ant", "kind", "cost", "vs iteration's best ant");
    private final Html footer = new Html();
    private Session session;

    IterationPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(header, BorderLayout.NORTH);
        add(new JScrollPane(ants), BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    void session(Session session) {
        this.session = session;
    }

    void show(Frame f) {
        ColonySettings s = session.settings;
        header.set("<b>Iteration " + f.iteration() + "</b> · " + f.ants().size() + " of " + s.ants()
                + " ants built · best so far " + Style.num(f.bestCost()) + " · " + String.format("%,d", f.evaluations())
                + " evaluations");
        double antBest = f.ants().stream().mapToDouble(Frame.AntResult::cost).min().orElse(Double.NaN);
        List<Object[]> rows = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        for (Frame.AntResult r : f.ants()) {
            rows.add(RowTable.row(String.valueOf(r.index()), r.explorer() ? "explorer" : "", Style.num(r.cost()),
                    r.cost() == antBest ? "best" : "+" + Style.pct((r.cost() - antBest) / antBest)));
            hl.add(r.cost() == antBest);
        }
        ants.setRows(rows, new ArrayList<>(java.util.Collections.nCopies(rows.size(), -1)), hl);

        StringBuilder sb = new StringBuilder();
        Frame.Improvement im = f.improvement();
        if (im == null)
            sb.append("<b>Improvement:</b> after the last ant, the iteration's best ant is improved.<br>");
        else {
            sb.append("<b>Improvement of ant ").append(im.ant()).append(im.explorer() ? " (explorer)" : "").append(":</b> ")
                    .append(Style.num(im.start())).append(" → local search (").append(im.improving()).append(" of ")
                    .append(im.tried()).append(" neighbors improved) ").append(Style.num(im.afterLocalSearch()));
            if (!Double.isNaN(im.afterHospitals()))
                sb.append(" → optimal hospitals ").append(Style.num(im.afterHospitals()));
            sb.append(" = <b>").append(Style.num(im.result())).append("</b> (")
                    .append(Style.pct((im.start() - im.result()) / im.start())).append(" better)<br>");
        }
        Frame.Deposit d = f.deposit();
        if (d == null)
            sb.append("<b>Pheromone:</b> evaporation and the deposit follow the improvement.");
        else {
            sb.append("<b>Pheromone:</b> everything evaporated by ρ = ").append(s.rho()).append("; ")
                    .append(d.globalBest() ? "the <b>best so far</b> (every " + ColonyRun.GLOBAL_BEST_PERIOD + "th iteration)"
                            : "the <b>iteration best</b>").append(" (cost ").append(Style.num(d.depositorCost()))
                    .append(") deposited ").append(Style.num(d.amount())).append(" on each of its choices");
            if (d.explorerDeposited())
                sb.append("; the best explorer (cost ").append(Style.num(d.explorerCost())).append(") deposited ")
                        .append(Style.num(d.explorerAmount()));
            sb.append(". Bounds: τ<sub>max</sub> = ").append(Style.num(d.tauMax())).append(", τ<sub>min</sub> = ")
                    .append(Style.num(d.tauMinPatient())).append(" (patients), ").append(Style.num(d.tauMinHospital()))
                    .append(" (hospitals). Branching factor ").append(Style.num(d.branchingFactor())).append(".");
        }
        footer.set(sb.toString());
    }
}
