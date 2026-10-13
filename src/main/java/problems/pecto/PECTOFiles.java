package problems.pecto;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads and writes PECTO instances and solutions as plain text. Everything after a '#' is a comment, blank lines are
 * ignored and tokens are separated by whitespace.
 * <p>
 * An instance has three sections, in any order, each a header with the number of participants of its type followed
 * by one line per participant; IDs are assigned by type in the order of the lines (Eq. 4):
 * <pre>
 * AMBULANCES 2
 * 10.5 20.0                    # x y
 * 300 40
 * PATIENTS 3
 * 50 60 CRITICAL               # x y urgency (STABLE, LIGHT, MODERATE, HEAVY or CRITICAL)
 * ...
 * HOSPITALS 1
 * 175 175 81                   # x y capacity
 * </pre>
 * A solution is the paper's encoding ({@link PECTOSolution#toSequence()}), written one route per line, optionally
 * preceded by {@code SOLUTION ambulances patients hospitals} so that it is only read for an instance of that size:
 * <pre>
 * SOLUTION 2 3 1
 * 0 2 5 4 5                    # ambulance 0 takes patient 2 to hospital 5, then patient 4 to hospital 5
 * 1 3 5
 * </pre>
 * Coordinates are written with full precision, so a written instance reads back exactly.
 */
public final class PECTOFiles {

    public static final String INSTANCE_EXTENSION = "pecto";
    public static final String SOLUTION_EXTENSION = "pectosol";

    private static final String INSTANCE_HEADER = """
            # PECTO instance: %d ambulances, %d patients, %d hospitals
            # AMBULANCES: x y | PATIENTS: x y urgency (STABLE, LIGHT, MODERATE, HEAVY, CRITICAL) | HOSPITALS: x y capacity
            """;
    private static final String SOLUTION_HEADER = """
            # PECTO solution: cost %s, %s
            # one route per line: the ambulance ID, then the patient and hospital IDs of its trips
            SOLUTION %d %d %d
            """;

    private PECTOFiles() {
    }

    // ---- instances

    public static void writeInstance(PECTOProblem problem, Writer out) throws IOException {
        out.write(INSTANCE_HEADER.formatted(problem.ambulanceCount(), problem.patientCount(), problem.hospitalCount()));
        out.write("AMBULANCES " + problem.ambulanceCount() + "\n");
        for (int k = 0; k < problem.ambulanceCount(); k++)
            out.write(point(problem, problem.ambulanceId(k)) + "\n");
        out.write("PATIENTS " + problem.patientCount() + "\n");
        for (int k = 0; k < problem.patientCount(); k++) {
            int id = problem.patientId(k);
            out.write(point(problem, id) + " " + problem.urgency(id) + "\n");
        }
        out.write("HOSPITALS " + problem.hospitalCount() + "\n");
        for (int k = 0; k < problem.hospitalCount(); k++) {
            int id = problem.hospitalId(k);
            out.write(point(problem, id) + " " + problem.capacity(id) + "\n");
        }
        out.flush();
    }

    // Throws IllegalArgumentException, with the line number, if the text is not a valid instance
    public static PECTOProblem readInstance(Reader in) throws IOException {
        List<Line> lines = lines(in);
        PECTOProblem.Builder builder = PECTOProblem.builder();
        Map<ParticipantType, Boolean> seen = new EnumMap<>(ParticipantType.class);
        int next = 0;
        while (next < lines.size()) {
            Line header = lines.get(next++);
            ParticipantType type = switch (header.tokens()[0].toUpperCase(Locale.ROOT)) {
                case "AMBULANCES" -> ParticipantType.AMBULANCE;
                case "PATIENTS" -> ParticipantType.PATIENT;
                case "HOSPITALS" -> ParticipantType.HOSPITAL;
                default -> throw header.error("expected AMBULANCES, PATIENTS or HOSPITALS followed by a count");
            };
            header.expect(2);
            if (seen.put(type, true) != null)
                throw header.error("the " + section(type) + " section appears twice");
            int count = header.integer(1);
            if (count < 0 || next + count > lines.size())
                throw header.error(section(type) + " announces " + count + " lines, " + (lines.size() - next) + " follow");
            for (int k = 0; k < count; k++) {
                Line line = lines.get(next++);
                line.expect(type == ParticipantType.AMBULANCE ? 2 : 3);
                double x = line.real(0), y = line.real(1);
                try {
                    switch (type) {
                        case AMBULANCE -> builder.ambulance(x, y);
                        case PATIENT -> builder.patient(x, y, urgency(line));
                        case HOSPITAL -> builder.hospital(x, y, line.integer(2));
                    }
                } catch (IllegalArgumentException e) {
                    throw line.error(e.getMessage());
                }
            }
        }
        for (ParticipantType type : ParticipantType.values())
            if (!seen.containsKey(type))
                throw new IllegalArgumentException("the " + section(type) + " section is missing");
        return builder.build();
    }

