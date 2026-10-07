package com.maxenonyme.createsubmarine.submarine.stress;

public final class HullShapeFactor {
    private HullShapeFactor() {
    }

    public static double[] compute(SolverCore core, double referenceSpan, double min, double max) {
        double[] ratios = core.computePanelBendingRatios();
        double reference = SolverCore.PLATE_BENDING_BETA * core.rhoG * referenceSpan * referenceSpan;
        double[] factors = new double[core.n];
        for (int i = 0; i < core.n; i++) {
            double r = ratios[i];
            factors[i] = r <= 1e-30 ? max : Math.max(min, Math.min(max, Math.sqrt(reference / r)));
        }
        return factors;
    }
}
