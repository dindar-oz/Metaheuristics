# Problem Lab: developer guide to supporting a new problem

The Problem Lab (`lab.problemlab`) is a desktop tool for understanding an optimization problem. Users create an
instance, make a solution, edit both by hand, apply mutation operators and watch the cost and the constraints react.
The window is generic, and all it knows about a problem comes from one class you write, a **`ProblemWorkbench`**.

This guide shows how to write that class, from the minimal version (about 40 lines, with a text view) up to an
interactive editor like the PECTO pilot. It assumes your problem already exists in the framework:

- the problem implements `OptimizationProblem<S>`, and its solutions implement `Representation<S>`;
- solution generators implement `InitialSolutionGenerator<S>`, and operators implement `MutationOperator<S>`.

| You want | Read | Effort |
|---|---|---|
| The problem selectable in the lab, with generators, operators, history and undo | §3 | ~40 lines |
| Editable generator and operator parameters | §4 | a few lines per parameter |
| Open and save | §5 | one reader and one writer |
| Constraint violations and measures in the evaluation strip | §6 | one method |
| A picture of the instance that can be edited with the mouse and the keyboard | §7 | the real work |

---

## 1. How the lab is put together

```
 ProblemLab (main, problem chooser) ──▶ ProblemLabCatalog.workbenches()
            │ opens
            ▼
 ProblemLabFrame<S,P>  (generic window, built from the workbench)
   ├─ side panel: one ComponentPanel each for instance generators, solution generators, mutations
   ├─ EvaluationPanel: cost, Δ of the last action, feasibility, violations, measures
   ├─ ProblemView<S,P> ◀── workbench.createView(workspace, host)     ← your interactive view (or TextProblemView)
   └─ HistoryPanel: cost plot + table of every action (click to go back)

 Workspace<S,P>  (model, no Swing)
   history: [LabState, LabState, …, current, (redoable…)]
   change(problem, solution, action[, mergeKey]) ──▶ evaluator (= workbench.evaluate) ──▶ new LabState
   undo / redo / goTo                              ──▶ listeners.stateChanged(previous, current)
```

Every change goes the same way. A side-panel button or your view computes a **new** problem and/or solution, then
calls `workspace.change(...)`. The workspace evaluates the solution, stores an immutable `LabState`, and notifies its
listeners: the frame updates the evaluation strip and the history, then calls your view's `stateChanged`. Undo and
redo just move through the stored states. You never write evaluation, history or undo code yourself.

### The classes you will use

| Class | Package | Role |
|---|---|---|
| `ProblemWorkbench<S,P>` | `lab.problemlab` | **The extension point.** Names, components, formats, evaluation, view. |
| `ProblemLabCatalog` | `lab.problemlab` | The list of workbenches the chooser offers. Register yours here. |
| `ComponentSpec<I,T>` | `lab.problemlab.model` | A named, parameterized factory: instance generator (`I = Void`), solution generator or mutation operator (`I = P`). |
| `ParamSpec`, `ParamValues` | `lab.model` (shared with the Metaheuristic Lab) | A parameter's type, default and range, and the values the user entered. |
| `FileFormat<C,T>` | `lab.problemlab.model` | Reads and writes instances (`C = Void`) or solutions (`C = P`). `FileFormat.text(...)` covers text formats. |
| `Evaluation` | `lab.problemlab.model` | Cost, feasibility, violations in words, named measures. |
| `Workspace<S,P>`, `LabState<S,P>` | `lab.problemlab.model` | The session: current state, history, merge keys, listeners. |
| `ProblemView<S,P>` | `lab.problemlab.ui` | Your interactive view. |
| `ViewHost` | `lab.problemlab.ui` | What the window offers your view: status bar, error dialog, seeded RNG, parent window. |
| `ZoomPanCanvas` | `lab.problemlab.ui` | A base class for problems drawn in the plane: world↔screen transform, wheel zoom, pan, fit. |
| `TextProblemView` | `lab.problemlab.ui` | The fallback view: the instance and the solution as text. |

Type parameters follow the framework's convention: **`S` (solution) first, then `P` (problem)**, as in `ProblemSpec`.

---

## 2. Ground rules

These are the contracts that keep the generic parts correct. Breaking one leads to subtle bugs, such as undo showing
the wrong thing or the cost plot lying.

