200 of 200 runs finished.

| configuration | n | mean cost | median | gap to default % | 95% interval | better/worse | p | Holm p | diversity | duplicates | immigrations | mutationRate |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| noSA | 50 | 105315.0501 | 95096.2133 | -0.380 | -0.559 … -0.202 | 36/14 | <0.001 | <0.001 | 0.01857 | 0.4065 | 9.280 | 0.1389 |
| plain | 50 | 105369.0851 | 94921.8392 | -0.158 | -0.413 … +0.097 | 37/13 | 0.133 | 0.266 | 0.03250 | 0.04750 | 133.2 | 0.7500 |
| default | 50 | 105916.1911 | 95483.4952 | +0.000 | +0.000 … +0.000 | 0/0 | 1.000 | 1.000 | 0.02588 | 0.3175 | 2.780 | 0.06628 |
| noLS | 50 | 105889.4031 | 95593.4559 | +0.023 | -0.119 … +0.166 | 17/33 | 0.372 | 0.372 | 0.01422 | 0.4180 | 7.020 | 0.1023 |

Gap to default by instance (%):

| configuration | C100 | T1 | T4 | T7 | T10 |
|---|---|---|---|---|---|
| noSA | -0.046 | -0.217 | -0.343 | -0.326 | -0.970 |
| plain | +1.350 | -0.413 | -0.563 | -0.607 | -0.557 |
| default | +0.000 | +0.000 | +0.000 | +0.000 | +0.000 |
| noLS | +0.200 | -0.061 | +0.111 | -0.117 | -0.016 |

Gap: (cost − default cost) / default cost of the run with the same instance and repeat; negative is better. p: exact two-sided Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.
