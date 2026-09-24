package util.random;

class Roulette
{
    double[] c;
    double total;
    RNG rng;

    public Roulette(RNG rng, double[] n) {
        this.rng = rng;
        total = 0;
        c = new double[n.length+1];
        c[0] = 0;
        // Create cumulative values for later:
        for (int i = 0; i < n.length; i++) {
            c[i+1] = c[i] + n[i];
            total += n[i];
        }
    }

    public int spin() {
        double r = rng.randDouble() * total;     // Create a rng number between 0 and 1 and times by the total we calculated earlier.
        //int j; for (j = 0; j < c.Length; j++) if (c[j] > r) break; return j-1; // Don't use this - it's slower than the binary search below.

        //// Binary search for efficiency. Objective is to find index of the number just above r:
        int a = 0;
        int b = c.length - 1;
        while (b - a > 1) {
            int mid = (a + b) / 2;
            if (c[mid] > r) b = mid;
            else a = mid;
        }
        return a;
    }
}