# 4. The PECTO problem

**Post-Earthquake Casualty Transport Optimization** (Balcı & Öz, *Post-Earthquake Casualty Transport Optimization*).
After an earthquake, ambulances must take every casualty to a hospital. Hospitals have a limited number of free beds.
An ambulance carries **one patient at a time**: it drives to a patient, takes them to a hospital, then drives from that
hospital to its next patient, and so on. The goal is to minimize the total urgency-weighted distance driven.

Code: `src/main/java/problems/pecto` (model), `problems/pecto/operators` (generators, moves, `OptimalHospitals`).
The equation numbers below are those of the paper, as cited in the Javadoc.

## 4.1 The model

### Participants (Eq. 4)

There are *A* ambulances, *P* patients and *H* hospitals, and every participant has a point in the plane. They are
numbered **by type** with one integer ID:

| Type | IDs | Data |
|---|---|---|
| Ambulances | `0 .. A−1` | start position |
| Patients | `A .. A+P−1` | position, urgency level |
| Hospitals | `A+P .. A+P+H−1` | position, capacity (free beds) |

`PECTOProblem` converts between IDs and indices: `patientId(k)`, `hospitalId(k)`, `index(id)`, `isPatient(id)`,
`typeOf(id)`. In code, *ID* always means the global participant ID and *index* the position within its type.
Arrays indexed by patient or hospital use the index.

### Urgency (Eq. 7)

| `Urgency` | weight w |
|---|---|
| `STABLE` | 1.00 |
| `LIGHT` | 0.80 |
| `MODERATE` | 0.60 |
| `HEAVY` | 0.40 |
| `CRITICAL` | 0.20 |

### Routes and cost (Eqs. 8–11)

The route *R_a* of ambulance *a* is a sequence of **trips**. A trip goes to a patient and then delivers that
patient to a hospital, so the stops alternate *patient, hospital, patient, hospital, ...*. The route starts at the
ambulance's position and ends at its last hospital. There is no return leg. An ambulance may have no trips.

