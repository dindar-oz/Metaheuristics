package lab.problemlab.pecto;

import lab.problemlab.model.LabState;
import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ProblemView;
import lab.problemlab.ui.ViewHost;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.ParticipantType;
import problems.pecto.Route;
import problems.pecto.Urgency;
import problems.pecto.operators.GreedyPECTOSG;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.IntStream;

/**
 * The interactive PECTO view: a map of the area ({@link PECTOCanvas}) with the participants and the routes, the
 * properties of the selected participant ({@link SelectionPanel}) and tables of the routes, trips and hospitals
 * ({@link RoutesPanel}). It is the controller of these parts: they report gestures, and it turns them into edits of
 * the instance ({@link PECTOInstanceEdit}) or of the solution ({@link PECTOTripEdit}) made through the workspace, so
 * that every edit is evaluated, recorded in the history and can be undone.
 */
final class PECTOEditor implements ProblemView<PECTOSolution, PECTOProblem> {

    // what a left click or drag on the map does
    enum Tool {
        SELECT("Select / move", KeyEvent.VK_V,
                "Click to select · drag a participant to move it · drag the background to pan · wheel to zoom · right-click for actions"),
        ROUTE("Edit routes", KeyEvent.VK_R,
                "Drag a patient onto a hospital (deliver there), an ambulance (append to its route) or another patient (insert before it)"),
        AMBULANCE("+ Ambulance", KeyEvent.VK_A, "Click to add an ambulance"),
        PATIENT("+ Patient", KeyEvent.VK_P, "Click to add a patient with the urgency chosen in the tool bar"),
        HOSPITAL("+ Hospital", KeyEvent.VK_H, "Click to add a hospital with the beds chosen in the tool bar");

        final String label;
        final int key;
        final String hint;

        Tool(String label, int key, String hint) {
            this.label = label;
            this.key = key;
            this.hint = hint;
        }
    }

    private static final String HELP = """
            <html><body style='font-family:sans-serif; font-size:11px; padding:6px'>
            <h3>Map tools</h3>
            <table cellpadding='3'>
            <tr><td><b>V</b></td><td>Select / move: click a participant to select it, drag it to move it.
                Drag the background to pan.</td></tr>
            <tr><td><b>R</b></td><td>Edit routes: drag a <i>patient</i> onto a <i>hospital</i> to deliver it there, onto an
                <i>ambulance</i> to append its trip to that route, or onto another <i>patient</i> to insert it before that trip.
                Dragging an ambulance or a hospital onto a patient works the other way round.</td></tr>
            <tr><td><b>A / P / H</b></td><td>Click to add an ambulance, a patient (urgency from the tool bar) or a hospital
                (beds from the tool bar).</td></tr>
            </table>
            <h3>Everywhere on the map</h3>
            <table cellpadding='3'>
            <tr><td>Right-click</td><td>actions for the participant under the mouse, or to add one there</td></tr>
            <tr><td>Wheel / middle drag</td><td>zoom around the mouse / pan; <b>F</b> fits the area again</td></tr>
            <tr><td>Hover</td><td>details of a participant: urgency, trip, trip cost, beds taken</td></tr>
            </table>
            <h3>Keys for the selected participant</h3>
            <table cellpadding='3'>
            <tr><td><b>Arrow keys</b></td><td>move it by 1 (with Shift: by 10)</td></tr>
            <tr><td><b>Delete</b></td><td>remove it from the instance (its trips are dropped)</td></tr>
            <tr><td><b>1 … 5</b></td><td>patient urgency: 1 stable, 2 light, 3 moderate, 4 heavy, 5 critical</td></tr>
            <tr><td><b>+ / −</b></td><td>hospital capacity</td></tr>
            <tr><td><b>U</b> or Backspace</td><td>take the patient's trip out of its route</td></tr>
            <tr><td><b>[ / ]</b></td><td>move the patient's trip earlier / later in its route</td></tr>
            <tr><td><b>G</b></td><td>assign every untransported patient greedily</td></tr>
            <tr><td><b>Esc</b></td><td>clear the selection and return to the select tool</td></tr>
            </table>
            <p>Click the map first so that it receives the keys. Ctrl+Z / Ctrl+Y undo and redo every edit;
            the history below the map lists them with their effect on the cost.</p>
            <h3>Reading the map</h3>
            <p>Squares are ambulances, dots patients (red critical … blue stable), crosses hospitals with a ring that
            fills as beds are taken (red when over capacity). Red circles mark patients that are not transported or are
            transported twice. After an edit or a mutation, the routes it changed are drawn thick, with their previous
            version dashed in gray.</p>
            </body></html>
            """;

