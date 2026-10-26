package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;
import problems.pecto.Route;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The selected participant: what the ants know about it (candidates, hospital order), its pheromone in both
 * directions, its state in the ant being built, and its part in the best solution so far.
 */
final class NodePanel extends JPanel {

    private static final int MAX_ROWS = 25;

    private final JEditorPane text = new JEditorPane("text/html", "");
    private Session session;

    NodePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        text.setEditable(false);
        add(new JScrollPane(text), BorderLayout.CENTER);
    }

    void session(Session session) {
        this.session = session;
    }

    void show(Frame f) {
        int id = session.selected();
        PECTOProblem p = session.problem;
        StringBuilder sb = new StringBuilder("<html><body style='font-family:sans-serif;font-size:10px'>");
        if (id < 0)
            sb.append("<p>Click a participant on the map, or a row of a table, to see what the ants know about it and its pheromone.</p>");
        else if (p.isAmbulance(id))
            ambulance(sb, f, id);
        else if (p.isPatient(id))
            patient(sb, f, id);
        else
            hospital(sb, f, id);
        text.setText(sb.append("</body></html>").toString());
        text.setCaretPosition(0);
    }

    private String l(int id) {
        return session.label(id);
    }

    private String rel(double tau, PheromoneTrails.Snapshot ph) {
        return Style.num(tau / ph.tauMax());
    }

    private void ambulance(StringBuilder sb, Frame f, int a) {
        PECTOProblem p = session.problem;
        sb.append("<h3>Ambulance ").append(l(a)).append("</h3><p>at (").append(Style.num(p.x(a))).append(", ")
                .append(Style.num(p.y(a))).append(")</p>");
        Frame.Construction c = f.construction();
        if (c != null)
            sb.append("<p><b>In the ant being built:</b> ").append(Style.state(c.rateState()[a]))
                    .append(c.rateState()[a] == DiagnosticAnt.UNKNOWN ? "" : ", rating " + Style.num(c.rating()[a])
                            + " by " + l(c.rated()[a])).append("; previous stop ").append(l(c.previous()[a])).append(", at ")
                    .append(l(c.at()[a])).append(", ").append(c.stops()[a].length / 2).append(" trips")
                    .append(c.pending()[a] >= 0 ? " + " + l(c.pending()[a]) + " (hospital provisional)" : "").append("</p>");
        candidates(sb, f, a, "Its first patient: candidates and pheromone τ(" + l(a) + ", q)");
        route(sb, f, a);
    }

    private void patient(StringBuilder sb, Frame f, int q) {
        PECTOProblem p = session.problem;
        PheromoneTrails.Snapshot ph = f.pheromone();
        int k = p.index(q);
        sb.append("<h3>Patient ").append(l(q)).append("</h3><p>").append(p.urgency(q).name().toLowerCase())
                .append(", w = ").append(p.urgencyWeight(q)).append(" · at (").append(Style.num(p.x(q))).append(", ")
                .append(Style.num(p.y(q))).append(")");
        Frame.Construction c = f.construction();
        if (c != null)
            sb.append(" · ").append(c.served()[k] ? "<b>served</b> in the ant being built" : "not served yet in the ant being built");
        sb.append("</p>");
        candidates(sb, f, q, "Who may follow it: candidates and pheromone τ(" + l(q) + ", q')");

        // who may come before it: the nodes that list it, with their pheromone towards it
        int[] before = session.candidates.candidateOf[k];
        List<double[]> pred = new ArrayList<>();
        for (int n : before) {
            int[] cand = session.candidates.patients[n];
            for (int j = 0; j < cand.length; j++)
                if (cand[j] == q)
                    pred.add(new double[]{n, ph.patient()[n][j]});
        }
        pred.sort(Comparator.comparingDouble((double[] x) -> -x[1]));
        sb.append("<p><b>Who may come before it</b>: ").append(before.length).append(" nodes list it as a candidate")
                .append(pred.size() > MAX_ROWS ? " (the " + MAX_ROWS + " strongest)" : "").append("</p><table><tr><th>node</th><th>τ(node, ")
                .append(l(q)).append(") / τmax</th></tr>");
        for (int i = 0; i < Math.min(MAX_ROWS, pred.size()); i++)
            sb.append("<tr><td>").append(l((int) pred.get(i)[0])).append("</td><td align=right>")
                    .append(rel(pred.get(i)[1], ph)).append("</td></tr>");
        sb.append("</table>");

        // its hospitals, nearest first
        int[] order = session.candidates.hospitalOrder[k];
        sb.append("<p><b>Its hospitals</b>, nearest first (a draw considers the first ")
                .append(session.settings.hospitalCandidates()).append(" with a free bed)</p><table><tr><th>hospital</th>")
                .append("<th>D</th><th>w·D</th><th>τ(").append(l(q)).append(", h) / τmax</th>")
                .append(c != null ? "<th>free beds</th>" : "").append("</tr>");
        for (int i = 0; i < Math.min(order.length, MAX_ROWS); i++) {
            int h = p.hospitalId(order[i]);
            sb.append("<tr><td>").append(l(h)).append("</td><td align=right>").append(Style.num(p.distance(q, h)))
                    .append("</td><td align=right>").append(Style.num(p.urgencyWeight(q) * p.distance(q, h)))
                    .append("</td><td align=right>").append(rel(ph.hospital()[k][order[i]], ph)).append("</td>")
                    .append(c != null ? "<td align=right>" + c.room()[order[i]] + "</td>" : "").append("</tr>");
        }
        sb.append("</table>");
        for (Route r : f.best().routes())
            for (int t = 0; t < r.tripCount(); t++)
                if (r.patient(t) == q)
                    sb.append("<p><b>In the best solution so far:</b> picked up by ").append(l(r.ambulance()))
                            .append(" (trip ").append(t + 1).append(" of ").append(r.tripCount()).append("), taken to ")
                            .append(l(r.hospital(t))).append(".</p>");
    }

    private void hospital(StringBuilder sb, Frame f, int h) {
        PECTOProblem p = session.problem;
        PheromoneTrails.Snapshot ph = f.pheromone();
        int hk = p.index(h);
        int inBest = f.best().load(h);
        sb.append("<h3>Hospital ").append(l(h)).append("</h3><p>").append(p.capacity(h)).append(" beds · ")
                .append(inBest).append(" used in the best solution so far");
        Frame.Construction c = f.construction();
        if (c != null)
            sb.append(" · ").append(c.room()[hk]).append(" free in the ant being built");
        sb.append("</p>");
        List<double[]> rows = new ArrayList<>();
        for (int k = 0; k < p.patientCount(); k++)
            rows.add(new double[]{p.patientId(k), ph.hospital()[k][hk]});
        rows.sort(Comparator.comparingDouble((double[] x) -> -x[1]));
        sb.append("<p><b>The patients whose pheromone points here most</b> (τ(q, ").append(l(h)).append(") / τmax)</p>")
                .append("<table><tr><th>patient</th><th>D</th><th>τ / τmax</th></tr>");
        for (int i = 0; i < Math.min(MAX_ROWS, rows.size()); i++) {
            int q = (int) rows.get(i)[0];
            sb.append("<tr><td>").append(l(q)).append("</td><td align=right>").append(Style.num(p.distance(q, h)))
                    .append("</td><td align=right>").append(rel(rows.get(i)[1], ph)).append("</td></tr>");
        }
        sb.append("</table>");
    }

    // the node's candidate patients with their weighted distance and pheromone
    private void candidates(StringBuilder sb, Frame f, int n, String title) {
        PECTOProblem p = session.problem;
        PheromoneTrails.Snapshot ph = f.pheromone();
        int[] cand = session.candidates.patients[n];
        Frame.Construction c = f.construction();
        sb.append("<p><b>").append(title).append("</b> (the ").append(cand.length)
                .append(" nearest by w·D, nearest first)</p><table><tr><th>q</th><th>urgency</th><th>w·D</th><th>τ / τmax</th>")
                .append(c != null ? "<th>served</th>" : "").append("</tr>");
        for (int j = 0; j < Math.min(cand.length, MAX_ROWS); j++) {
            int q = cand[j];
            sb.append("<tr><td>").append(l(q)).append("</td><td>").append(p.urgency(q).name().toLowerCase())
                    .append("</td><td align=right>").append(Style.num(p.urgencyWeight(q) * p.distance(n, q)))
                    .append("</td><td align=right>").append(rel(ph.patient()[n][j], ph)).append("</td>")
                    .append(c != null ? "<td>" + (c.served()[p.index(q)] ? "yes" : "") + "</td>" : "").append("</tr>");
        }
        sb.append("</table>");
    }

    private void route(StringBuilder sb, Frame f, int a) {
        Route r = f.best().route(a);
        PECTOProblem p = session.problem;
        sb.append("<p><b>Its route in the best solution so far</b>: ");
        if (r.isEmpty()) {
            sb.append("no trips (idle).</p>");
            return;
        }
        for (int t = 0; t < r.tripCount(); t++)
            sb.append(t == 0 ? "" : ", ").append(l(r.patient(t))).append("→").append(l(r.hospital(t)));
        sb.append(" · cost ").append(Style.num(p.routeCost(r))).append("</p>");
    }
}