Every leg has exactly one patient at one of its ends, and costs its Euclidean length times **that patient's**
urgency weight. A trip from `from` (the ambulance's start, or the previous hospital) to patient *p* and on to
hospital *h* costs

```
tripCost(from, p, h) = w_p · ( D(from, p) + D(p, h) )                      (Eq. 8)
routeCost(R_a)       = Σ over the trips of R_a                              (Eq. 9)
cost(solution)       = Σ over all ambulances a of routeCost(R_a)            (Eqs. 10–11, minimized)
```

### Constraints (Eqs. 12–13)

A solution is **feasible** when

- every patient is transported **exactly once** (Eq. 12), and
- no hospital receives more patients than its capacity (Eq. 13).

`PECTOProblem.cost` does **not** check feasibility. `PECTOProblem.isFeasible` does. Every generator and operator in
this package keeps solutions feasible, so the algorithms never see infeasible ones. An instance whose total capacity
is below the number of patients is rejected when it is built.

### A worked example

Two ambulances, three patients, two hospitals (IDs 0–1, 2–4, 5–6):

| ID | Type | Position | Urgency / capacity |
|---|---|---|---|
| 0 | ambulance | (0, 0) | |
| 1 | ambulance | (10, 0) | |
| 2 | patient | (0, 3) | CRITICAL, w = 0.2 |
| 3 | patient | (4, 0) | STABLE, w = 1.0 |
| 4 | patient | (10, 4) | MODERATE, w = 0.6 |
| 5 | hospital | (4, 3) | 2 beds |
| 6 | hospital | (10, 8) | 2 beds |

The solution "ambulance 0 takes patient 2 to hospital 5, then patient 3 to hospital 5; ambulance 1 takes patient 4 to
hospital 6" costs

```
route 0: 0.2·(D(0,2) + D(2,5)) + 1.0·(D(5,3) + D(3,5)) = 0.2·(3 + 4) + 1.0·(3 + 3) = 1.4 + 6.0 = 7.4
route 1: 0.6·(D(1,4) + D(4,6))                         = 0.6·(4 + 4)                = 4.8
total                                                                               = 12.2
```

It is feasible: hospital 5 receives 2 patients (capacity 2), hospital 6 receives 1. In the paper's flat encoding it
is `0 2 5 3 5 1 4 6`. The code gives exactly these numbers:

```java
PECTOProblem p = PECTOProblem.builder()
        .ambulance(0, 0).ambulance(10, 0)
        .patient(0, 3, Urgency.CRITICAL).patient(4, 0, Urgency.STABLE).patient(10, 4, Urgency.MODERATE)
        .hospital(4, 3, 2).hospital(10, 8, 2)
        .build();
PECTOSolution s = p.decode(new int[]{0, 2, 5, 3, 5, 1, 4, 6});
p.cost(s);          // 12.2
p.isFeasible(s);    // true
```

The builder assigns IDs by type, whatever the order of the calls. This example is a good first unit test to write.

## 4.2 Properties of the objective worth knowing

These follow directly from Eq. 8. Many design decisions in the operators and in PECTOACO rely on them.

1. **The objective has no time dimension.** When a patient is picked up does not matter, only how far the
   ambulances drive, weighted by urgency. Urgency enters only as a weight on the two legs that involve the patient.
   An urgent (low-weight) patient's legs are *cheap*, so the objective is less sensitive to how far an urgent
   patient's legs are. Read the paper's motivation for this choice before you design any urgency-aware heuristic.
2. **The hospital of a trip affects only two legs:** the delivery `w_p · D(p, h)` and the next pickup
   `w_next · D(h, next)`, where *next* is the next patient of the same route (none for the last trip). Changing one
   hospital is therefore an O(1) cost change. Choosing all hospitals for **fixed routes** is a *transportation
   problem* (trips → hospitals with capacities) that can be solved exactly. See `OptimalHospitals`, 4.5.
3. **Routes are independent except through hospital capacity.** The cost is a sum of route costs, so a move that
   changes two routes only needs those two re-evaluated.
4. **Every leg includes a patient.** A route's pickup legs start from hospitals, except the first one, which starts
   from the ambulance's position. Where ambulances start matters mainly for their first trip. Ambulances far from all
   patients may be best left idle.
5. **Capacity can be tight.** On the large Table I instances almost every bed is needed (see 4.6), so the hospital
   choice is constrained and the nearest hospital is often full.

## 4.3 Representation in code

### `Route`

An immutable route of one ambulance: `ambulance()`, `tripCount()`, `patient(t)`, `hospital(t)`, `end()`, `stops()`
(a copy). Derived routes: `withoutTrip(t)`, `withTrip(t, patient, hospital)`, `withHospital(t, hospital)`. They
never change the original. A route **caches its cost** the first time the problem computes it (`routeCost`).
Routes are created through `PECTOProblem.route(ambulance, stops...)`, which checks the patient/hospital
alternation.

### `PECTOSolution`

One `Route` per ambulance (`route(a)`, `routes()`), plus the **load** of every hospital (`load(h)`) and the total
trip count. Immutable:

- `withRoutes(changed...)` returns a new solution that **shares every unchanged route**, together with its cached
  cost, and updates the loads incrementally.
- Therefore `problem.cost(neighbor)` only computes the cost of the changed routes and adds up the cached ones.
  Evaluating a neighbor that changes two routes takes about 1 µs on Table I instances, and gives exactly the same
  result as a full evaluation, because route costs are always added in ambulance order.
- `copy()` returns `this`.

Constructors: `problem.solution(int[][] stops)`, `problem.emptySolution()` (no trips, infeasible unless there are no
patients), `problem.decode(int[] sequence)`. `toSequence()` gives the paper's flat encoding: every route as its
ambulance ID followed by its stops.

### Distance between solutions

`PECTOSolution.distanceTo(other)` is the share of patient **choices** that differ. For every patient there are two
choices: who is picked up right before it (its ambulance or a patient) and the hospital it goes to. 0 means the same
routes, 1 means no choice is shared. With diagnostics on, this is the `diversity` metric of PECTOACO's ants (mean
over pairs of ants), and it measures the same thing that the pheromone learns.