    private final Workspace<PECTOSolution, PECTOProblem> workspace;
    private final ViewHost host;
    private final PECTOCanvas canvas = new PECTOCanvas(this);
    private final SelectionPanel selectionPanel = new SelectionPanel(this);
    private final RoutesPanel routesPanel = new RoutesPanel(this);
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<Tool, JToggleButton> toolButtons = new EnumMap<>(Tool.class);
    private final JComboBox<Urgency> newUrgency = new JComboBox<>(Urgency.values());
    private final JSpinner newCapacity = new JSpinner(new SpinnerNumberModel(20, 0, 1_000_000, 1));
    private final Action assignMissing = action("Assign missing", "Add every untransported patient greedily (nearest route end, nearest hospital with room) [G]",
            () -> editSolution("Assigned untransported patients greedily", GreedyPECTOSG::complete));
    private final Action repair = action("Repair", "Drop repeated trips and trips beyond hospital capacity, then assign the missing patients greedily",
            () -> editSolution("Repaired the solution", GreedyPECTOSG::repair));
    private final Action fit = action("Fit", "Show the whole area [F]", canvas::fit);

    private PECTOFacts facts;               // of the current state, null before the first instance
    private int selected = -1;              // selected participant, -1 if none
    private ParticipantType selectedType;
    private int route = -1;                 // ambulance whose route is highlighted, -1 if none
    private boolean ownEdit;                // the next state change is an edit made here
    private int nextSelected, nextRoute;    // the selection to show after that edit

