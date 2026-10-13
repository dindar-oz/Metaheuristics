package lab.problemlab.pecto;

import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ViewHost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Urgency;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mouse gestures on the map become the right edits: synthetic mouse events are sent to the canvas of a real editor.
 */
class PECTOCanvasGestureTest {

    // ambulance 0, patients 1-2, hospitals 3-4
    private static final PECTOProblem PROBLEM = PECTOProblem.builder()
            .ambulance(50, 50)
            .patient(100, 100, Urgency.CRITICAL).patient(250, 250, Urgency.STABLE)
            .hospital(300, 50, 5).hospital(50, 300, 5)
            .build();

    private Workspace<PECTOSolution, PECTOProblem> workspace;
    private PECTOEditor editor;
    private PECTOCanvas canvas;

    @BeforeEach
    void setUp() {
        workspace = new Workspace<>(new PECTOWorkbench()::evaluate);
        editor = new PECTOEditor(workspace, new ViewHost() {
            @Override
            public void status(String message) {
            }

            @Override
            public void error(String message) {
                fail(message);
            }

            @Override
            public RNG rng() {
                return new MersenneTwisterRNG(1);
            }

            @Override
            public Window window() {
                return null;
            }
        });
        workspace.addListener(editor::stateChanged);
        canvas = find(editor.component());
        canvas.setSize(800, 700);
        workspace.newInstance(PROBLEM, "instance");
        workspace.setSolution(PROBLEM.solution(new int[][]{{1, 3, 2, 3}}), "solution");
        canvas.fit();
    }

    private static PECTOCanvas find(Component c) {
        if (c instanceof PECTOCanvas canvas)
            return canvas;
        if (c instanceof Container container)
            for (Component child : container.getComponents()) {
                PECTOCanvas found = find(child);
                if (found != null)
                    return found;
            }
        return null;
    }

    private Point at(int id) {
        PECTOProblem p = workspace.problem();
        return new Point((int) Math.round(canvas.screenX(p.x(id))), (int) Math.round(canvas.screenY(p.y(id))));
    }

    private Point atWorld(double x, double y) {
        return new Point((int) Math.round(canvas.screenX(x)), (int) Math.round(canvas.screenY(y)));
    }

    private void send(int type, Point p) {
        int modifiers = type == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        canvas.dispatchEvent(new MouseEvent(canvas, type, System.currentTimeMillis(), modifiers, p.x, p.y, 1, false, MouseEvent.BUTTON1));
    }

    private void drag(Point from, Point to) {
        send(MouseEvent.MOUSE_PRESSED, from);
        send(MouseEvent.MOUSE_DRAGGED, new Point((from.x + to.x) / 2, (from.y + to.y) / 2));
        send(MouseEvent.MOUSE_DRAGGED, to);
        send(MouseEvent.MOUSE_RELEASED, to);
    }

    @Test
    void clickingSelectsAndTheBackgroundDeselects() {
        send(MouseEvent.MOUSE_PRESSED, at(2));
        send(MouseEvent.MOUSE_RELEASED, at(2));
        assertEquals(2, editor.selected());
        assertEquals(0, editor.route());
        Point empty = atWorld(200, 20);
        send(MouseEvent.MOUSE_PRESSED, empty);
        send(MouseEvent.MOUSE_RELEASED, empty);
        assertEquals(-1, editor.selected());
    }

    @Test
    void draggingInTheSelectToolMovesAParticipant() {
        drag(at(4), atWorld(120, 280));
        assertEquals(120, workspace.problem().x(4), 1.5 / canvas.scale());
        assertEquals(280, workspace.problem().y(4), 1.5 / canvas.scale());
        assertEquals(4, editor.selected());
    }

    @Test
    void draggingInTheRouteToolEditsTrips() {
        editor.setTool(PECTOEditor.Tool.ROUTE);
        drag(at(2), at(4));                                  // patient 2 onto hospital 4
        assertArrayEquals(new int[]{1, 3, 2, 4}, workspace.solution().route(0).stops());
        drag(at(2), at(1));                                  // patient 2 before patient 1
        assertArrayEquals(new int[]{2, 4, 1, 3}, workspace.solution().route(0).stops());
        int entries = workspace.history().size();
        drag(at(2), atWorld(200, 20));                       // dropped on nothing: no edit
        assertEquals(entries, workspace.history().size());
    }

    @Test
    void clickingWithAnAddToolAddsAParticipant() {
        editor.setTool(PECTOEditor.Tool.HOSPITAL);
        send(MouseEvent.MOUSE_PRESSED, atWorld(200, 200));
        send(MouseEvent.MOUSE_RELEASED, atWorld(200, 200));
        assertEquals(3, workspace.problem().hospitalCount());
        assertEquals(5, editor.selected());                 // the new hospital
        assertEquals(20, workspace.problem().capacity(5));  // the tool bar's default beds
    }
}