1. **Problems and solutions are never modified.** A `LabState` is a snapshot. If an edit changed an object in place,
   every earlier state that shares it would change too, and undo would silently break. Always build a new object;
   PECTO does this with `Route.withTrip`, `PECTOSolution.withRoutes` and a fresh `PECTOProblem` for instance edits.
   If your representation is mutable, `copy()` it before changing it. This is the framework's existing operator
   contract ("operators must not modify their inputs"), applied to the lab as well.
2. **All changes go through the `Workspace`.** Never keep a "current solution" of your own that differs from
   `workspace.solution()`. Read the state in `stateChanged` and write it with `workspace.change`.
3. **`equals` on solutions must mean "the same solution".** `MutationStep` counts an operator result as *no change*
   when `next.equals(current)`, and views use `equals` to decide what changed.
4. **Report invalid input with `IllegalArgumentException`** and a message a user can read. When a component or a file
   format throws it, the frame shows the message in a dialog. In your view, catch it and call `host.error(...)`.
5. **Everything runs on the Swing thread.** Generators and operators run synchronously, so keep them fast enough for
   the instance sizes you offer. PECTO's Table I instances, with up to 5,163 patients, take milliseconds.
6. **Keep `evaluate` cheap.** It runs for every state, including each tick of **Play** (every 120 ms).

---

## 3. Step 1: the minimal workbench

This is complete, working support for the TSP. It is also compiled and run as a test,
`src/test/java/lab/problemlab/MinimalWorkbenchExampleTest.java`, so it stays correct.

```java
public final class TSPWorkbench implements ProblemWorkbench<IntegerPermutation, TSPProblem> {

    @Override
    public String name() {
        return "Travelling salesman (TSP)";
    }

    @Override
    public String description() {                       // shown in the problem chooser and the side panel
        return "Visit every city once and return to the start; minimize the length of the tour.";
    }

    @Override
    public List<ComponentSpec<Void, TSPProblem>> instanceGenerators() {
        return List.of(ComponentSpec.generator("Random (unit square)", "Cities placed uniformly in the unit square.",
                List.of(integer("cities", 30, 3, 5000, "number of cities"),
                        integer("seed", 42, 0, Integer.MAX_VALUE, "seed of the coordinates")),
                v -> TSPProblem.randomEuclidean(v.getInt("cities"), v.getInt("seed"))));
    }

    @Override
    public List<ComponentSpec<TSPProblem, InitialSolutionGenerator<IntegerPermutation>>> solutionGenerators() {
        return List.of(ComponentSpec.of("Random tour", "The cities in uniformly random order.",
                problem -> new RandomPermutationSG(problem.getCityCount())));
    }

    @Override
    public List<ComponentSpec<TSPProblem, MutationOperator<IntegerPermutation>>> mutations() {
        return List.of(
                ComponentSpec.of("2-opt", "Reverses a random part of the tour.", problem -> new TwoOpt()),
                ComponentSpec.of("Remove & reinsert", "Moves a random city to a random position.",
                        problem -> new RandomRemoveReinsert<>()));
    }
}
```

Register it:

```java
// lab/problemlab/ProblemLabCatalog.java
public static List<ProblemWorkbench<?, ?>> workbenches() {
    return List.of(new PECTOWorkbench(), new TSPWorkbench());
}
```

Run `./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab -Dexec.args=TSP` (use `mvnw.cmd` on
Windows). Without writing anything else, you get:

- generate an instance with editable parameters, then generate a solution;
- apply operators once or *n* times, as a random walk or a hill climber, or keep applying them with **Play**;
- the cost and feasibility after every action, the change each action made, and a history with a cost plot, with
  undo, redo and jumping to any state;
- one seeded RNG for all randomness (seed in the tool bar), so a sequence of actions can be replayed;
- the instance and the solution as text (`TextProblemView`, which uses `describe(P)` and `S.toString()`).

### `ComponentSpec` factories

| Factory | For | Parameters |
|---|---|---|
| `ComponentSpec.generator(name, description, params, values -> P)` | instance generators | yes |
| `ComponentSpec.of(name, description, problem -> T)` | generators and operators without parameters | none |
| `new ComponentSpec<>(name, description, params, (problem, values) -> T)` | generators and operators with parameters | yes |