    PECTOEditor(Workspace<PECTOSolution, PECTOProblem> workspace, ViewHost host) {
        this.workspace = workspace;
        this.host = host;
        root.add(buildToolBar(), BorderLayout.NORTH);
        JPanel side = new JPanel(new BorderLayout(0, 6));
        side.add(selectionPanel, BorderLayout.NORTH);
        side.add(routesPanel, BorderLayout.CENTER);
        side.setPreferredSize(new Dimension(360, 400));
        side.setMinimumSize(new Dimension(250, 100));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, side);
        split.setResizeWeight(1);
        split.setContinuousLayout(true);
        root.add(split, BorderLayout.CENTER);
        bindKeys();
        setTool(Tool.SELECT);
    }

    private JComponent buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        ButtonGroup group = new ButtonGroup();
        for (Tool t : Tool.values()) {
            JToggleButton b = new JToggleButton(t.label);
            b.setToolTipText(t.hint + " [" + KeyEvent.getKeyText(t.key) + "]");
            b.addActionListener(e -> setTool(t));
            group.add(b);
            bar.add(b);
            toolButtons.put(t, b);
        }
        newUrgency.setSelectedItem(Urgency.MODERATE);
        newUrgency.setMaximumSize(new Dimension(110, 28));
        newUrgency.setToolTipText("Urgency of the patients added with + Patient");
        newUrgency.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value == null ? "" : name((Urgency) value), index, selected, focus);
                if (value != null)
                    setIcon(dot(PECTOCanvas.urgencyColor((Urgency) value)));
                return this;
            }
        });
        newCapacity.setMaximumSize(new Dimension(70, 28));
        newCapacity.setPreferredSize(new Dimension(70, 28));
        newCapacity.setToolTipText("Beds of the hospitals added with + Hospital");
        bar.addSeparator();
        bar.add(new JLabel("urgency "));
        bar.add(newUrgency);
        bar.add(new JLabel("  beds "));
        bar.add(newCapacity);
        bar.addSeparator();
        bar.add(assignMissing);
        bar.add(repair);
        bar.addSeparator();
        bar.add(checkBox("All routes", true, "Draw every route, not only the selected one", canvas::setAllRoutes));
        bar.add(checkBox("IDs", false, "Label every participant with its ID", canvas::setLabels));
        bar.add(checkBox("Changes", true, "Highlight the routes the last action changed, with their previous version dashed", canvas::setShowChanges));
        bar.add(Box.createHorizontalGlue());
        bar.add(fit);
        return bar;
    }

    // single keys act on the map only when it has the focus, so they never interfere with typing elsewhere
    private void bindKeys() {
        for (Tool t : Tool.values())
            key(KeyStroke.getKeyStroke(t.key, 0), "tool " + t, () -> setTool(t));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape", () -> {
            select(-1);
            setTool(Tool.SELECT);
        });
        key(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete", () -> deleteParticipant(selected));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_U, 0), "unassign", () -> unassign(selected));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0), "unassign2", () -> unassign(selected));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, 0), "earlier", () -> shiftTrip(selected, -1));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_CLOSE_BRACKET, 0), "later", () -> shiftTrip(selected, 1));
        key(KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "fit", canvas::fit);
        key(KeyStroke.getKeyStroke(KeyEvent.VK_G, 0), "assign", () -> assignMissing.actionPerformed(null));
        Urgency[] levels = Urgency.values();
        for (int k = 0; k < levels.length; k++) {
            Urgency u = levels[k];
            key(KeyStroke.getKeyStroke(KeyEvent.VK_1 + k, 0), "urgency " + u, () -> setUrgency(selected, u));
            key(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD1 + k, 0), "urgency pad " + u, () -> setUrgency(selected, u));
        }
        for (int code : new int[]{KeyEvent.VK_PLUS, KeyEvent.VK_EQUALS, KeyEvent.VK_ADD})
            key(KeyStroke.getKeyStroke(code, 0), "more beds " + code, () -> changeCapacity(selected, 1));
        for (int code : new int[]{KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT})
            key(KeyStroke.getKeyStroke(code, 0), "fewer beds " + code, () -> changeCapacity(selected, -1));
        int[][] arrows = {{KeyEvent.VK_LEFT, -1, 0}, {KeyEvent.VK_RIGHT, 1, 0}, {KeyEvent.VK_UP, 0, 1}, {KeyEvent.VK_DOWN, 0, -1}};
        for (int[] a : arrows) {
            key(KeyStroke.getKeyStroke(a[0], 0), "nudge " + a[0], () -> nudge(a[1], a[2]));
            key(KeyStroke.getKeyStroke(a[0], InputEvent.SHIFT_DOWN_MASK), "nudge10 " + a[0], () -> nudge(10 * a[1], 10 * a[2]));
        }
    }

    private void key(KeyStroke stroke, String name, Runnable body) {
        canvas.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        canvas.getActionMap().put(name, action(name, null, body));
    }

    // ------------------------------------------------------------------ ProblemView

    @Override
    public JComponent component() {
        return root;
    }

    @Override
    public void stateChanged(LabState<PECTOSolution, PECTOProblem> previous, LabState<PECTOSolution, PECTOProblem> current) {
        if (current == null) {
            facts = null;
            canvas.show(null, null);
            routesPanel.show(null, null);
            selectionPanel.show(null, -1);
            updateActions();
            return;
        }
        PECTOProblem problem = current.problem();
        facts = new PECTOFacts(problem, current.solution());

        if (ownEdit) {
            selected = nextSelected;
            route = nextRoute;
        } else if (!current.sameInstance(previous)) {
            // another instance (generated, opened, undone): keep the selection only where it still makes sense
            if (selected >= problem.participantCount() || selected >= 0 && problem.typeOf(selected) != selectedType)
                selected = -1;
            if (route >= problem.ambulanceCount())
                route = -1;
        }
        ownEdit = false;
        selectedType = selected >= 0 ? problem.typeOf(selected) : null;
        if (selected >= 0 && problem.isPatient(selected) && facts.carrierOf(selected) >= 0)
            route = facts.carrierOf(selected);      // follow the selected patient to its route

        // the routes the change touched, if the instance stayed the same
        Route[] before = null;
        Double[] delta = null;          // by ambulance: the change of the route's cost, null if the route did not change
        if (previous != null && current.sameInstance(previous) && previous.hasSolution() && current.hasSolution()) {
            before = new Route[problem.ambulanceCount()];
            delta = new Double[problem.ambulanceCount()];
            for (int a = 0; a < before.length; a++) {
                Route old = previous.solution().route(a), now = current.solution().route(a);
                if (old != now && !old.equals(now)) {
                    before[a] = old;
                    delta[a] = problem.routeCost(now) - problem.routeCost(old);
                }
            }
        }
        canvas.show(facts, before);
        routesPanel.show(facts, delta);
        refreshSelection();
        updateActions();
    }

    @Override
    public List<Action> actions() {
        List<Action> actions = new ArrayList<>(List.of(assignMissing, repair, fit));
        // no accelerators: they would act window-wide, also while typing; the map binds the keys when it has the focus
        for (Tool t : Tool.values())
            actions.add(action("Tool: %s   (%s on the map)".formatted(t.label, KeyEvent.getKeyText(t.key)), t.hint, () -> setTool(t)));
        return actions;
    }

    @Override
    public String help() {
        return HELP;
    }

    // ------------------------------------------------------------------ selection and tools

    PECTOFacts facts() {
        return facts;
    }

    ViewHost host() {
        return host;
    }

    void setTool(Tool tool) {
        toolButtons.get(tool).setSelected(true);
        canvas.setTool(tool);
        host.status(tool.label + ": " + tool.hint);
    }

    Urgency newUrgency() {
        return (Urgency) newUrgency.getSelectedItem();
    }

    int newCapacity() {
        return (Integer) newCapacity.getValue();
    }

    // selects a participant (-1: none); a patient or an ambulance also highlights its route
    void select(int id) {
        if (facts == null)
            return;
        selected = id;
        selectedType = id >= 0 ? facts.problem.typeOf(id) : null;
        if (id < 0)
            route = -1;
        else if (facts.problem.isAmbulance(id))
            route = id;
        else if (facts.problem.isPatient(id) && facts.carrierOf(id) >= 0)
            route = facts.carrierOf(id);
        refreshSelection();
    }

    // highlights the route of the ambulance and selects the ambulance
    void selectRoute(int ambulance) {
        select(ambulance);
    }

    int selected() {
        return selected;
    }

    int route() {
        return route;
    }

    private void refreshSelection() {
        canvas.setSelection(selected, route);
        routesPanel.setSelection(selected, route);
        selectionPanel.show(facts, selected);
    }

    private void updateActions() {
        boolean solution = facts != null && facts.hasSolution();
        assignMissing.setEnabled(solution);
        repair.setEnabled(solution);
    }

    // ------------------------------------------------------------------ instance edits

    void moveParticipant(int id, double x, double y, Object mergeKey) {
        editInstance("Moved " + describe(id), mergeKey,
                () -> PECTOInstanceEdit.move(facts.problem, facts.solution, id, x, y));
    }

    void nudge(double dx, double dy) {
        if (selected < 0)
            return;
        PECTOProblem p = facts.problem;
        moveParticipant(selected, p.x(selected) + dx, p.y(selected) + dy, List.of("move", selected));
    }

    void addParticipant(Tool tool, double x, double y) {
        if (facts == null) {
            host.error("Generate or open an instance first (\"Blank area\" starts from an empty map).");
            return;
        }
        ParticipantType type = switch (tool) {
            case AMBULANCE -> ParticipantType.AMBULANCE;
            case PATIENT -> ParticipantType.PATIENT;
            case HOSPITAL -> ParticipantType.HOSPITAL;
            default -> throw new IllegalArgumentException("tool " + tool + " adds nothing");
        };
        String what = switch (type) {
            case AMBULANCE -> "an ambulance";
            case PATIENT -> "a " + name(newUrgency()) + " patient";
            case HOSPITAL -> "a hospital with " + newCapacity() + " beds";
        };
        editInstance("Added " + what + " at (%.1f, %.1f)".formatted(x, y), null,
                () -> PECTOInstanceEdit.add(facts.problem, facts.solution, type, x, y, newUrgency(), newCapacity()));
    }

    void deleteParticipant(int id) {
        if (id < 0)
            return;
        editInstance("Removed " + describe(id), null, () -> PECTOInstanceEdit.remove(facts.problem, facts.solution, id));
    }

    void setUrgency(int patient, Urgency urgency) {
        if (patient < 0 || !facts.problem.isPatient(patient) || facts.problem.urgency(patient) == urgency)
            return;
        editInstance("Patient %d: urgency %s → %s".formatted(patient, name(facts.problem.urgency(patient)), name(urgency)), null,
                () -> PECTOInstanceEdit.setUrgency(facts.problem, facts.solution, patient, urgency));
    }

    void setCapacity(int hospital, int capacity) {
        if (hospital < 0 || !facts.problem.isHospital(hospital) || facts.problem.capacity(hospital) == capacity)
            return;
        editInstance("Hospital %d: %d beds".formatted(hospital, capacity), List.of("beds", hospital),
                () -> PECTOInstanceEdit.setCapacity(facts.problem, facts.solution, hospital, capacity));
    }

    void changeCapacity(int hospital, int delta) {
        if (hospital >= 0 && facts.problem.isHospital(hospital))
            setCapacity(hospital, Math.max(0, facts.problem.capacity(hospital) + delta));
    }

    private void editInstance(String action, Object mergeKey, Supplier<PECTOInstanceEdit.Result> edit) {
        if (facts == null)
            return;
        PECTOInstanceEdit.Result result;
        try {
            result = edit.get();
        } catch (IllegalArgumentException e) {
            host.error("Cannot do this: " + e.getMessage());
            return;
        }
        ownEdit = true;
        nextSelected = result.created() >= 0 ? result.created() : result.newId(selected);
        nextRoute = result.newId(route);
        workspace.change(result.problem(), result.solution(), action, mergeKey);
    }

    // ------------------------------------------------------------------ solution edits

    void unassign(int patient) {
        if (isPatient(patient))
            editSolution("Took patient " + patient + " out of its route", s -> PECTOTripEdit.unassign(s, patient));
    }

    void shiftTrip(int patient, int delta) {
        if (isPatient(patient))
            editSolution("Moved the trip of patient " + patient + (delta < 0 ? " earlier" : " later"),
                    s -> PECTOTripEdit.shift(s, patient, delta));
    }

    void deliver(int patient, int hospital) {
        editSolution("Patient " + patient + " delivered to hospital " + hospital, s -> PECTOTripEdit.deliver(s, patient, hospital));
    }

    void moveTripTo(int patient, int ambulance) {
        editSolution("Trip of patient " + patient + " moved to the end of ambulance " + ambulance + "'s route",
                s -> PECTOTripEdit.moveTrip(s, patient, ambulance, -1));
    }

    void insertBefore(int patient, int other) {
        editSolution("Trip of patient " + patient + " inserted before patient " + other,
                s -> PECTOTripEdit.insertBefore(s, patient, other));
    }

    void clearRoute(int ambulance) {
        editSolution("Cleared the route of ambulance " + ambulance, s -> PECTOTripEdit.clearRoute(s, ambulance));
    }

    void reverseRoute(int ambulance) {
        editSolution("Reversed the route of ambulance " + ambulance, s -> PECTOTripEdit.reverseRoute(s, ambulance));
    }

    private void editSolution(String action, UnaryOperator<PECTOSolution> edit) {
        if (facts == null)
            return;
        if (!facts.hasSolution()) {
            host.error("There is no solution yet: generate one (\"Empty\" to build the routes by hand).");
            return;
        }
        PECTOSolution next = edit.apply(facts.solution);
        if (next == facts.solution) {
            host.status("Nothing to change");
            return;
        }
        ownEdit = true;
        nextSelected = selected;
        nextRoute = route;
        workspace.change(facts.problem, next, action);
    }

    /**
     * What dropping source onto target does in the route tool, as a phrase (null if nothing): a patient onto a
     * hospital, an ambulance or another patient, or an ambulance or a hospital onto a patient.
     */
    String linkDescription(int source, int target) {
        if (facts == null || !facts.hasSolution() || source < 0 || target < 0 || source == target)
            return null;
        PECTOProblem p = facts.problem;
        if (p.isPatient(source)) {
            if (p.isHospital(target))
                return "deliver to hospital " + target;
            if (p.isAmbulance(target))
                return "append to ambulance " + target + "'s route";
            if (p.isPatient(target) && facts.carrierOf(target) >= 0)
                return "insert before patient " + target;
        } else if (p.isPatient(target)) {
            if (p.isAmbulance(source))
                return "append patient " + target + " to this route";
            if (p.isHospital(source))
                return "deliver patient " + target + " here";
        }
        return null;
    }

    void link(int source, int target) {
        if (linkDescription(source, target) == null)
            return;
        PECTOProblem p = facts.problem;
        int patient = p.isPatient(source) ? source : target, other = patient == source ? target : source;
        if (p.isHospital(other))
            deliver(patient, other);
        else if (p.isAmbulance(other))
            moveTripTo(patient, other);
        else
            insertBefore(patient, other);
    }

    // ------------------------------------------------------------------ context menu

    void showContextMenu(int id, Component invoker, Point at, Point2D world) {
        if (facts == null)
            return;
        int patientBefore = isPatient(selected) ? selected : -1;    // can be delivered to a right-clicked hospital
        if (id >= 0)
            select(id);
        JPopupMenu menu = new JPopupMenu();
        PECTOProblem p = facts.problem;
        boolean solution = facts.hasSolution();
        if (id < 0) {
            for (Tool t : List.of(Tool.AMBULANCE, Tool.PATIENT, Tool.HOSPITAL))
                menu.add(item("Add " + t.label.substring(2).toLowerCase(Locale.ROOT) + " here"
                        + (t == Tool.PATIENT ? " (" + name(newUrgency()) + ")" : t == Tool.HOSPITAL ? " (" + newCapacity() + " beds)" : ""),
                        () -> addParticipant(t, world.getX(), world.getY())));
            menu.addSeparator();
            menu.add(item("Fit the area", canvas::fit));
        } else if (p.isPatient(id)) {
            menu.add(header("Patient " + id + " · " + name(p.urgency(id))));
            JMenu urgency = new JMenu("Urgency");
            for (Urgency u : Urgency.values()) {
                JRadioButtonMenuItem item = new JRadioButtonMenuItem(name(u) + " (weight " + u.weight() + ")", u == p.urgency(id));
                item.setIcon(dot(PECTOCanvas.urgencyColor(u)));
                item.addActionListener(e -> setUrgency(id, u));
                urgency.add(item);
            }
            menu.add(urgency);
            if (solution) {
                menu.add(hospitalMenu(id));
                menu.add(ambulanceMenu(id));
                boolean assigned = facts.carrierOf(id) >= 0;
                menu.add(enabled(item("Earlier in its route  [", () -> shiftTrip(id, -1)), assigned));
                menu.add(enabled(item("Later in its route  ]", () -> shiftTrip(id, 1)), assigned));
                menu.add(enabled(item("Take out of its route  U", () -> unassign(id)), assigned));
            }
            menu.addSeparator();
            menu.add(item("Remove patient  Del", () -> deleteParticipant(id)));
        } else if (p.isHospital(id)) {
            menu.add(header("Hospital " + id + " · " + facts.load(id) + " of " + p.capacity(id) + " beds taken"));
            menu.add(item("Set beds…", () -> askCapacity(id)));
            menu.add(item("One more bed  +", () -> changeCapacity(id, 1)));
            menu.add(item("One bed less  −", () -> changeCapacity(id, -1)));
            if (solution && patientBefore >= 0)
                menu.add(item("Deliver patient " + patientBefore + " here", () -> deliver(patientBefore, id)));
            menu.addSeparator();
            menu.add(item("Remove hospital  Del", () -> deleteParticipant(id)));
        } else {
            Route r = solution ? facts.solution.route(id) : null;
            menu.add(header("Ambulance " + id + (r == null ? "" : " · " + r.tripCount() + " trips")));
            if (solution) {
                menu.add(enabled(item("Clear its route", () -> clearRoute(id)), !r.isEmpty()));
                menu.add(enabled(item("Reverse its trips", () -> reverseRoute(id)), r.tripCount() > 1));
            }
            menu.addSeparator();
            menu.add(item("Remove ambulance  Del", () -> deleteParticipant(id)));
        }
        menu.show(invoker, at.x, at.y);
    }

    private JMenu hospitalMenu(int patient) {
        PECTOProblem p = facts.problem;
        JMenu menu = new JMenu("Deliver to hospital");
        int current = facts.carrierOf(patient) < 0 ? -1 : facts.solution.route(facts.carrierOf(patient)).hospital(facts.tripOf(patient));
        IntStream.range(0, p.hospitalCount()).map(p::hospitalId).boxed()
                .sorted(Comparator.comparingDouble(h -> p.distance(patient, h)))
                .limit(10)
                .forEach(h -> {
                    int room = p.room(facts.solution, h);
                    JMenuItem item = item("%d  ·  %.1f away  ·  %d free bed%s%s".formatted(h, p.distance(patient, h), room,
                            room == 1 ? "" : "s", h == current ? "  (current)" : ""), () -> deliver(patient, h));
                    item.setEnabled(h != current);
                    menu.add(item);
                });
        return menu;
    }

    private JMenu ambulanceMenu(int patient) {
        PECTOProblem p = facts.problem;
        JMenu menu = new JMenu("Move trip to ambulance");
        int carrier = facts.carrierOf(patient);
        if (route >= 0 && route != carrier)
            menu.add(item("%d (highlighted route)".formatted(route), () -> moveTripTo(patient, route)));
        IntStream.range(0, p.ambulanceCount()).boxed()
                .sorted(Comparator.comparingDouble(a -> p.distance(facts.solution.route(a).end(), patient)))
                .filter(a -> a != carrier)
                .limit(8)
                .forEach(a -> menu.add(item("%d  ·  route ends %.1f away  ·  %d trips".formatted(a,
                        p.distance(facts.solution.route(a).end(), patient), facts.solution.route(a).tripCount()), () -> moveTripTo(patient, a))));
        return menu;
    }

    void askCapacity(int hospital) {
        Object answer = JOptionPane.showInputDialog(host.window(), "Beds of hospital " + hospital + ":", "Capacity",
                JOptionPane.QUESTION_MESSAGE, null, null, facts.problem.capacity(hospital));
        if (answer == null)
            return;
        try {
            setCapacity(hospital, Integer.parseInt(answer.toString().strip()));
        } catch (NumberFormatException e) {
            host.error("'" + answer + "' is not a number of beds");
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean isPatient(int id) {
        return facts != null && id >= 0 && facts.problem.isPatient(id);
    }

    private String describe(int id) {
        return facts.problem.typeOf(id).name().toLowerCase(Locale.ROOT) + " " + id;
    }

    static String name(Urgency u) {
        return u.name().toLowerCase(Locale.ROOT);
    }

    static Icon dot(Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, 9, 9);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return 12;
            }

            @Override
            public int getIconHeight() {
                return 12;
            }
        };
    }

    private static JMenuItem item(String text, Runnable body) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> body.run());
        return item;
    }

    private static JMenuItem enabled(JMenuItem item, boolean enabled) {
        item.setEnabled(enabled);
        return item;
    }

    private static JMenuItem header(String text) {
        JMenuItem item = new JMenuItem(text);
        item.setEnabled(false);
        item.setFont(item.getFont().deriveFont(Font.BOLD));
        return item;
    }

    private static JCheckBox checkBox(String text, boolean selected, String tooltip, Consumer<Boolean> onChange) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setToolTipText(tooltip);
        box.setFocusable(false);
        box.addActionListener(e -> onChange.accept(box.isSelected()));
        return box;
    }

    static Action action(String name, String tooltip, Runnable body) {
        Action a = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        };
        if (tooltip != null)
            a.putValue(Action.SHORT_DESCRIPTION, tooltip);
        return a;
    }
}
