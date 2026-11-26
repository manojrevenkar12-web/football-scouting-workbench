package scout.world;

import java.util.SplittableRandom;

/** Same luck for every design: every draw comes from a stream keyed by the seed and what it is for. */
public final class Rng {
    private Rng() {}

    public static long key(long seed, Object... parts) {
        long h = 0xcbf29ce484222325L ^ seed;
        for (Object p : parts) {
            for (char c : String.valueOf(p).toCharArray()) {
                h ^= c;
                h *= 0x100000001b3L;
            }
            h ^= '|';
            h *= 0x100000001b3L;
        }
        return mix(h);
    }

    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public static SplittableRandom stream(long seed, Object... parts) {
        return new SplittableRandom(key(seed, parts));
    }

    public static double u(long seed, Object... parts) {
        return (key(seed, parts) >>> 11) * 0x1.0p-53;
    }

    public static double normal(SplittableRandom r) {
        double u1 = Math.max(r.nextDouble(), 1e-12), u2 = r.nextDouble();
        return Math.sqrt(-2 * Math.log(u1)) * Math.cos(2 * Math.PI * u2);
    }

    public static int poisson(SplittableRandom r, double mean) {
        if (mean <= 0) return 0;
        if (mean > 30) return Math.max(0, (int) Math.round(mean + Math.sqrt(mean) * normal(r)));
        double l = Math.exp(-mean), p = 1;
        int k = 0;
        do {
            k++;
            p *= r.nextDouble();
        } while (p > l);
        return k - 1;
    }
}
