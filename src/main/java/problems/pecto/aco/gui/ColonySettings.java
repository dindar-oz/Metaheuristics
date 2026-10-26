package problems.pecto.aco.gui;

import lab.model.Catalog;
import lab.model.LabProblem;
import lab.model.ParamSpec;
import lab.model.ParamValues;
import problems.pecto.aco.PECTOACO;

import java.util.List;
import java.util.Set;

/**
 * The parameters of a PECTOACO run, as the diagnosis runs it: the parameters of {@link PECTOACO.Builder} except the
 * execution mode and threads (the diagnosis builds the ants one after the other, which gives the same run).
 */
public record ColonySettings(int ants, double alpha, double beta, double rho, double pBest, int candidates,
                             int hospitalCandidates, int shortlist, int localSearchSteps, boolean optimalHospitals,
                             double tauMinRatio, int explorers, double explorerRate, double explorerBeta,
                             double explorerDeposit) {

    static final String CATALOG_PROBLEM = "Casualty transport (PECTO)";
    static final String CATALOG_ALGORITHM = "ACO (MMAS)";
    private static final Set<String> NOT_USED = Set.of("execution", "threads");

    // the defaults of PECTOACO.Builder (a test checks that they stay the same)
    public static ColonySettings defaults() {
        return new ColonySettings(20, 1, 2, 0.1, 0.05, 20, 5, 3, 100, true, 0, 2, 0.05, 0.5, 0.05);
    }

    // from the lab's parameters of PECTOACO (Catalog, "ACO (MMAS)"), by their names
    public static ColonySettings from(ParamValues v) {
        int ants = v.getInt("ants");
        return new ColonySettings(ants, v.getDouble("alpha"), v.getDouble("beta"), v.getDouble("rho"),
                v.getDouble("p best"), v.getInt("candidates"), v.getInt("hospital candidates"), v.getInt("shortlist"),
                v.getInt("local search steps"), v.getChoice("hospitals").equals("optimal"), v.getDouble("tau min ratio"),
                Math.min(v.getInt("explorers"), ants), v.getDouble("explorer rate"), v.getDouble("explorer beta"),
                v.getDouble("explorer deposit"));
    }

    // the lab's parameters of PECTOACO that the diagnosis uses
    public static List<ParamSpec> params() {
        return problem().algorithmParams(CATALOG_ALGORITHM).stream().filter(s -> !NOT_USED.contains(s.name())).toList();
    }

    // the lab's PECTO problem: its instance parameters and its instances
    static LabProblem problem() {
        return Catalog.problems().stream().filter(p -> p.name().equals(CATALOG_PROBLEM)).findFirst()
                .orElseThrow(() -> new IllegalStateException("the catalog has no problem " + CATALOG_PROBLEM));
    }

    // the same algorithm as PECTOACO; also checks the values (IllegalArgumentException)
    public PECTOACO toPECTOACO() {
        return PECTOACO.builder().antCount(ants).alpha(alpha).beta(beta).rho(rho).pBest(pBest).candidates(candidates)
                .hospitalCandidates(hospitalCandidates).shortlist(shortlist).localSearchSteps(localSearchSteps)
                .optimalHospitals(optimalHospitals).tauMinRatio(tauMinRatio).explorers(explorers)
                .explorerRate(explorerRate).explorerBeta(explorerBeta).explorerDeposit(explorerDeposit).build();
    }
}