A component is created each time the user presses its button, with the current instance and the current parameter
values, so it may use the instance freely (e.g. `new RandomPECTOSG(problem)`). Do not keep state in it between calls.

---

## 4. Step 2: parameters

Parameters are the same `ParamSpec`s the Metaheuristic Lab uses (`import static lab.model.ParamSpec.*`):

```java
integer("cities", 30, 3, 5000, "number of cities")                    // name, default, min, max, tooltip
real("radius", 0.1, 0, 1, "neighborhood radius")
choice("instance", "1", "instance of Table I", "1", "2", "3")         // name, default, tooltip, choices...
```

Read them by name in the factory: `v.getInt("cities")`, `v.getDouble("radius")`, `v.getChoice("instance")`.

- The name is the key and the label, so keep it short and unique within the component.
- The table checks types and ranges before your factory runs. For checks that involve several parameters, throw
  `IllegalArgumentException` from the factory.
- Each component keeps its own values while the window is open, so switching between generators does not lose edits.
- Seeds are `integer(..., 0, Integer.MAX_VALUE, ...)`, because there is no long parameter kind.

---

## 5. Step 3: opening and saving

Override `instanceFormat()` and/or `solutionFormat()`. `FileFormat.text` wraps a reader and a writer in a UTF-8 file
format:

```java
@Override
public Optional<FileFormat<Void, PECTOProblem>> instanceFormat() {
    return Optional.of(FileFormat.text("PECTO instance", PECTOFiles.INSTANCE_EXTENSION,
            (nothing, in) -> PECTOFiles.readInstance(in),
            (nothing, problem, out) -> PECTOFiles.writeInstance(problem, out)));
}

@Override
public Optional<FileFormat<PECTOProblem, PECTOSolution>> solutionFormat() {
    return Optional.of(FileFormat.text("PECTO solution", PECTOFiles.SOLUTION_EXTENSION,
            PECTOFiles::readSolution,                                   // (problem, in) -> solution
            (problem, solution, out) -> PECTOFiles.writeSolution(solution, out)));
}
```

The frame handles the file chooser, the extension filter, appending the extension and confirming overwrites. It
enables the menu items only when a format exists and there is something to save.

Recommendations:

- **Put the reading and writing code in the problem's package**, as `problems.pecto.PECTOFiles` does, not in the lab.
  Experiments and tests can then use it too.
- **Report format errors as `IllegalArgumentException("line N: …")`.** The frame shows the message as it is.
- **A solution file refers to an instance.** Validate that it fits, e.g. PECTO's optional `SOLUTION a p h` header,
  and remember that users may edit the instance after saving a solution. The frame reminds them to save both.
- Write doubles with `Double.toString` so that a saved instance reads back exactly.

---

## 6. Step 4: evaluation, violations and measures

The default `evaluate` shows only `problem.cost(s)` and `problem.isFeasible(s)`. Override it to say *why* a solution
is infeasible and to add measures that help users understand the solution:

```java
@Override
public Evaluation evaluate(PECTOProblem problem, PECTOSolution solution) {
    PECTOFacts facts = new PECTOFacts(problem, solution);            // compute shared facts once
    Evaluation.Builder e = Evaluation.builder(facts.cost, problem.isFeasible(solution));
    if (facts.missing > 0)
        e.violation(facts.missing + " patient(s) not transported (Eq. 12)");
    if (facts.overfull > 0)
        e.violation(facts.overfull + " hospital(s) over capacity (Eq. 13): …");
    return e.metric("trips", solution.tripCount() + " / " + problem.patientCount())
            .metric("ambulances used", facts.busy + " / " + problem.ambulanceCount())
            .build();
}
```

- Violations are shown in red under the cost. Name the constraint, and say where it is violated if that fits on one
  line (e.g. the first few IDs).
- Measures appear in insertion order as `name value · name value`. Aim for 3 to 6.
- Keep `feasible` consistent with `problem.isFeasible`: the hill-climbing acceptance rules rank feasible solutions
  above infeasible ones.
- `describe(P)` (default `toString()`) is the instance summary in the window title, e.g.
  `"200 ambulances, 2430 patients, 64 hospitals (5184 beds)"`.

---

## 7. Step 5: an interactive view

Override `createView` to return your own `ProblemView`:

```java
@Override
public ProblemView<PECTOSolution, PECTOProblem> createView(Workspace<PECTOSolution, PECTOProblem> workspace, ViewHost host) {
    return new PECTOEditor(workspace, host);
}
```

