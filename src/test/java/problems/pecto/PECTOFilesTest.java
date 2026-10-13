package problems.pecto;

import org.junit.jupiter.api.Test;
import problems.pecto.operators.RandomPECTOSG;
import util.random.MersenneTwisterRNG;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.*;

class PECTOFilesTest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(4, 25, 3, 10, 5);

    private static String write(PECTOProblem problem) throws IOException {
        StringWriter out = new StringWriter();
        PECTOFiles.writeInstance(problem, out);
        return out.toString();
    }

    private static String write(PECTOSolution solution) throws IOException {
        StringWriter out = new StringWriter();
        PECTOFiles.writeSolution(solution, out);
        return out.toString();
    }

    @Test
    void instancesReadBackExactly() throws IOException {
        PECTOProblem read = PECTOFiles.readInstance(new StringReader(write(PROBLEM)));
        assertEquals(PROBLEM.participantCount(), read.participantCount());
        assertEquals(PROBLEM.patientCount(), read.patientCount());
        for (int id = 0; id < PROBLEM.participantCount(); id++) {
            assertEquals(PROBLEM.typeOf(id), read.typeOf(id));
            assertEquals(PROBLEM.x(id), read.x(id));
            assertEquals(PROBLEM.y(id), read.y(id));
            if (PROBLEM.isPatient(id))
                assertEquals(PROBLEM.urgency(id), read.urgency(id));
            if (PROBLEM.isHospital(id))
                assertEquals(PROBLEM.capacity(id), read.capacity(id));
        }
    }

    @Test
    void solutionsReadBackWithTheSameCost() throws IOException {
        PECTOSolution solution = new RandomPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(1));
        PECTOSolution read = PECTOFiles.readSolution(PROBLEM, new StringReader(write(solution)));
        assertEquals(solution, read);
        assertEquals(PROBLEM.cost(solution), PROBLEM.cost(read));
    }

    @Test
    void handWrittenFilesWithCommentsAndAnySectionOrder() throws IOException {
        String text = """
                # a tiny instance
                HOSPITALS 1
                10 10 2          # x y capacity
                patients 2
                1 1 critical
                2 2 STABLE
                AMBULANCES 1
                0 0
                """;
        PECTOProblem problem = PECTOFiles.readInstance(new StringReader(text));
        assertEquals(1, problem.ambulanceCount());
        assertEquals(Urgency.CRITICAL, problem.urgency(1));
        assertEquals(2, problem.capacity(3));
        // the paper's flat encoding, without a header
        PECTOSolution solution = PECTOFiles.readSolution(problem, new StringReader("0 1 3 2 3"));
        assertTrue(problem.isFeasible(solution));
    }

    @Test
    void errorsNameTheLine() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> PECTOFiles.readInstance(new StringReader(
                "AMBULANCES 1\n0 0\nPATIENTS 1\n1 1 DYING\nHOSPITALS 1\n5 5 1\n")));
        assertTrue(e.getMessage().startsWith("line 4"), e.getMessage());
        assertThrows(IllegalArgumentException.class, () -> PECTOFiles.readInstance(new StringReader("AMBULANCES 1\n0 0\n")));
        assertThrows(IllegalArgumentException.class, () -> PECTOFiles.readInstance(new StringReader("AMBULANCES 2\n0 0\n")));
    }

    @Test
    void solutionsOfAnotherSizeAreRejected() throws IOException {
        PECTOProblem other = PECTOInstances.random(4, 26, 3, 10, 5);
        PECTOSolution solution = new RandomPECTOSG(other).generate(other, new MersenneTwisterRNG(2));
        String text = write(solution);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PECTOFiles.readSolution(PROBLEM, new StringReader(text)));
        assertTrue(e.getMessage().contains("26 patients"), e.getMessage());
    }
}
