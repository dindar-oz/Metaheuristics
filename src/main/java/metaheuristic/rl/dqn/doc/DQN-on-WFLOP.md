# Deep Q-learning (DQN) as a constructive metaheuristic, applied to wind farm layout optimization

This document explains how `metaheuristic.rl.dqn.DQN` works and how it solves the wind farm layout optimization
problem (WFLOP) through `problems.wflop.rl.ConstructiveWFLOP`. It assumes introductory reinforcement learning (agent,
state, action, reward, policy) but no prior knowledge of DQN: every DQN-specific idea is introduced before it is used.

Contents

1. [The big picture](#1-the-big-picture)
2. [Reinforcement learning background, as far as DQN needs it](#2-reinforcement-learning-background-as-far-as-dqn-needs-it)
3. [WFLOP as a sequential decision problem](#3-wflop-as-a-sequential-decision-problem)
4. [Describing actions by features](#4-describing-actions-by-features)
5. [The reward baseline: learning only the foresight](#5-the-reward-baseline-learning-only-the-foresight)
6. [The neural network](#6-the-neural-network)
7. [One run, step by step](#7-one-run-step-by-step)
8. [How the network learns](#8-how-the-network-learns)
9. [Budget, cost and results](#9-budget-cost-and-results)
10. [Transfer to other farms](#10-transfer-to-other-farms)
11. [Parameters](#11-parameters)
12. [Glossary](#12-glossary)
13. [References and source files](#13-references-and-source-files)

---

## 1. The big picture

Most metaheuristics in this project (SA, local search, EA) are **improvement** methods: they start from complete
layouts and change them. DQN here is a **constructive** method: it builds a layout from an empty farm, placing one
turbine at a time, and the hard part is deciding *where to put the next turbine*.

A simple constructive method is the greedy heuristic (`GreedyConstruction`): always place the turbine where it improves
the objective the most right now. It is fast but short-sighted. A turbine that looks best now may stand right upwind of
the cells where later turbines would go best, and spoil them.

DQN learns to look ahead. It repeats the construction many times (each repetition is an **episode**). While it does,
it trains a small neural network to predict how each placement will affect the *rest* of the construction. Each
construction then uses what was learned so far, plus some deliberate randomness (exploration). The algorithm returns the
best layout built in any episode.

```
            +--------------------------------------------------------------+
            |                        one run of DQN                        |
            |                                                              |
            |   episode 1      episode 2      episode 3          ...       |
            |   empty farm     empty farm     empty farm                   |
            |     | place        | place        | place                  |
            |     | place        | place        | place                  |
            |     |  ...         |  ...         |  ...                   |
            |   layout 1       layout 2       layout 3                     |
            |   (1 eval)       (1 eval)       (1 eval)                     |
            |      \              |              /                         |
            |       '--> experience (replay buffer) --> trains network     |
            |                         network guides the next episodes     |
            |                                                              |
            |   result = best layout of all episodes                       |
            +--------------------------------------------------------------+
```

So DQN is used as a **metaheuristic**, not as a way to obtain a trained policy. What matters is the best solution it
finds within its evaluation budget. Learning is how it focuses the search.

Three design choices shape the implementation. Later sections explain each one:

- **Rewards that add up to the objective.** The rewards of an episode sum to exactly
  −objective(final layout) / constant, so maximizing the total reward is minimizing the objective
  ([Section 3](#3-wflop-as-a-sequential-decision-problem)).
- **Actions described by features.** The network does not see the farm as an image. It scores each candidate cell from
  12 numbers computed with the wake model, the same network for every cell
  ([Section 4](#4-describing-actions-by-features)).
- **Known rewards are not learned.** The immediate effect of a placement can be computed exactly, so the network
  learns only the effect on the future. An untrained network is therefore exactly the greedy heuristic
  ([Section 5](#5-the-reward-baseline-learning-only-the-foresight)).

---

## 2. Reinforcement learning background, as far as DQN needs it

### 2.1 Episodes, return, and why we care about the sum of rewards

At each step $t$ the agent is in a state $s_t$, takes an action $a_t$, receives a reward $r_t$ and moves to state
$s_{t+1}$. An episode ends in a terminal state. The **return** from step $t$ is the sum of the rewards that follow,
optionally discounted by a factor $\gamma \in [0, 1]$:

$$G_t = r_t + \gamma r_{t+1} + \gamma^2 r_{t+2} + \dots$$

Here $\gamma = 1$ (the default), so the return of a whole episode is simply the sum of its rewards. Episodes have at
most 100 steps, so no discounting is needed to keep sums finite. With $\gamma = 1$ the return is *exactly* minus the
scaled objective (Section 3), which is what we want to optimize.

### 2.2 Action values (Q-values)

The **action value** $Q(s, a)$ is the return you can expect if you take action $a$ in state $s$ and act well
afterwards. If you knew $Q$, acting optimally would be trivial: in every state, take the action with the highest $Q$.

The optimal action values satisfy the **Bellman equation**:

$$Q(s, a) = r(s, a) + \gamma \max_{a'} Q(s', a')$$

In words: the value of an action is its immediate reward plus the value of the best action in the state it leads to
($s'$). In the terminal state there is nothing left, and the value there is 0.

### 2.3 Q-learning

Q-learning estimates $Q$ from experience. After observing one step $(s, a, r, s')$, it moves its estimate
$Q(s, a)$ toward the **target**

$$y = r + \gamma \max_{a'} Q(s', a')$$

The target uses the current estimate of $Q$ itself on the right-hand side. This is called **bootstrapping**: estimates
are improved from other estimates. Repeated over many steps, the estimates propagate the final outcome backwards through
the episode.

### 2.4 Why "deep": function approximation

Classic Q-learning keeps one table entry per (state, action) pair. In WFLOP a state is a partial layout. There are
$\binom{100}{39} \approx 10^{28}$ layouts of 39 turbines on 100 cells, so a table is impossible. **Deep** Q-learning
replaces the table with a neural network $Q_\theta(s, a)$, whose parameters $\theta$ are trained by gradient descent so
that $Q_\theta(s, a) \approx y$. The network generalizes: what it learns about one state also applies to similar states
it has never seen.

### 2.5 What goes wrong when you naively combine the two, and the DQN fixes

Training a network on its own bootstrapped targets is unstable. DQN (Mnih et al. 2015) added two now-standard
remedies, and later work added more. This implementation uses all of the following. Section 8 shows each in the code.

| Problem | Remedy | In this implementation |
|---|---|---|
| Consecutive steps are strongly correlated, so training on them in order overfits the current episode. | **Experience replay**: store past transitions and train on random samples from them. | `ReplayBuffer`, 20 000 transitions |
| The target $y$ moves every time the network is updated (it chases its own tail). | **Target network**: compute targets with a frozen copy of the network, refreshed only now and then. | copy refreshed every 500 steps |
| $\max_{a'} Q(s', a')$ over noisy estimates is biased upwards: the max picks whichever action happens to be overestimated. | **Double Q-learning** (van Hasselt et al. 2016): one network picks the best $a'$, the other one values it. | online picks, target values |
| One-step targets propagate the outcome backwards slowly, one step per update. | **n-step returns**: use $n$ real rewards before bootstrapping. | $n = 3$ |
| Large errors produce huge gradients. | **Huber loss** and gradient clipping. | Huber, clipped norm 10 |

### 2.6 Exploration

If the agent always takes the action it currently values highest, it never discovers that another action is better.
It must sometimes **explore**. Two strategies are implemented (`Exploration`):

- **ε-greedy**: with probability ε take a uniformly random action, otherwise the best one.
- **Softmax (Boltzmann)**, the default: choose action $a$ with probability proportional to $\exp(Q(a) / T)$. High
  temperature $T$ gives nearly uniform choices. Low temperature gives nearly greedy choices. Unlike ε-greedy, it
  rarely tries actions that are clearly bad, and it mostly explores among the near-best ones.

Exploration decays over the run: lots of trying early, mostly exploiting later.

---

## 3. WFLOP as a sequential decision problem

### 3.1 The optimization problem

The farm is a grid of cells (10 × 10 cells of 200 m in the paper). A layout is the set of occupied cells, with at most
one turbine per cell (`CellLayout`). Turbines upwind slow the wind for turbines downwind (their **wake**). The power of
a turbine grows with the cube of its wind speed, so wakes cost a lot of power. The objective, to be **minimized**, is

$$\text{obj}(L) = \frac{\text{cost}(n)}{\text{power}(L)}, \qquad \text{cost}(n) = n\left(\tfrac{2}{3} + \tfrac{1}{3} e^{-0.00174\, n^2}\right)$$

where $n$ is the number of turbines and power is the expected power over all wind states (directions and speeds).
The cost per turbine falls as $n$ grows, which pushes toward more turbines. Wakes push toward fewer and better-spread
turbines. Cases 1–2 let the number of turbines vary (1–100); Cases 3–4 fix it (39 by default).

### 3.2 The decision process (MDP)

`ConstructiveWFLOP` turns a `DiscreteWFLOP` into an episodic decision process. `LayoutConstruction` holds one episode.

| RL concept | In WFLOP |
|---|---|
| **State** | The partial layout: the cells occupied so far (plus quantities derived from it, Section 4). |
| **Actions** | Place a turbine in any free cell (actions `0 .. cells-1`). In Cases 1–2 also **STOP** (action `cells`), allowed once at least one turbine is placed. |
| **Transition** | Deterministic: the chosen cell becomes occupied. |
| **Episode end** | STOP is chosen, or the maximum number of turbines is placed (always 39 placements in Cases 3–4). |
| **Reward** | The improvement of the objective, in units of $\text{obj}^*$ (below). |

### 3.3 The rewards telescope to the objective

Let $L_t$ be the layout after $t$ placements, and $\text{obj}^*$ the objective of a single turbine without any wake
loss (`referenceObjective()`). The rewards are

$$r_1 = -\frac{\text{obj}(L_1)}{\text{obj}^*}, \qquad r_t = \frac{\text{obj}(L_{t-1}) - \text{obj}(L_t)}{\text{obj}^*} \;(t > 1), \qquad r_{\text{STOP}} = 0$$

Adding them up, every intermediate objective appears once with + and once with −, and cancels:

$$\sum_t r_t = -\frac{\text{obj}(L_1)}{\text{obj}^*} + \frac{\text{obj}(L_1) - \text{obj}(L_2)}{\text{obj}^*} + \dots = -\frac{\text{obj}(L_{\text{final}})}{\text{obj}^*}$$

This "telescoping" is what makes the RL problem *the same* problem as the optimization problem: maximizing the return
is exactly minimizing the objective, with no approximation or surrogate. `ConstructiveWFLOPTest.returnIsMinusScaledCost`
checks it to 12 digits.

The rewards are also *informative at every step*. A positive reward means the placement improved the cost per power,
and a negative one means it made it worse. The agent does not have to wait until the end of the episode to learn
anything.

**A small worked example** (single turbine, then a second one outside any wake):

- The first turbine is never waked on an empty farm, so $\text{obj}(L_1) = \text{obj}^*$ and $r_1 = -1$ always.
- A second, unwaked turbine doubles the power. The cost goes from cost(1) = 0.99942 to cost(2) = 1.99538, so
  $\text{obj}(L_2) / \text{obj}^* = 1.99538 / (2 \times 0.99942) = 0.99827$ and $r_2 = 1 - 0.99827 = +0.00173$.
  Adding the turbine helped, because cost per turbine fell.
- Had the second turbine stood deep in the first one's wake, power would have grown by much less than double, and $r_2$
  would have been negative.

For reference, a layout of 39 turbines has cost(39)/39 ≈ 0.690. Its return is about $-0.69 / \eta$, where $\eta$ is the
farm efficiency (power relative to 39 unwaked turbines). Every percent of efficiency gained is worth about 0.007 of
return.

### 3.4 Computing rewards is cheap

Each step needs the power of the new partial layout. `WindFarm.power(CellLayout)` evaluates a layout derived by
`withAdded` *incrementally*: only turbines in the wakes of the new cell are recomputed. These step evaluations are
**not counted** in the evaluation budget. Only the final layout of each episode is evaluated through
`SearchContext.evaluate` (Section 9).

---

## 4. Describing actions by features

### 4.1 Why not "one network output per cell"?

The textbook DQN for games takes the state (e.g. the screen pixels) as input and has one output per action. For WFLOP
that would mean a network that sees the whole 10 × 10 grid and outputs 100 values. Such a network

- only works on farms of exactly that size (no transfer to 15 × 15 or 20 × 20),
- has to learn the wake physics from scratch, from the rewards alone,
- must handle a changing set of feasible actions (occupied cells, STOP only sometimes).

This implementation uses the **action-in** design instead. The problem describes every (state, action) pair by a short
vector of features, and *one* small network maps that vector to a score:

$$Q(s, a) \approx \text{baseline}(s, a) + \text{scale}(s) \cdot f_\theta(\phi(s, a))$$

where $\phi(s, a)$ are the 12 features of placing the next turbine in cell $a$. (The baseline and scale are explained in
Section 5.) To choose an action, the network is applied to each feasible action's feature row, and the scores are
compared. The same network scores every cell, so the number of cells does not matter.

The interfaces are `FeaturedConstructiveProblem` (declares `featureCount()`) and `FeaturedConstruction`
(`actionFeatures()`: one row per feasible action).

### 4.2 The 12 features (`LayoutFeature`)

All features are **intensive**: per turbine, per cell, or a ratio. A value therefore means the same thing on a farm of
any size, which is what makes transfer possible (Section 10). They are computed in `LayoutConstruction.computeFeatures`.

| # | Feature | Meaning | Why it helps |
|---|---|---|---|
| 1 | `OWN_EFFICIENCY` | Power the new turbine would produce, relative to an unwaked turbine (1 = no wake loss). | The immediate gain of the placement. |
| 2 | `CAUSED_LOSS` | Power the already placed turbines would lose because of the new turbine's wake, in unwaked-turbine units. | The immediate damage of the placement. |
| 3 | `MARGINAL_POWER` | (own − caused) relative to the mean power of the placed turbines. | Net power added, on the same scale as feature 4. |
| 4 | `MARGINAL_COST` | Cost added by one more turbine, relative to the mean cost of the placed ones. | The objective improves **exactly** when `MARGINAL_POWER` > `MARGINAL_COST`. This helps the STOP decision in Cases 1–2. |
| 5 | `IMPROVEMENT` | The exact reward of the action, times the number of turbines after it. | The immediate reward, rescaled so that it does not shrink as the farm fills (rewards scale like 1/n). |
| 6 | `UPWIND_EXPOSURE` | How strongly the cells that are still **free** could wake this cell (sum of squared deficits, weighted over wind states). | Risk: a turbine here may later be waked by turbines placed upwind of it. |
| 7 | `DOWNWIND_EXPOSURE` | How strongly this cell would wake the cells that are still free. | Damage to the future: a turbine here spoils the free cells downwind of it. |
| 8 | `EDGE_DISTANCE` | Distance to the farm edge, relative to 1 km, at most 1. | Edge cells face free wind from outside the farm in many directions. |
| 9 | `PROGRESS` | Turbines placed / maximum turbines. | How far the episode is. Same for every action in a state. |
| 10 | `DENSITY` | Turbines placed / cells. | How crowded the farm is. Same for every action. |
| 11 | `EFFICIENCY` | Power of the placed turbines / power of as many unwaked ones. | Quality of the layout so far. Same for every action. |
| 12 | `STOP` | 1 for the STOP action, 0 otherwise. | Lets the network treat STOP differently. For STOP, features 1–8 are 0. |

Features 1–5 describe the **present**, the immediate effect of the action. Features 6–8 describe the **future**: they
tell how the action interacts with the turbines that are still to come. They are the main raw material for the
foresight the network has to learn (Section 5). Features 9–11 describe the **state**.

### 4.3 How the features are computed fast

All features come from the farm's `WakeMatrix`, a precomputed sparse table. For every upstream cell $i$, downstream
cell $j$ and wind state $s$ it stores the squared velocity deficit that a turbine in $i$ causes at $j$ (Jensen wake
model). Only about 5% of the $(i, j, s)$ triples are nonzero, because a wake only reaches a narrow cone downstream.

`LayoutConstruction` keeps three running quantities and updates them in `step()` from the matrix entries of the newly
occupied cell only:

- `squaredDeficits[s][j]`: the sum of squared deficits the placed turbines cause at cell $j$ under wind state $s$. The
  Jensen model combines wakes by the root of the sum of squares, so the wind speed ratio at $j$ is
  $u = 1 - \sqrt{\text{sum}}$ (`JensenWakeModel.speedRatio`).
- `upwind[j]`, `downwind[j]`: the exposures of features 6 and 7. They start as the exposures of an empty farm
  (precomputed in `ConstructiveWFLOP`), and a cell's contributions are subtracted once it is occupied, because it is no
  longer free.

From these:

- `OWN_EFFICIENCY` of cell $c$ $= \sum_s w_s \, u_s(c)^3$, with $w_s$ the share of wind state $s$ in an unwaked
  turbine's expected power (`WindFarm.stateWeight`). Power grows with $v^3$, hence the cube.
- `CAUSED_LOSS` of cell $c$ = for every occupied cell $j$ in $c$'s wake, the drop of $w_s u^3$ when $c$'s squared
  deficit is added to $j$'s sum.
- The exact objective after placing in $c$ is then
  $\text{cost}(n+1) / (\text{power} + P_{\text{free}} \cdot (\text{own} - \text{caused}))$. That gives the exact reward
  (`IMPROVEMENT`) without evaluating the farm. `ConstructiveWFLOPTest.powerFeaturesMatchTheFarm` checks that this
  prediction equals the farm's own evaluation.

---

## 5. The reward baseline: learning only the foresight

This is the most important idea specific to this implementation (`rewardBaseline(true)`, the default).

### 5.1 The decomposition

From the Bellman equation with $\gamma = 1$:

$$Q(s, a) = \underbrace{r(s, a)}_{\text{known exactly}} + \underbrace{V(s')}_{\text{the future: must be learned}}$$

The immediate reward of every action is known *before* the action is taken (`FeaturedConstruction.actionRewards()`,
Section 4.3). There is no point in making a network learn something we can compute exactly. So the network learns only
the second term, the value of what follows the action. This document calls it the **foresight**:

$$Q(s, a) = r(s, a) + \text{scale}(s) \cdot f_\theta(\phi(s, a))$$

In the code (`DQN.values`): `values[k] = o.baselines[k] + o.scale * net.forward(features of action k)`.

### 5.2 The scale

Rewards and future changes of the objective shrink roughly like $1 / n$ as the farm fills: adding the 30th turbine
changes cost/power much less than adding the 2nd. A network output that had to represent tiny numbers late in the
episode and large ones early would be hard to train. So `valueScale()` returns $1 / (n + 1)$, where $n + 1$ is the
number of turbines after the placement. The network then predicts the foresight **per turbine**, a quantity of similar
size throughout the episode and on farms of any size. `IMPROVEMENT` (feature 5) is the reward times the same
$n + 1$, for the same reason.

### 5.3 Consequences

- **An untrained network is the greedy heuristic.** The network's output layer starts with all weights 0
  (`new Mlp(..., outputScale = 0)`), so $f_\theta = 0$ and $Q(s, a) = r(s, a)$. Taking the highest $Q$ is taking the
  best immediate reward, exactly what `GreedyConstruction` does. `DQNTest.untrainedNetworkIsTheGreedyHeuristic` checks
  this.
- **DQN is never worse than greedy.** Episode 0 is always greedy (Section 7.3), so its layout, the greedy one, is
  among the candidates for the result.
- **Learning only has to find corrections.** The network must learn when the greedy choice is short-sighted, e.g.
  "this cell gives a slightly better reward now, but its `DOWNWIND_EXPOSURE` is high, so it will spoil good free cells".
  That is a much smaller learning task than learning all of $Q$ from scratch.

With `rewardBaseline(false)` the network learns all of $Q$ (baseline 0, scale 1), as in textbook DQN. The output layer
then starts random, so the first episodes are close to random constructions.

---

## 6. The neural network

`metaheuristic.rl.nn.Mlp` is a small hand-written multi-layer perceptron, with no external ML library:

```
 12 features  -->  16 ReLU  -->  16 ReLU  -->  1 linear output  =  f(features)
```

- **Size.** Hidden layers 16 and 16 by default (`hidden(16, 16)`), i.e. 12·16 + 16·16 + 16·1 weights plus biases:
  about 500 parameters. The features already encode the physics, so the network only has to combine them.
- **Initialization.** He initialization for the ReLU layers, zero biases, and output weights scaled by `outputScale`
  (0 with the reward baseline, see 5.3). While the output weights are 0, the gradient does not reach the hidden layers.
  They start learning once the output weights have moved away from 0.
- **Training.** `forward` stores the activations, and `backward` accumulates the gradient for one input, scaled by an
  output gradient. `Adam` (Kingma & Ba 2015) applies the averaged gradient of a batch, with learning rate 1e-3, after
  clipping the gradient's global norm to 10.
- **Persistence.** `save`/`load` write the layer sizes and the exact weights, so a trained network can be reused on
  another problem (Section 10).

`MlpTest` checks the gradients against finite differences.

---

## 7. One run, step by step

### 7.1 Pseudocode

This is `DQN.search` with bookkeeping removed:

```
online  = new network (output 0)            # or a copy of a given initial network
target  = copy of online
replay  = empty buffer (capacity 20 000)
elite   = transitions of the best episode so far (empty)
steps   = 0

for episode = 0, 1, 2, ... until the budget is spent:
    greedy = (episode % greedyEvery == 0)             # episodes 0, 10, 20, ...
    level  = greedy ? 0 : exploration level of this episode
    construction = empty farm

    while the layout is not complete:
        s = observe: feasible actions, their feature rows, their known rewards, the scale
        Q[k] = reward[k] + scale * online(features[k])      for every feasible action k
        remember the 16 actions with the highest Q as the candidates of s     (Section 8.4)
        k = greedy ? argmax Q : exploration.choose(Q, level)
        r = construction.step(action k)                     # place the turbine, get the exact reward
        steps++
        if steps % trainEvery == 0 and replay has >= warmup transitions:
            one training step on a batch                    (Section 8)
        if steps % targetUpdate == 0:
            target = copy of online

    cost = evaluate(final layout)                           # the one counted evaluation
    store the episode's n-step transitions in replay        (Section 8.1)
    if cost < best cost so far: elite = this episode's transitions

return best layout found        (tracked by SearchContext)
```

Two points are easy to miss:

- **Training happens during the episode.** With `trainEvery = 1`, every placement is followed by a gradient step. A
  39-turbine episode therefore performs 39 training steps. They use transitions from *previous* episodes only, because
  an episode's transitions are stored when it ends: an n-step transition needs the rewards of the steps after it.
- **The network changes while an episode is built.** Later placements of an episode already benefit from what was
  learned during its first placements.

### 7.2 Exploration in practice

The default is softmax with **standardized** values (`Softmax`): action $a$ is chosen with probability proportional to

$$\exp\!\left(\frac{Q(a) - \max_b Q(b)}{T \cdot \sigma}\right)$$

where $\sigma$ is the standard deviation of the Q-values of the current state. Dividing by $\sigma$ makes the
temperature independent of the scale of the values, which changes a lot between early and late steps and between farms.
$T$ decays geometrically from 0.3 to 0.05 over the first 1000 episodes, then stays at 0.05.

Interpretation: at $T = 0.3$, an action one standard deviation below the best is chosen $e^{-1/0.3} \approx 3.6\%$ as
often as the best. At $T = 0.05$ it is chosen $e^{-20} \approx 2 \times 10^{-9}$ as often, so only near-ties are
explored. Exploration therefore concentrates on cells that the current knowledge considers almost as good as the best.
That suits WFLOP, where many cells are clearly bad (deep in existing wakes) and trying them teaches little.

With ε-greedy (`epsilon(start, end, decayEpisodes)`), ε decays linearly, and a random action is uniformly random among
all feasible ones.

The level is reported as the `temperature` (or `epsilon`) metric. In the lab it appears in the *Metrics* tab, together
with the `current cost` of every episode and the mean training `loss`.

### 7.3 Greedy episodes

Every `greedyEvery`-th episode (default 10, starting with episode 0) takes no exploratory actions: it builds the layout
the current network considers best. This has two purposes:

- Episode 0 shows where the run starts from: the greedy heuristic, or the policy of a given initial network.
- Periodic greedy episodes **exploit** what has been learned. Their layouts are often the best of the run once the
  network is good, since exploration adds noise.

---

## 8. How the network learns

### 8.1 Transitions and n-step returns

At the end of an episode, `DQN.transitions` turns the sequence of steps into training examples. With $n = 3$ (default)
and $\gamma = 1$, the transition of step $t$ holds

- the feature row of the action taken at step $t$ and its known reward $r_t$ (the *baseline*) and scale,
- the sum of the real rewards of the next up to 3 steps: $R = r_t + r_{t+1} + r_{t+2}$,
- the state reached after those 3 steps, $s_{t+3}$, or nothing if the episode ended before it,
- the discount $\gamma^3$ (= 1 here) to apply to that state's value.

For a placement 3 steps before the end of the episode, the target needs no bootstrapping at all: it is the real
outcome. For earlier placements it is 3 real rewards plus an estimate. Compared with 1-step targets, information about
how the layout ended travels back through the episode three times faster.

A stored state ("observation") keeps the feature rows of *all* its feasible actions. They are needed later, because its
value must be recomputed with whatever the target network is at training time.

### 8.2 The training target

For a transition whose reached state $s'$ is not terminal:

$$y = R + \gamma^n \cdot Q_{\text{target}}(s', a^*), \qquad a^* = \arg\max_{a' \in \text{candidates}(s')} Q_{\text{online}}(s', a')$$

This is **double Q-learning**: the *online* network chooses the best next action, and the *target* network says how
good it is. Using one network for both tends to overestimate, because the max picks whichever action is accidentally
overvalued. Using two networks decorrelates the choice from the evaluation. If $s'$ is terminal, $y = R$.

Both $Q$s include their baselines and scales: $Q(s', a') = r(s', a') + \text{scale}(s') \cdot f(\phi(s', a'))$.

### 8.3 The loss, in network units

The network outputs the foresight per turbine, not $Q$, so the target is converted to the network's units:

$$\text{error} = f_{\text{online}}(\phi(s, a)) - \frac{y - r(s, a)}{\text{scale}(s)}$$

Here $(y - r) / \text{scale}$ is "everything after this placement, per turbine". The loss is the **Huber loss** of the
error: quadratic for $|\text{error}| \le 1$, linear beyond. Its gradient is the error clipped to [−1, 1], so a single
surprising transition cannot yank the network. The gradients of a batch of 32 transitions are averaged and applied by
Adam (`DQN.train`).

### 8.4 Candidates: a cheap max over 100 actions

The target needs $\max_{a'}$ over all actions of $s'$, up to 100 cells, each one a network forward pass, for each of
the 32 transitions of every batch, at every step. To keep this affordable, each state remembers the 16 actions the
online network valued highest **when the state was visited** (`candidates(16)`). The max is taken over those only. The
idea comes from Wolpertinger (Dulac-Arnold et al. 2015). The network's opinion of a state's best actions changes slowly,
and the actions it ranked far down are very unlikely to have become the best. `candidates(0)` uses all actions, which
is exact but slower.

### 8.5 Replay, elite replay, warm-up and the target network

- **Replay buffer.** The last 20 000 transitions (about 500 episodes of 39 placements). A batch samples uniformly
  from them, which mixes many episodes and many stages of construction in every update.
- **Elite replay.** Each transition of a batch comes, with probability 0.25 (`eliteFraction`), from the episode that
  built the **best layout so far** instead of from the buffer. The network keeps relearning the best trajectory known,
  which pulls the greedy episodes toward it and its neighborhood. In metaheuristic terms this is an *intensification*
  mechanism, like elitism in an EA.
- **Warm-up.** Training starts only when the buffer holds 500 transitions (about 13 episodes of 39 placements), so the
  first batches are not dominated by one or two episodes.
- **Target network.** Copied from the online network every 500 steps (again about 13 episodes). Between copies, the
  targets $y$ stay put, and the online network can actually fit them.

### 8.6 What the network ends up learning

Combining Sections 5 and 8, $f_\theta$ is trained toward

$$f_\theta(\phi(s, a)) \approx (n + 1) \times \big(\text{return of the rest of the episode after placing in } a\big)$$

which equals, by telescoping, $(n + 1)(\text{obj}(L_{t}) - \text{obj}(L_{\text{final}})) / \text{obj}^*$: how much the
objective will still improve (or deteriorate) after this placement, per turbine. A cell whose placement looks good now
but blocks many good free cells (high `DOWNWIND_EXPOSURE`) gets a negative foresight. A cell whose immediate reward is
mediocre but which keeps the farm open for good placements gets a positive one. The sum, reward + foresight, can then
rank cells differently from the greedy heuristic. That re-ranking is the whole benefit of DQN over greedy.

---

## 9. Budget, cost and results

### 9.1 What one evaluation buys

DQN implements the project's `Metaheuristic` contract like every other algorithm:

- **One iteration = one episode = one evaluation.** Only the final layout of an episode is passed to
  `SearchContext.evaluate`. With the paper's budget of 2500 evaluations, DQN builds 2500 layouts.
- **Exact budgets.** The episode loop checks `ctx.shouldStop()` before every step. A run stopped mid-episode discards
  that episode. `DQNTest.oneEvaluationPerEpisodeAndExactBudget` checks this.
- **Reproducibility.** All randomness comes from the run's RNG, so the same seed gives the identical run, including the
  network. Networks, buffer and optimizer state are local to a run, so one `DQN` instance can run concurrently on many
  threads (`DQNTest.sameSeedGivesIdenticalRun`, `concurrentRunsOfOneInstanceAreIndependent`).

Counting evaluations alone flatters DQN. Every step also evaluates the partial layout incrementally (for the reward),
computes features for up to 100 actions, runs the network on all of them, and performs a training step of 32
transitions, each with up to 18 forward passes (16 candidates, the target value and the prediction). Wall-clock time is the fairer comparison with SA or local search.

### 9.2 Results reported in the project README

On the paper's 10 × 10 farm with 2500 evaluations, DQN reaches about the same objectives as SA, slightly worse on
average (`examples.rl.WFLOPDQNExample` runs DQN, SA and local search on all four cases).

On larger farms with only 300 evaluations, mean of 3 runs, Condition 1, full wake (`examples.rl.WFLOPTransferExample`):

| | 15 × 15, 88 turbines | 20 × 20, 156 turbines |
|---|---|---|
| Greedy heuristic (1 evaluation) | 0.00143910 | 0.00145581 |
| SA, 300 evaluations | 0.00145235 | 0.00148248 |
| DQN from scratch | 0.00143803 | 0.00145429 |
| DQN from the 10 × 10 network, still learning | 0.00143969 | 0.00145372 |
| DQN from the 10 × 10 network, frozen | **0.00143748** | 0.00145464 |
| SA, 30 000 evaluations (1–1.5 s, other seeds) | **0.00143257** | **0.00144980** |

How to read this:

- **Per evaluation, DQN is far ahead of SA** on large farms. Its very first episode (greedy) is already better than
  300 SA evaluations, and learning improves on greedy.
- **Per second, SA wins.** A DQN run takes 30–180 s, while SA does 30 000 evaluations in 1–1.5 s and then beats every
  DQN variant.
- So DQN is the right tool when **evaluations are expensive** (e.g. a high-fidelity wake simulation instead of the
  Jensen model), when a **good layout is needed in few evaluations**, or when **a trained network can be reused** on
  new farms. With the cheap Jensen model, SA is more efficient.

---

## 10. Transfer to other farms

Because every feature is intensive (Section 4.2) and the network scores one cell at a time (Section 4.1), a network
trained on one farm can be applied to another farm with the same turbines and cell size, of any size.
`ConstructiveWFLOPTest.featuresDoNotDependOnTheFarmSize` checks that a cell in the middle of a 10 × 10 farm and one in
the middle of a 20 × 20 farm get nearly the same features.

The builder supports the whole workflow:

```java
// 1. train on the 10 x 10 farm and keep the network
AtomicReference<Mlp> trained = new AtomicReference<>();
DQN<CellLayout> learner = DQN.<CellLayout>builder().trainedNetwork(trained::set).build();
Runner.of(learner).seed(2024).stopWhen(new EvaluationBasedTC(2500)).run(new ConstructiveWFLOP(small));
trained.get().save(Paths.get("results", "dqn-wflop-10x10.mlp"));

// 2a. keep learning on a larger farm, starting from it ("fine-tuned")
DQN.<CellLayout>builder().initialNetwork(network).softmax(0.1, 0.05, budget / 2).build();

// 2b. only use it ("frozen"): no replay, no updates
DQN.<CellLayout>builder().initialNetwork(network).training(false).softmax(0.1, 0.1, 1).greedyEvery(budget).build();
```

A frozen network still explores (softmax at temperature 0.1), so its runs sample layouts around what the network
considers best. In the README results, the frozen 10 × 10 network beats learning from scratch on 15 × 15. It never saw
a farm that large.

---

## 11. Parameters

Defaults are those of `DQN.Builder`. The lab offers the ones marked "lab" when the wind farm problem uses its *discrete*
encoding.

| Builder method | Default | Lab | Meaning and effect |
|---|---|---|---|
| `hidden(...)` | 16, 16 | ✓ (one size for both) | Hidden layer sizes. Larger networks can represent more, but learn slower. |
| `learningRate` | 1e-3 | ✓ | Adam step size. |
| `gamma` | 1 | | Discount. Keep 1: only then is the return exactly −objective/obj*. |
| `nStep` | 3 | ✓ | Real rewards before bootstrapping. Larger: faster propagation, noisier targets. |
| `batchSize` | 32 | | Transitions per training step. |
| `replayCapacity` | 20 000 | | Transitions kept. |
| `warmup` | 500 | | Transitions collected before training starts. |
| `trainEvery` | 1 | | Construction steps per training step. Increase it to trade learning for speed. |
| `targetUpdate` | 500 | | Steps between target network refreshes. |
| `softmax(start, end, episodes)` | 0.3 → 0.05 over 1000 | ✓ | Default exploration (Section 7.2). |
| `epsilon(start, end, episodes)` | | ✓ | Alternative exploration. |
| `eliteFraction` | 0.25 | ✓ | Share of every batch from the best episode. Higher: more intensification. |
| `greedyEvery` | 10 | ✓ | Every n-th episode is greedy. 0: never (then episode 0 is not greedy either). |
| `candidates` | 16 | ✓ | Actions per state the target maximizes over. 0: all (exact, slower). |
| `rewardBaseline` | true | | Learn only the foresight (Section 5). |
| `initialNetwork` | none | | Start from a given network (transfer). |
| `training` | true | | false: only use the network. |
| `trainedNetwork(consumer)` | none | | Receives the final network of every run. |

To run it:

- **Lab:** `mvnw.cmd compile exec:java`, choose *Wind farm layout (WFLOP)*, select the *discrete* encoding, and add
  a *DQN* run. Keep the budget in the low thousands of evaluations, since each episode takes milliseconds.
- **Paper protocol, DQN vs SA and LS:** `mvnw.cmd compile exec:java -Dexec.mainClass=examples.rl.WFLOPDQNExample`
- **Transfer:** `mvnw.cmd compile exec:java -Dexec.mainClass=examples.rl.WFLOPTransferExample`
- **From code**, on a `DiscreteWFLOP`:

  ```java
  DiscreteWFLOP discrete = WFLOPCase.CASE_3.createDiscrete(WindScenario.condition1());
  RunResult<CellLayout> result = Runner.of(DQN.<CellLayout>builder().build())
          .seed(42).stopWhen(new EvaluationBasedTC(2500))
          .run(new ConstructiveWFLOP(discrete));
  ```

  `dqn.viewing(ConstructiveWFLOP::new)` turns the DQN into an algorithm for `DiscreteWFLOP` directly. That is how the
  lab offers it next to EA, SA and local search.

---

## 12. Glossary

| Term | Meaning here |
|---|---|
| Episode | One construction of a layout from an empty farm. Costs one evaluation. |
| Step | One placement (or STOP) within an episode. |
| Return | Sum of an episode's rewards from some step on. Over the whole episode: −obj(final)/obj*. |
| Q(s, a) | Expected return of taking action a in state s and acting well afterwards. |
| Bootstrapping | Building a learning target from the current estimate of the value of a later state. |
| Online network | The network being trained and used to act. |
| Target network | A periodically refreshed copy, used to compute stable targets. |
| Double Q-learning | Online network picks the next action, target network values it. |
| n-step return | Use n real rewards before bootstrapping. |
| Experience replay | Train on random samples of stored past transitions instead of the latest ones. |
| Elite replay | Part of every batch comes from the best episode so far (specific to this implementation). |
| Baseline | The known reward of an action, added to the network output instead of learned. |
| Foresight | What the network learns: the value of what follows an action, per turbine. |
| Candidates | The top-16 actions of a state, among which targets maximize. |
| Huber loss | Squared error for small errors, absolute error for large ones. Its gradient is bounded. |
| Action-in network | A network that takes (state, action) features and outputs one value, applied once per action. |
| obj* | Objective of a single unwaked turbine, the unit of the rewards. |

---

## 13. References and source files

**Papers**

- Mnih et al. (2015), *Human-level control through deep reinforcement learning*, Nature 518: DQN, experience replay,
  target network.
- van Hasselt, Guez & Silver (2016), *Deep reinforcement learning with double Q-learning*, AAAI: double Q-learning.
- Sutton (1988); Sutton & Barto, *Reinforcement Learning: An Introduction*, 2nd ed. (2018), Ch. 7: n-step returns.
- Dulac-Arnold et al. (2015), *Deep reinforcement learning in large discrete action spaces*, arXiv:1512.07679:
  restricting the max to a candidate set (Wolpertinger).
- Kingma & Ba (2015), *Adam: a method for stochastic optimization*, ICLR.
- Yu & Zhang (2024), *A teaching-learning-based optimization algorithm with reinforcement learning to address wind farm
  layout optimization problem*, Applied Soft Computing 151, 111135: the WFLOP cases and protocol.
- Katić, Højstrup & Jensen (1987): the Jensen wake model. Mosetti et al. (1994): the farm and cost model.

**Source files**

| File | Role |
|---|---|
| `metaheuristic/rl/dqn/DQN.java` | The algorithm: episode loop, exploration, transitions, training. |
| `metaheuristic/rl/dqn/ReplayBuffer.java` | Replay buffer, `Observation` (a stored state), `Transition`. |
| `metaheuristic/rl/dqn/Exploration.java`, `Softmax.java`, `EpsilonGreedy.java` | Exploration strategies and schedules. |
| `metaheuristic/rl/nn/Mlp.java`, `Adam.java` | The network and its optimizer. |
| `metaheuristic/rl/ConstructiveProblem.java`, `Construction.java` | Generic episodic view of a problem. |
| `metaheuristic/rl/FeaturedConstructiveProblem.java`, `FeaturedConstruction.java` | Features, known rewards and value scale. |
| `metaheuristic/rl/GreedyConstruction.java` | The greedy heuristic DQN starts from. |
| `problems/wflop/rl/ConstructiveWFLOP.java` | WFLOP as a constructive problem; empty-farm exposures. |
| `problems/wflop/rl/LayoutConstruction.java` | One episode: actions, rewards, feature computation. |
| `problems/wflop/rl/LayoutFeature.java` | The 12 features. |
| `problems/wflop/WakeMatrix.java`, `JensenWakeModel.java`, `WindFarm.java` | Wake physics and (incremental) power. |
| `examples/rl/WFLOPDQNExample.java`, `WFLOPTransferExample.java` | Runnable experiments. |
| `test/.../rl/dqn/DQNTest.java`, `test/.../wflop/rl/ConstructiveWFLOPTest.java` | The properties claimed above, as tests. |