### 7.1 The `ProblemView` contract

| Method | Called | Your job |
|---|---|---|
| `JComponent component()` | once | the whole view, placed below the evaluation strip |
| `stateChanged(previous, current)` | after every change, undo, redo or jump | redraw from `current`; `current == null` until there is an instance, and `current.solution()` may be null |
| `List<Action> actions()` | once | commands for the window's **Problem** menu |
| `String help()` | from **Help → Mouse and keyboard…** | HTML describing your gestures and keys (a text block works well) |

The frame registers the listener for you, so do **not** add your view to the workspace yourself. Keep `workspace`
and `host` from the constructor: the view reads the state in `stateChanged` and changes it through the workspace.

### 7.2 Recommended structure

The PECTO pilot splits its view into parts with one job each. Copying that layout keeps views testable and
reviewable:

| Part | PECTO class | Swing? | Responsibility |
|---|---|---|---|
| Workbench | `PECTOWorkbench` | no | components, formats, evaluation, `createView` |
| Facts | `PECTOFacts` | no | values derived from one state, computed once and shared by the evaluation, canvas and tables |
| Edit helpers | `PECTOInstanceEdit`, `PECTOTripEdit` | **no** | pure functions from (problem, solution, arguments) to a new problem/solution; **unit-test these** |
| Controller | `PECTOEditor` (the `ProblemView`) | yes | selection, tools, key bindings, context menus; turns gestures into edits through the workspace |
| Canvas | `PECTOCanvas extends ZoomPanCanvas` | yes | drawing, hit testing, recognizing gestures, which it reports to the controller |
| Panels | `SelectionPanel`, `RoutesPanel` | yes | property editor and tables; they report to the controller and never touch the workspace |

The canvas and the panels **report** gestures, such as "participant 12 was dropped on 70", and the controller
**decides** what they mean. Only the controller calls `workspace.change`.

### 7.3 Making edits

Instance and solution edits use the same pattern: compute the new objects with a Swing-free helper, then hand them
to the workspace with a description, which becomes the history entry and the status-bar message.

```java
private void editSolution(String action, UnaryOperator<PECTOSolution> edit) {
    PECTOSolution next = edit.apply(facts.solution);
    if (next == facts.solution) {                // helpers return their input when nothing changes
        host.status("Nothing to change");
        return;                                  // so no empty history entry is recorded
    }
    workspace.change(facts.problem, next, action);
}

// e.g. a drop of a patient on a hospital:
editSolution("Patient 12 delivered to hospital 70", s -> PECTOTripEdit.deliver(s, 12, 70));
```

**Instance edits** build a new problem, so the current solution has to be carried over to it. In PECTO, adding or
removing a participant renumbers IDs. `PECTOInstanceEdit` returns a `Result(problem, solution, newIds, created)`:
the solution is translated to the new IDs, trips of removed participants are dropped, and `newIds` maps old IDs to new
ones so that the selection can follow. Choose a carry-over rule that fits your problem. When the instance changes in a
way that makes the old solution meaningless, set it to `null`.

Problem builders reject invalid instances, e.g. PECTO with fewer beds than patients. Let the
`IllegalArgumentException` propagate out of the helper, and show it with `host.error("Cannot do this: " + message)`.
The workspace stays unchanged.

**Continuous gestures** (dragging a spinner, holding an arrow key, **Play**) would flood the history. Give them a
*merge key*: a change whose key equals that of the previous change replaces the newest entry instead of adding one.

```java
workspace.change(problem, solution, "Moved patient 12", List.of("move", 12));   // equal keys merge
```

Any value with `equals` works as a key; `List.of(kind, id)` is convenient. Undo, or a change without a key, ends the
merging.

### 7.4 Keeping the selection across states

Your selection (e.g. a participant ID) refers to one instance. `stateChanged` has to handle two cases:

- **Your own edit.** You know where the selection went. PECTO sets `ownEdit = true` and `nextSelected = result.newId(selected)`
  (or `result.created()`) just before calling `workspace.change`, and applies them in `stateChanged`.
- **Any other change** (new instance, file, undo, redo, history click). Keep the selection only if it still makes
  sense: the ID exists and has the same type. Otherwise clear it.

