package com.maxenonyme.createsubmarine.submarine.stress;

/**
 * Merge stub: shape-factor scaling is neutral (1.0) until the analytical
 * panel model is re-based on the cogfly lattice. See {@link HullShapeAnalyzer}.
 */
public class HullShapeFactor {

    public static double[] compute(int n, double referenceSpan, double min, double max) {
        double[] factors = new double[n];
        java.util.Arrays.fill(factors, 1.0);
        return factors;
    }
}
