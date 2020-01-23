import com.zaxxer.sparsebits.SparseBitSet;
import org.apache.lucene.SparseFixedBitSet;
import org.roaringbitmap.RoaringBitmap;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class ZaxxerTest {

    public static void main(String[] args) throws Exception {

        for (int i = Integer.MAX_VALUE; i > 1000000; i -=1) {
            System.out.println(i);
            try {
                SparseFixedBitSet sb = new SparseFixedBitSet(i);
            } catch (AssertionError e) {
                System.out.println("problem");
            }
        }
        Random r = new Random();
        int sz = 100000;
        int iterations = 100;
        long absents = 0;
        long gets = 0;
        long totalElapsed = 0;
        int warmup = 10;
        int observations = 0;
        int max = Integer.MAX_VALUE;
        for (int it = 0; it < iterations; it++) {
            long start = System.currentTimeMillis();
            System.out.println("iteration " + it);
            int[] ints = new int[sz];
            for (int i = 0; i < sz; i++) {
                ints[i] = Math.abs(r.nextInt(max));
            }
//            EWAHCompressedBitmap bm = new EWAHCompressedBitmap();
  //          SparseBitSet bs = new SparseBitSet();
            SparseFixedBitSet sparseFixedBitSet= new SparseFixedBitSet(Integer.MAX_VALUE);
            for (int i = 0; i < sz; i++) {
                /*bm.set(ints[i]);
                if (bm.get(Math.abs(r.nextInt()))) {
                    gets++;
                }*/

    /*            bs.set(ints[i]);
                long absent = bs.nextClearBit(ints[i]);
                absents += absent;

                if (bs.get(Math.abs(r.nextInt()))) {
                    gets++;
                }

                */

                sparseFixedBitSet.set(ints[i]);
                sparseFixedBitSet.set(ints[i]);
                long absent = sparseFixedBitSet.nextSetBit(ints[i]);
                absents += absent;
                if (sparseFixedBitSet.get(Math.abs(r.nextInt(max)))) {
                    gets++;
                }
            }
            if (it > warmup) {
                long elapsed = System.currentTimeMillis() - start;
                System.out.println(absents + " " + gets + " " + elapsed);
                totalElapsed += elapsed;
                observations++;
            }
            //check(rb, ints);
        }
        System.out.println(totalElapsed + " total ms, " +
                (double) totalElapsed / (double) observations + " avg ms");
        int big = Integer.MAX_VALUE - 1;
        /*for (int i = 10000000; i < Integer.MAX_VALUE; i += 100000000) {
            System.out.println(i);
           // long[] longs = new long[i];
        }
//        long[] longs = new long[big];
        long nxts = 0;
        for (int it = 0; it < iterations; it++) {
            org.apache.lucene.BitSet bitSet = new org.apache.lucene.BitSet();
            for (int i = 0; i < sz; i++) {
                bitSet.set(ints[i]);
                int nxt = bitSet.nextSetBit(0);
                nxts += nxt;
                bitSet.get(ints[i]);
            }
            //check(bitSet, ints);
        }
        System.out.println(elapsed + " "+nxts);*/

    }

    private static void check(SparseBitSet bitSet, int[] ints) {
        Set<Integer> uniq = new HashSet<Integer>();
        for (int i : ints) {
            uniq.add(i);
        }
        if (bitSet.cardinality() != uniq.size()) {
            throw new RuntimeException("cardinality");
        }
        for (int i : ints) {
            if (!bitSet.get(i)) {
                throw new RuntimeException("Yikes!");
            }
        }
    }

    private static void check(RoaringBitmap rb, int[] ints) {
        Set<Integer> uniq = new HashSet<Integer>();
        for (int i : ints) {
            uniq.add(i);
        }
        if (rb.getCardinality() != uniq.size()) {
            System.out.println(rb.getCardinality() + " "+ uniq.size());
            throw new RuntimeException("cardinality");
        }
        for (int i : ints) {
            if (!rb.contains(i)) {
                throw new RuntimeException("Yikes!");
            }
        }
    }
}