```java
if (ownEdit) {
    selected = nextSelected;
} else if (!current.sameInstance(previous)) {
    if (selected >= problem.participantCount() || selected >= 0 && problem.typeOf(selected) != selectedType)
        selected = -1;
}
ownEdit = false;
```

### 7.5 Showing what changed

`stateChanged` receives the state shown before, so the view can highlight the effect of the last action, whether it
was an edit, a mutation or an undo. Compare only when `current.sameInstance(previous)` and both have solutions; IDs
mean nothing across instances. Since unchanged parts are shared between immutable solutions, a reference comparison
finds candidates quickly and `equals` confirms them:

```java
Route old = previous.solution().route(a), now = current.solution().route(a);
if (old != now && !old.equals(now)) { /* route a changed: draw old dashed, new thick; show its Δ cost */ }
```

### 7.6 Drawing in the plane with `ZoomPanCanvas`

Extend `ZoomPanCanvas` and implement `worldBounds()`. You get world coordinates with y pointing up, one scale for both
axes (so distances look true), wheel zoom around the mouse, middle-drag pan, and a view that stays fitted until the
user zooms or pans.

```java
final class MyCanvas extends ZoomPanCanvas {
    @Override
    protected Rectangle2D worldBounds() {                  // what "fit" shows; keep it stable while editing
        return new Rectangle2D.Double(0, 0, 350, 350);
    }

    @Override
    protected int bottomReserve() {                       // pixels kept free below, e.g. for a legend
        return 26;
    }

    void show(MyFacts facts) {
        this.facts = facts;
        boundsChanged();                                   // refits only if the user has not zoomed or panned
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);                           // required: it also performs the pending fit
        double x = screenX(worldX), y = screenY(worldY);   // world -> screen
        Point2D w = toWorld(mouseEvent.getPoint());        // screen -> world, e.g. to add or move something
    }
}
```

Other helpers: `fit()`, `zoom(factor, point)`, `zoomCenter(factor)`, `panBy(dx, dy)` (e.g. for left-dragging the
background, as PECTO does), `scale()` and `drawWorldRect(g, rect)`.

- Draw markers in **pixels**, not world units, so that they stay readable at any zoom.
- If the bounds follow the data (e.g. a bounding box), moving one point re-fits the view and makes it jump. PECTO
  uses the fixed 350 × 350 area whenever all participants lie inside it.
- For problems without coordinates, such as distance-matrix TSP, `util.MultidimensionalScaling` gives a planar
  layout (as `TSPVisualizer` uses it).

### 7.7 Mouse and keyboard conventions

These conventions keep views consistent with each other and avoid known Swing pitfalls:

- **Tools as an enum** (PECTO: `SELECT`, `ROUTE`, `AMBULANCE`, `PATIENT`, `HOSPITAL`), each with a label, a key and a
  one-line hint. Show the hint on the canvas and in the status bar.
- **Single-key shortcuts go in the canvas input map with `WHEN_FOCUSED`**:
  `canvas.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name)`. Do **not** give menu actions single-key
  accelerators. Menu accelerators act window-wide, so typing a "p" into a parameter cell would also switch tools.
  Ctrl-combinations are reserved for the frame (Ctrl+Z / Y / O / S / W).
- Call `requestFocusInWindow()` on mouse press so the keys work after a click, and draw a subtle border while the
  canvas has the focus.
- **Context menus:** check `isPopupTrigger()` on *both* press and release, because platforms differ. Build the menu
  in the controller for the participant under the mouse, and add items for the empty background too ("Add … here").
- **Drags:** start a drag only after a few pixels (`DRAG_THRESHOLD`), so that clicks are not tiny moves. While
  dragging, draw feedback (a ghost, a rubber band, a highlighted drop target, a caption saying what the drop will
  do), and change the workspace only on release.
- **Tooltips:** override `getToolTipText(MouseEvent)` and give what a user needs to reason about the item, e.g. a
  trip's cost broken down into weight × distances.

### 7.8 Using the `ViewHost`