### Distances

`distance(i, j)` is computed from the coordinates on demand. A full matrix for the largest instance would take about
235 MB, and computing a square root is faster than reading a large table from memory.

## 4.4 Generators and moves (`problems.pecto.operators`)

| Class | Kind | What it does | Keeps feasibility because |
|---|---|---|---|
| `RandomPECTOSG` | initial solution | Patients in random order, each to a random ambulance, delivered to a random hospital with a free bed | Beds are tracked while assigning |
| `GreedyPECTOSG` | initial solution (deterministic) | Most urgent patients first (lowest weight; ties by ID); each is picked up by the ambulance whose route currently ends nearest and delivered to the nearest hospital with a free bed | Beds are tracked |
| `GreedyPECTOSG.complete(s)` | repair helper | Appends every untransported patient greedily; keeps existing trips | — |
| `GreedyPECTOSG.repair(s)` | repair helper | Drops repeated trips and trips beyond capacity, then `complete` | — |
| `MoveTrip` | mutation | Moves a random trip (patient + its hospital) to a random position of a random route (possibly the same) | Hospitals keep their loads |
| `ChangeHospital` | mutation | Sends the patient of a random trip to another hospital, uniformly among those with a free bed | Only free beds are used |
| `SwapTrips` | mutation | Exchanges two random trips (each with its hospital), within or between routes | Loads unchanged |
| `RouteCrossover` | crossover (EA) | Each ambulance takes its route from a random parent. A patient transported twice keeps only its first copied trip, and trips to full hospitals are dropped. Missing patients go to the nearest hospital with a free bed, via a random ambulance at a random position. | Explicit repair |
| `OptimalHospitals.of(s)` | exact improvement | Best hospitals for the given routes (below) | Solves within capacities |

These are simple, generic operators. **They are not the paper's operators.** EA, SA and local search use
`MoveTrip` + `ChangeHospital` (+ `RouteCrossover` for EA) by default. The `mutation operators` parameter can add
`SwapTrips` or replace `ChangeHospital` with it. PECTOACO's local search uses `MoveTrip` + `SwapTrips`.

## 4.5 `OptimalHospitals`: the exact hospital assignment

Given the routes (which ambulance takes which patients in which order), choose the hospital of every trip to
minimize the cost within capacities. By property 2 above, trip *t* costs

```
c(t, h) = w_p · D(p, h) + w_next · D(h, next)          (next = the next patient on the route, if any)
```

independently of the other trips. This is a **transportation problem**. `OptimalHospitals` solves it exactly by
**negative cycle cancelling**:

- Nodes: the *H* hospitals plus one node `beds` that stands for free beds.
- Edge `h → h'`: move to `h'` the trip at `h` that gains most from the move, with cost `c(t, h') − c(t, h)`.
  Edge `h → beds` (cost 0) if `h` has a free bed, and edge `beds → h` (cost 0) for every hospital.
- A negative cycle is a set of simultaneous trip moves that lowers the cost and respects capacities.
  Bellman–Ford finds it, and the trips along it are moved. When no negative cycle is left, the assignment is optimal.

Only the rows of the hospitals on the cycle are recomputed after a cancellation, and Bellman–Ford stops at the first
cycle it finds, so this is fast enough to run once per PECTOACO iteration on Table I instances. It is tested against
brute force (`OptimalHospitalsTest`).

## 4.6 Instances

### Table I of the paper (`PECTOInstances.paper(k, seed)`)

Generated as described in the paper's Section II.F, from data of the 2020 İzmir earthquake: 200 ambulances,
64 hospitals with 81 free beds each, and `⌊3797 · m_k⌋` patients with multiplier `m_k = 0.64, 0.72, …, 1.36`. All
participants are placed uniformly at random in a 350 × 350 area. **Urgency levels are drawn uniformly**, because the
paper does not say how they are distributed.

