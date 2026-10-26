package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * The decision of the current event, with every number that made it: the shortlist scan of the ambulances (and how
 * each rating was computed), or the roulette of a patient or hospital draw (weights, probabilities, the random number),
 * or what a pickup changed.
 */
final class DecisionPanel extends JPanel {

    private static final Color[] SEGMENTS = {new Color(0x1F6FEB), new Color(0x8A4FD6), new Color(0x0D8A7F),
            new Color(0xD98200), new Color(0xC2185B), new Color(0x5B6B7F)};

    private final Html header = new Html();
    private final RowTable table = new RowTable("");
    private final RouletteBar roulette = new RouletteBar();
    private final Html detailHeader = new Html();
    private final RowTable detail = new RowTable("q", "served", "τ^α", "w", "D(at, q)", "η^β", "τ^α·η^β");
    private final JPanel tablePanel = new JPanel(new BorderLayout());
    private final JPanel detailPanel = new JPanel(new BorderLayout(0, 4));
    private RowTable current;
    private final JSplitPane split;
    private Session session;
    private Frame frame;

    DecisionPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(header, BorderLayout.NORTH);
        detailPanel.add(detailHeader, BorderLayout.NORTH);
        detailPanel.add(new JScrollPane(detail), BorderLayout.CENTER);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tablePanel, detailPanel);
        split.setResizeWeight(0.55);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        add(roulette, BorderLayout.SOUTH);
        current = table;
    }

    void session(Session session) {
        this.session = session;
    }

    void show(Frame f) {
        this.frame = f;
        Object d = f.decision();
        roulette.setVisible(false);
        detailPanel.setVisible(false);
        if (d instanceof Frame.AmbulanceDecision ad)
            ambulance(ad);
        else if (d instanceof Frame.PatientDecision pd)
            patient(pd);
        else if (d instanceof Frame.HospitalDecision hd)
            hospital(hd);
        else if (d instanceof Frame.StepDone sd)
            stepDone(sd);
        else {
            header.set(("<b>" + f.type().label + "</b><br>No decision at this event. Choose the level "
                    + "<i>Decision</i> and step to see how each ambulance, patient and hospital is drawn."));
            setTable(new RowTable(""));
        }
        revalidate();
        repaint();
    }

    private String l(int id) {
        return session.label(id);
    }


    private void setTable(RowTable t) {
        tablePanel.removeAll();
        tablePanel.add(new JScrollPane(t), BorderLayout.CENTER);
        current = t;
    }

    // ---- ambulance

    private void ambulance(Frame.AmbulanceDecision ad) {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < ad.shortlist().length; i++)
            list.append(i == 0 ? "" : ", ").append(i == ad.pick() ? "<b>" : "").append(i).append(": ")
                    .append(l(ad.shortlist()[i])).append(i == ad.pick() ? "</b>" : "");
        header.set(("<b>Which ambulance moves?</b> Each is rated by its most attractive next patient, "
                + "τ<sup>α</sup>(previous, q)·(1/(w<sub>q</sub>·D(at, q)))<sup>β</sup>. The scan keeps the "
                + session.settings.shortlist() + " best; a stale rating (BOUND) at or below the shortlist's worst is "
                + "ruled out without recomputing it.<br>Shortlist: " + list + ". <code>rng.randInt("
                + ad.shortlist().length + ")</code> = " + ad.pick() + " → <b>" + l(ad.chosen()) + "</b> moves (uniform).<br>"
                + "<i>Select a row to see how its rating was computed.</i>"));
        RowTable t = new RowTable("amb.", "state before", "stored", "threshold", "what happens", "rating", "rated by");
        t.widths(45, 85, 80, 80, 190, 80, 80);
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        for (Frame.ScanRow r : ad.scan()) {
            boolean computed = r.detail() != null;
            boolean kept = r.action().startsWith("into");
            String how = r.stateBefore() == DiagnosticAnt.UNKNOWN ? "computed" : r.rerated() ? "recomputed"
                    : r.stateBefore() == DiagnosticAnt.BOUND ? "bound" : "exact";
            String what = how + (kept ? " → kept" : " → out") + (r.action().contains("replacing") ? " (" + r.action()
                    .substring(r.action().indexOf("replacing") ) + ")" : "");
            rows.add(RowTable.row(l(r.a()), Style.state(r.stateBefore()), Style.num(r.storedBefore()),
                    Style.num(r.threshold()), what, computed || r.stateBefore() == DiagnosticAnt.EXACT
                            ? Style.num(r.rating()) : "≤ " + Style.num(r.rating()), r.ratedBy() < 0 ? "–" : l(r.ratedBy())
                            + (computed && r.detail().fallback() != null ? " (nearest)" : "")));
            ids.add(r.a());
            hl.add(r.a() == ad.chosen());
        }
        t.setRows(rows, ids, hl);
        t.getSelectionModel().addListSelectionListener(e -> {
            int i = t.getSelectedRow();
            if (!e.getValueIsAdjusting() && i >= 0 && i < ad.scan().size())
                showRating(ad.scan().get(i));
        });
        t.onSelect(session::select);
        setTable(t);
        Frame.ScanRow chosen = ad.scan().stream().filter(r -> r.a() == ad.chosen()).findFirst().orElse(null);
        if (chosen != null)
            showRating(chosen);
    }

    private void showRating(Frame.ScanRow r) {
        if (!detailPanel.isVisible()) {
            detailPanel.setVisible(true);
            javax.swing.SwingUtilities.invokeLater(() -> split.setDividerLocation(0.5));
        }
        Frame.Rating d = r.detail();
        if (d == null) {
            detailHeader.set((l(r.a()) + ": " + (r.stateBefore() == DiagnosticAnt.EXACT
                    ? "its rating was still exact, so it was not recomputed."
                    : "its stale rating was an upper bound low enough to rule it out, so it was not recomputed.")));
            detail.setRows(List.of(), List.of(), List.of());
            return;
        }
        detailHeader.set(("<b>" + l(r.a()) + "</b>: previous stop " + l(d.previous()) + " (its candidates), at "
                + l(d.at()) + " (where the pickup leg starts); unserved candidates left: " + d.remaining()
                + (d.fallback() != null ? ". <b>None is left</b>: it is rated by the nearest unserved patient at τ<sub>min</sub>." : ".")));
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        for (Frame.CandidateScore c : d.candidates()) {
            rows.add(RowTable.row(l(c.q()), c.served() ? "yes" : "", Style.num(c.tauA()), Style.num(c.w()),
                    Style.num(c.d()), Style.num(c.etaB()), c.served() || d.remaining() == 0 ? "–" : Style.num(c.score())));
            ids.add(c.q());
            hl.add(d.fallback() == null && c.q() == r.ratedBy());
        }
        if (d.fallback() != null) {
            Frame.CandidateScore c = d.fallback();
            rows.add(RowTable.row(l(c.q()) + " (nearest)", "", "τmin " + Style.num(c.tauA()), Style.num(c.w()),
                    Style.num(c.d()), Style.num(c.etaB()), Style.num(c.score())));
            ids.add(c.q());
            hl.add(true);
        }
        detail.setRows(rows, ids, hl);
    }

    // ---- patient

    private void patient(Frame.PatientDecision pd) {
        PECTOProblem p = session.problem;
        String why = pd.fallback() ? "<b>No candidate is left</b>, so it takes the patient it was rated by (the nearest unserved one)."
                : "Each unserved candidate weighs τ<sup>α</sup>·(1/(w<sub>q</sub>·D(at, q)))<sup>β</sup>; one is drawn in proportion.";
        header.set(("<b>Which patient does " + l(pd.ambulance()) + " take?</b> Its candidates are those of its "
                + "previous stop " + l(pd.previous()) + "; it is at " + l(pd.at()) + ". " + why
                + (pd.ignoredPheromone() ? "<br><b>Explorer draw:</b> this draw ignores the pheromone (τ<sup>α</sup> = 1) and uses β = "
                + session.settings.explorerBeta() + "." : "") + "<br>→ <b>" + l(pd.chosen()) + "</b> ("
                + p.urgency(pd.chosen()).name().toLowerCase() + ")."));
        RowTable t = new RowTable("patient", "available", "τ^α", "w", "D(at, q)", "η^β", "weight", "probability");
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        List<RouletteBar.Segment> segments = new ArrayList<>();
        int chosenIndex = -1;
        for (Frame.Option o : pd.options()) {
            rows.add(RowTable.row(l(o.id()), o.available() ? "yes" : "served", Style.num(o.tauA()), Style.num(o.w()),
                    Style.num(o.d()), Style.num(o.etaB()), Style.num(o.weight()),
                    pd.sum() > 0 ? Style.pct(o.weight() / pd.sum()) : "–"));
            ids.add(o.id());
            hl.add(o.id() == pd.chosen());
            if (o.id() == pd.chosen() && !pd.fallback())
                chosenIndex = segments.size();
            segments.add(new RouletteBar.Segment(l(o.id()), o.weight(), SEGMENTS[segments.size() % SEGMENTS.length]));
        }
        t.setRows(rows, ids, hl);
        t.onSelect(session::select);
        setTable(t);
        if (!pd.fallback()) {
            roulette.show(segments, pd.u(), chosenIndex);
            roulette.setVisible(true);
        }
    }

    // ---- hospital

    private void hospital(Frame.HospitalDecision hd) {
        String what = hd.provisional()
                ? "<b>A provisional hospital for " + l(hd.patient()) + ".</b> The next patient of " + l(hd.ambulance())
                + " is not known yet, so the draw weighs the delivery leg only: cost = w·D(p, h). The hospital takes a "
                + "bed now, and it is where " + l(hd.ambulance()) + " waits."
                : "<b>The final hospital of " + l(hd.patient()) + ".</b> " + l(hd.ambulance()) + "'s next patient is "
                + l(hd.next()) + ", so the provisional bed at " + l(hd.previous()) + " is released and the hospital is "
                + "drawn again with the full cost it causes: cost = w<sub>p</sub>·D(p, h) + w<sub>next</sub>·D(h, next).";
        header.set((what + " Only the first " + session.settings.hospitalCandidates() + " hospitals with a "
                + "free bed, nearest first, are considered; weight = τ<sup>α</sup>(p, h)·(1/cost)<sup>β</sup>."
                + (hd.ignoredPheromone() ? "<br><b>Explorer draw:</b> the pheromone is ignored here." : "")
                + "<br>→ <b>" + l(hd.chosen()) + "</b>" + (!hd.provisional() && hd.chosen() != hd.previous()
                ? " (changed from " + l(hd.previous()) + ")" : "") + "."));
        RowTable t = new RowTable("hospital", "free beds", "considered", "τ^α", "D(p, h)", "D(h, next)", "cost", "weight",
                "probability");
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        List<RouletteBar.Segment> segments = new ArrayList<>();
        int chosenIndex = -1;
        int shown = 0;
        for (Frame.HospitalOption o : hd.options()) {
            if (!o.considered() && shown >= session.settings.hospitalCandidates() + 6)
                continue;   // far hospitals that the draw never reaches
            shown++;
            rows.add(RowTable.row(l(o.h()), String.valueOf(o.room()), o.considered() ? "yes" : o.room() == 0 ? "full" : "no",
                    Style.num(o.tauA()), Style.num(o.dDeliver()), Style.num(o.dNext()), Style.num(o.cost()),
                    Style.num(o.weight()), o.considered() && hd.sum() > 0 ? Style.pct(o.weight() / hd.sum()) : "–"));
            ids.add(o.h());
            hl.add(o.h() == hd.chosen());
            if (o.considered()) {
                if (o.h() == hd.chosen())
                    chosenIndex = segments.size();
                segments.add(new RouletteBar.Segment(l(o.h()), o.weight(), SEGMENTS[segments.size() % SEGMENTS.length]));
            }
        }
        t.setRows(rows, ids, hl);
        t.onSelect(session::select);
        setTable(t);
        roulette.show(segments, hd.u(), chosenIndex);
        roulette.setVisible(true);
    }

    // ---- bookkeeping

    private void stepDone(Frame.StepDone sd) {
        StringBuilder nodes = new StringBuilder();
        for (int n : sd.candidateOf())
            nodes.append(nodes.length() == 0 ? "" : " ").append(l(n));
        header.set(("<b>" + l(sd.ambulance()) + " picked up " + l(sd.patient()) + "</b> and waits at "
                + l(sd.hospital()) + " (provisionally). " + l(sd.patient()) + " is served, so every node that lists it "
                + "as a candidate has one unserved candidate less: " + (nodes.length() == 0 ? "none" : nodes) + ".<br>"
                + "Ratings based on " + l(sd.patient()) + " are stale now (see the table); " + l(sd.ambulance())
                + " itself moved, so its rating is UNKNOWN."));
        RowTable t = new RowTable("ambulance", "rating becomes", "why");
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        for (Frame.Invalidation i : sd.invalidated()) {
            String why = switch (i.why()) {
                case "candidates left" -> "it still has unserved candidates: the old rating is an upper bound";
                case "rated by the nearest patient" -> "it was rated by the nearest patient; the next one is farther";
                default -> "no candidate is left: it will be rated by the nearest patient, which may score higher";
            };
            rows.add(RowTable.row(l(i.ambulance()), i.ambulance() == sd.ambulance() ? "UNKNOWN (it moved)"
                    : Style.state(i.to()), why));
            ids.add(i.ambulance());
        }
        if (sd.invalidated().stream().noneMatch(i -> i.ambulance() == sd.ambulance())) {
            rows.add(RowTable.row(l(sd.ambulance()), "UNKNOWN", "it moved"));
            ids.add(sd.ambulance());
        }
        t.setRows(rows, ids, new ArrayList<>(java.util.Collections.nCopies(rows.size(), false)));
        t.onSelect(session::select);
        setTable(t);
    }
}