| Method | Use for |
|---|---|
| `host.status(text)` | feedback that needs no attention ("Nothing to change", the current tool's hint) |
| `host.error(text)` | a refused edit, with the reason |
| `host.rng()` | randomness in an edit, so that seeded sessions replay exactly; never `new Random()` |
| `host.window()` | the parent of your dialogs (`JOptionPane.showInputDialog(host.window(), …)`) |

---

## 8. Testing

Mirror the PECTO tests (`src/test/java/lab/problemlab/…`):

| Test | Example | Needs a window? |
|---|---|---|
| Every component works with its default parameters, and `evaluate` agrees with the problem | `PECTOWorkbenchTest.everyComponentWorksWithItsDefaults` | no |
| Files round-trip, and errors name the line | `PECTOWorkbenchTest.filesRoundTrip`, `problems/pecto/PECTOFilesTest` | no |
| Edit helpers (ID remapping, carry-over, no-op returns the same object) | `PECTOEditsTest` | no |
| The controller turns calls into undoable edits | `PECTOWorkbenchTest.editorGesturesBecomeUndoableEdits` | no (a `Workspace` and a fake `ViewHost`) |
| Mouse gestures | `PECTOCanvasGestureTest` | no |
| Generic behavior of a minimal workbench | `MinimalWorkbenchExampleTest` | no |

Gesture tests send synthetic events to a real canvas. Size it, fit it, and convert world coordinates with
`screenX`/`screenY`:

```java
canvas.setSize(800, 700);
workspace.newInstance(problem, "instance");
canvas.fit();
Point from = new Point((int) Math.round(canvas.screenX(x0)), (int) Math.round(canvas.screenY(y0)));
canvas.dispatchEvent(new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(),
        InputEvent.BUTTON1_DOWN_MASK, from.x, from.y, 1, false, MouseEvent.BUTTON1));
// … MOUSE_DRAGGED in two steps (past the drag threshold), then MOUSE_RELEASED (modifiers 0) …
assertArrayEquals(expectedStops, workspace.solution().route(0).stops());
```

> **Build gotcha.** VS Code's Java extension compiles into `target/classes` with its own settings. If they are out of
> date (e.g. Java 8 compliance on this Java 21 project), it writes classes that fail at run time with *"Unresolved
> compilation problem"*, and Maven's incremental build keeps them. Use `./mvnw clean test` for trustworthy results, and
> run *Java: Clean Java Language Server Workspace* after changing the pom.

---

## 9. Checklist for a new problem

- [ ] `XWorkbench implements ProblemWorkbench<S, P>` with a name, a description and at least one instance generator
      (or an instance format)
- [ ] Solution generators, including an **empty** or trivial one if users should be able to build solutions by hand
- [ ] Mutation operators; each must return a new solution and never modify its input
- [ ] Registered in `ProblemLabCatalog.workbenches()`
- [ ] Optional: `instanceFormat()` / `solutionFormat()`, with the I/O code in the problem's package
- [ ] Optional: `evaluate` with violations and 3–6 measures; `describe` for the window title
- [ ] Optional: a view, split into Swing-free edit helpers, a controller, a canvas and panels; changes only through
      the `Workspace`; selection kept across instance edits; merge keys for continuous gestures; `help()` filled in
- [ ] Tests: component defaults, edit helpers, file round trip, controller, gestures
- [ ] `./mvnw clean test` passes; the problem works in the running lab
      (`-Dexec.mainClass=lab.problemlab.ProblemLab -Dexec.args=<part of its name>`)
- [ ] README: a line under *Problem Lab (GUI)*

---

## 10. File map

```
src/main/java/lab/problemlab/
  ProblemLab.java              main + problem chooser (also opened from the Metaheuristic Lab tool bar)
  ProblemLabCatalog.java       the offered workbenches                                   ← register here
  ProblemWorkbench.java        the extension point                                       ← implement this
  model/  ComponentSpec, Evaluation, FileFormat, LabState, MutationStep, Workspace       (no Swing)
  ui/     ProblemLabFrame, ProblemView, ViewHost, TextProblemView, ZoomPanCanvas,
          ComponentPanel, EvaluationPanel, HistoryPanel
  pecto/  PECTOWorkbench, PECTOEditor (controller), PECTOCanvas, SelectionPanel, RoutesPanel,
          PECTOFacts, PECTOInstanceEdit, PECTOTripEdit                                   (the reference example)
  doc/    Problem-Lab-Developer-Guide.md (this file)
src/main/java/problems/pecto/
  PECTOFiles.java              text formats for instances and solutions
  operators/GreedyPECTOSG.java greedy generator, complete(), repair()
  operators/SwapTrips.java     the swap mutation
```