    public static void writeInstance(PECTOProblem problem, Path file) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeInstance(problem, out);
        }
    }

    public static PECTOProblem readInstance(Path file) throws IOException {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return readInstance(in);
        }
    }

    // ---- solutions

    public static void writeSolution(PECTOSolution solution, Writer out) throws IOException {
        PECTOProblem problem = solution.problem();
        out.write(SOLUTION_HEADER.formatted(problem.cost(solution), problem.isFeasible(solution) ? "feasible" : "infeasible",
                problem.ambulanceCount(), problem.patientCount(), problem.hospitalCount()));
        for (Route route : solution.routes()) {
            StringBuilder line = new StringBuilder().append(route.ambulance());
            for (int stop : route.stopArray())
                line.append(' ').append(stop);
            out.write(line.append('\n').toString());
        }
        out.flush();
    }

    /**
     * Reads a solution of the problem: an optional SOLUTION header, then the paper's encoding (all routes, each
     * starting with its ambulance ID; line breaks do not matter). Throws IllegalArgumentException if the text is not
     * a solution of this problem.
     */
    public static PECTOSolution readSolution(PECTOProblem problem, Reader in) throws IOException {
        List<Line> lines = lines(in);
        List<Integer> sequence = new ArrayList<>();
        for (int k = 0; k < lines.size(); k++) {
            Line line = lines.get(k);
            if (k == 0 && line.tokens()[0].equalsIgnoreCase("SOLUTION")) {
                line.expect(4);
                int a = line.integer(1), p = line.integer(2), h = line.integer(3);
                if (a != problem.ambulanceCount() || p != problem.patientCount() || h != problem.hospitalCount())
                    throw line.error("the solution is for %d ambulances, %d patients and %d hospitals, the instance has %d, %d and %d"
                            .formatted(a, p, h, problem.ambulanceCount(), problem.patientCount(), problem.hospitalCount()));
                continue;
            }
            for (int t = 0; t < line.tokens().length; t++)
                sequence.add(line.integer(t));
        }
        return problem.decode(sequence.stream().mapToInt(Integer::intValue).toArray());
    }

    public static void writeSolution(PECTOSolution solution, Path file) throws IOException {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writeSolution(solution, out);
        }
    }

    public static PECTOSolution readSolution(PECTOProblem problem, Path file) throws IOException {
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return readSolution(problem, in);
        }
    }

    // ---- helpers

    private static String point(PECTOProblem problem, int id) {
        return problem.x(id) + " " + problem.y(id);
    }

    private static String section(ParticipantType type) {
        return type.name() + "S";
    }

    private static Urgency urgency(Line line) {
        try {
            return Urgency.valueOf(line.tokens()[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw line.error("unknown urgency '" + line.tokens()[2] + "'");
        }
    }

    // the non-blank lines without comments, split into tokens
    private static List<Line> lines(Reader in) throws IOException {
        BufferedReader reader = in instanceof BufferedReader b ? b : new BufferedReader(in);
        List<Line> lines = new ArrayList<>();
        String text;
        int number = 0;
        while ((text = reader.readLine()) != null) {
            number++;
            int comment = text.indexOf('#');
            String content = (comment < 0 ? text : text.substring(0, comment)).strip();
            if (!content.isEmpty())
                lines.add(new Line(number, content.split("\\s+")));
        }
        return lines;
    }

    private record Line(int number, String[] tokens) {

        void expect(int count) {
            if (tokens.length != count)
                throw error("expected " + count + " values, got " + tokens.length);
        }

        int integer(int k) {
            try {
                return Integer.parseInt(tokens[k]);
            } catch (NumberFormatException e) {
                throw error("'" + tokens[k] + "' is not an integer");
            }
        }

        double real(int k) {
            try {
                return Double.parseDouble(tokens[k]);
            } catch (NumberFormatException e) {
                throw error("'" + tokens[k] + "' is not a number");
            }
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException("line " + number + ": " + message);
        }
    }
}
