package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/**
 * The ant being built: every ambulance with its rating (EXACT, BOUND, UNKNOWN), where it is and its trips, and every
 * hospital with its free beds.
 */
final class ConstructionPanel extends JPanel {

    private final Html header = new Html();
    private final RowTable ambulances = new RowTable("ambulance", "rating state", "rating", "rated by", "previous", "at",
            "trips", "pending");
    private final RowTable hospitals = new RowTable("hospital", "beds", "used", "free");
    private Session session;

    ConstructionPanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(header, BorderLayout.NORTH);
        ambulances.widths(70, 85, 85, 100, 70, 70, 45, 120);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(ambulances), new JScrollPane(hospitals));
        split.setResizeWeight(0.7);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
    }

    void session(Session session) {
        this.session = session;
        ambulances.onSelect(session::select);
        hospitals.onSelect(session::select);
    }

    void show(Frame f, boolean paused) {
        Frame.Construction c = f.construction();
        PECTOProblem p = session.problem;
        if (c == null) {
            header.set("<b>No ant is being built at this event.</b> Step at the level <i>Ant</i>, "
                    + "<i>Construction step</i> or <i>Decision</i> to watch the ants.");
            ambulances.setRows(List.of(), List.of(), List.of());
            hospitals.setRows(List.of(), List.of(), List.of());
            return;
        }
        int served = 0;
        for (boolean s : c.served())
            if (s)
                served++;
        header.set("<b>Ant " + f.ant() + "</b>" + (f.explorer() ? " (explorer)" : "") + " of iteration "
                + f.iteration() + " · step " + f.step() + " of " + p.patientCount() + " · " + served + " patients served"
                + "<br><span style='color:gray'>EXACT: the rating holds now; BOUND: stale, but at least the true value; "
                + "UNKNOWN: must be computed. Trips are fixed; the pending patient's hospital is provisional.</span>");
        if (!paused && !session.practicality.liveTables())
            return;
        List<Object[]> rows = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Boolean> hl = new ArrayList<>();
        int focus = focus(f);
        for (int a = 0; a < p.ambulanceCount(); a++) {
            boolean unknown = c.rateState()[a] == DiagnosticAnt.UNKNOWN;
            rows.add(RowTable.row(session.label(a), Style.state(c.rateState()[a]), unknown ? "–" : Style.num(c.rating()[a]),
                    unknown ? "–" : session.label(c.rated()[a]) + (c.ratedNearest()[a] ? " (nearest)" : ""),
                    session.label(c.previous()[a]), session.label(c.at()[a]), String.valueOf(c.stops()[a].length / 2),
                    c.pending()[a] < 0 ? "" : session.label(c.pending()[a]) + " → " + session.label(c.pendingHospital()[a])));
            ids.add(a);
            hl.add(a == focus);
        }
        ambulances.setRows(rows, ids, hl);
        List<Object[]> hr = new ArrayList<>();
        List<Integer> hid = new ArrayList<>();
        List<Boolean> hhl = new ArrayList<>();
        for (int k = 0; k < p.hospitalCount(); k++) {
            int id = p.hospitalId(k), cap = p.capacity(id);
            hr.add(RowTable.row(session.label(id), String.valueOf(cap), String.valueOf(cap - c.room()[k]), String.valueOf(c.room()[k])));
            hid.add(id);
            hhl.add(c.room()[k] == 0);
        }
        hospitals.setRows(hr, hid, hhl);
    }

    // the ambulance the event is about
    private static int focus(Frame f) {
        Object d = f.decision();
        if (d instanceof Frame.AmbulanceDecision ad)
            return ad.chosen();
        if (d instanceof Frame.PatientDecision pd)
            return pd.ambulance();
        if (d instanceof Frame.HospitalDecision hd)
            return hd.ambulance();
        if (d instanceof Frame.StepDone sd)
            return sd.ambulance();
        return -1;
    }
}