| k | patients | beds (64 × 81) | spare beds |
|---|---|---|---|
| 1 | 2 430 | 5 184 | 2 754 |
| 2 | 2 733 | 5 184 | 2 451 |
| 3 | 3 037 | 5 184 | 2 147 |
| 4 | 3 341 | 5 184 | 1 843 |
| 5 | 3 645 | 5 184 | 1 539 |
| 6 | 3 948 | 5 184 | 1 236 |
| 7 | 4 252 | 5 184 | 932 |
| 8 | 4 556 | 5 184 | 628 |
| 9 | 4 860 | 5 184 | 324 |
| 10 | 5 163 | 5 184 | **21** |

The coordinates and urgencies depend on the **instance seed**. All experiments so far use seed 42 (the catalog
default). "T8" in the experiments means `PECTOInstances.paper(8, 42)`.

### Other instances

- `PECTOInstances.random(ambulances, patients, hospitals, capacity, seed)`: any size, generated the same way.
  This is the catalog's *custom* instance.
- `PECTOProblem.builder()`: participants by hand (tests, special scenarios).
- `PECTOFiles`: instances from text files (4.7). Real data, such as actual hospital locations, would be loaded this
  way.

## 4.7 File formats

`PECTOFiles` reads and writes plain text. `#` starts a comment and tokens are separated by whitespace.

Instance (`*.pecto`). Sections in any order; IDs are assigned by type in line order:

```
AMBULANCES 2
10.5 20.0                    # x y
300 40
PATIENTS 3
50 60 CRITICAL               # x y urgency
...
HOSPITALS 1
175 175 81                   # x y capacity
```

Solution (`*.pectosol`). The paper's encoding, one route per line, optionally with a size header:

```
SOLUTION 2 3 1
0 2 5 4 5                    # ambulance 0 takes patient 2 to hospital 5, then patient 4 to hospital 5
1 3 5
```

Coordinates are written with full precision, so an instance reads back exactly.

## 4.8 Algorithms available for PECTO

In the catalog, *Casualty transport (PECTO)* is a `MultiEncodingProblem` with two encodings:

| Encoding | Algorithms | Notes |
|---|---|---|
| **routes** (`PECTOSolution`) | `EA`, `SA`, `Local Search`, **`ACO (MMAS)` = `PECTOACO`** | EA/SA/LS: parameter `initial solutions` = *random* (`RandomPECTOSG`, default) or *greedy* (`GreedyPECTOSG`; deterministic, so an EA population starts as copies of one solution); parameter `mutation operators` = *MoveTrip + ChangeHospital* (default), *MoveTrip + ChangeHospital + SwapTrips* or *MoveTrip + SwapTrips*; EA adds `RouteCrossover`. SA uses the paper's initial temperature 25. |
| **giant tour** (`IntegerPermutation` over `PECTOGraph`) | `ACO` (generic Ant System) | A permutation of ambulances and patients, cut into routes at the ambulances. `PECTOGraph.decode` sends every patient to the free hospital that minimizes its delivery leg plus the next pickup leg, so every tour is feasible. Needs node × node matrices: only practical for a few hundred patients. On Table I: 1 it is still above the greedy cost after 2 000 evaluations. |

`PECTOACO` is the main algorithm for this problem. The next document covers it.

## 4.9 Visualization and tools

- `lab.view.PECTOVisualizer`: map and route table in the Metaheuristic Lab (*View solution*).
- `lab.problemlab.pecto.*`: the interactive editor of the Problem Lab (`PECTOWorkbench`, `PECTOEditor`,
  `PECTOCanvas`, ...). See [Tools 2.3](02-tools.md#23-problem-lab).

## 4.10 Tests

`src/test/java/problems/pecto`: `PECTOProblemTest` (IDs, equations, feasibility, encoding), `PECTOOperatorsTest`
(feasibility and determinism of every operator and generator; route edits leave the original unchanged),
`OptimalHospitalsTest` (against brute force; never worsens; refuses solutions over capacity), `PECTOFilesTest`
(round trips), `PECTOGraphTest`.
