package lab.problemlab.pecto;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.ParticipantType;
import problems.pecto.Route;
import problems.pecto.Urgency;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * Edits of a PECTO instance. Problems are immutable and IDs are assigned by type (ambulances, then patients, then
 * hospitals), so an edit builds a new problem, in which adding or removing a participant shifts the IDs after it.
 * The solution is carried over: every trip keeps its patient and hospital under their new IDs, the trips of removed
 * participants are dropped (their patients are then not transported) and new ambulances start with empty routes.
 * <p>
 * Edits throw IllegalArgumentException if the result would not be a valid problem, e.g. without a hospital or with
 * fewer beds than patients.
 */
final class PECTOInstanceEdit {

    /**
     * The edited problem and the solution carried over to it (null if there was none).
     *
     * @param newIds  the new ID of every old participant, -1 if it was removed
     * @param created the ID of the participant the edit added, -1 if none
     */
    record Result(PECTOProblem problem, PECTOSolution solution, int[] newIds, int created) {

        // the new ID of an old participant, -1 if it was removed or did not exist
        int newId(int oldId) {
            return oldId >= 0 && oldId < newIds.length ? newIds[oldId] : -1;
        }
    }

    // a participant being edited; oldId is -1 for one the edit adds
    private static final class Participant {
        final int oldId;
        final ParticipantType type;
        double x, y;
        Urgency urgency;
        int capacity;

        Participant(int oldId, ParticipantType type, double x, double y, Urgency urgency, int capacity) {
            this.oldId = oldId;
            this.type = type;
            this.x = x;
            this.y = y;
            this.urgency = urgency;
            this.capacity = capacity;
        }
    }

    private PECTOInstanceEdit() {
    }

    static Result move(PECTOProblem problem, PECTOSolution solution, int id, double x, double y) {
        return edit(problem, solution, id, p -> {
            p.x = x;
            p.y = y;
        });
    }

    static Result setUrgency(PECTOProblem problem, PECTOSolution solution, int patient, Urgency urgency) {
        require(problem, patient, ParticipantType.PATIENT);
        return edit(problem, solution, patient, p -> p.urgency = urgency);
    }

    static Result setCapacity(PECTOProblem problem, PECTOSolution solution, int hospital, int capacity) {
        require(problem, hospital, ParticipantType.HOSPITAL);
        if (capacity < 0)
            throw new IllegalArgumentException("a capacity cannot be negative");
        return edit(problem, solution, hospital, p -> p.capacity = capacity);
    }

    static Result remove(PECTOProblem problem, PECTOSolution solution, int id) {
        List<Participant> participants = participants(problem);
        participants.remove(checked(problem, id));
        return build(problem, solution, participants, null);
    }

    // adds a participant of the type; urgency is used for a patient, capacity for a hospital
    static Result add(PECTOProblem problem, PECTOSolution solution, ParticipantType type, double x, double y,
                      Urgency urgency, int capacity) {
        List<Participant> participants = participants(problem);
        Participant added = new Participant(-1, type, x, y, urgency, capacity);
        participants.add(added);
        return build(problem, solution, participants, added);
    }

    // ---- helpers

    private static Result edit(PECTOProblem problem, PECTOSolution solution, int id, Consumer<Participant> change) {
        List<Participant> participants = participants(problem);
        change.accept(participants.get(checked(problem, id)));
        return build(problem, solution, participants, null);
    }

    // all participants in ID order
    private static List<Participant> participants(PECTOProblem problem) {
        List<Participant> list = new ArrayList<>(problem.participantCount() + 1);
        for (int id = 0; id < problem.participantCount(); id++) {
            ParticipantType type = problem.typeOf(id);
            list.add(new Participant(id, type, problem.x(id), problem.y(id),
                    type == ParticipantType.PATIENT ? problem.urgency(id) : null,
                    type == ParticipantType.HOSPITAL ? problem.capacity(id) : 0));
        }
        return list;
    }

    private static Result build(PECTOProblem old, PECTOSolution solution, List<Participant> participants, Participant created) {
        PECTOProblem.Builder builder = PECTOProblem.builder();
        int[] newIds = new int[old.participantCount()];
        Arrays.fill(newIds, -1);
        int next = 0, createdId = -1;
        for (ParticipantType type : ParticipantType.values())       // IDs by type, in the order of the list
            for (Participant p : participants)
                if (p.type == type) {
                    switch (type) {
                        case AMBULANCE -> builder.ambulance(p.x, p.y);
                        case PATIENT -> builder.patient(p.x, p.y, p.urgency);
                        case HOSPITAL -> builder.hospital(p.x, p.y, p.capacity);
                    }
                    if (p.oldId >= 0)
                        newIds[p.oldId] = next;
                    if (p == created)
                        createdId = next;
                    next++;
                }
        PECTOProblem problem = builder.build();
        return new Result(problem, solution == null ? null : carryOver(solution, problem, newIds), newIds, createdId);
    }

    private static PECTOSolution carryOver(PECTOSolution solution, PECTOProblem problem, int[] newIds) {
        int[][] stops = new int[problem.ambulanceCount()][0];
        for (Route route : solution.routes()) {
            int ambulance = newIds[route.ambulance()];
            if (ambulance < 0)
                continue;
            int[] kept = new int[2 * route.tripCount()];
            int n = 0;
            for (int t = 0; t < route.tripCount(); t++) {
                int patient = newIds[route.patient(t)], hospital = newIds[route.hospital(t)];
                if (patient >= 0 && hospital >= 0) {
                    kept[n++] = patient;
                    kept[n++] = hospital;
                }
            }
            stops[ambulance] = Arrays.copyOf(kept, n);
        }
        return problem.solution(stops);
    }

    private static int checked(PECTOProblem problem, int id) {
        if (id < 0 || id >= problem.participantCount())
            throw new IllegalArgumentException("no participant has ID " + id);
        return id;
    }

    private static void require(PECTOProblem problem, int id, ParticipantType type) {
        if (problem.typeOf(checked(problem, id)) != type)
            throw new IllegalArgumentException(id + " is not a " + type.name().toLowerCase());
    }
}
