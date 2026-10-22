package br.commons.math;

import br.commons.MathEngine;
import jdk.incubator.vector.DoubleVector;
import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.IntVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;
import org.jspecify.annotations.NullMarked;

/** Strategy SIMD: referencia a Vector API, então só é carregada quando o módulo existe. */
@NullMarked
final class SimdMathEngine implements MathEngine {

    private static final VectorSpecies<Double> D = DoubleVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Float> F = FloatVector.SPECIES_PREFERRED;
    private static final VectorSpecies<Integer> I = IntVector.SPECIES_PREFERRED;

    // double

    @Override
    public double sum(double[] a) {
        var acc = DoubleVector.zero(D);
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) acc = acc.add(DoubleVector.fromArray(D, a, i));
        var r = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) r += a[i];
        return r;
    }

    @Override
    public double min(double[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= D.length()) {
            var acc = DoubleVector.fromArray(D, a, 0);
            for (i = D.length(); i < D.loopBound(a.length); i += D.length()) acc = acc.min(DoubleVector.fromArray(D, a, i));
            r = acc.reduceLanes(VectorOperators.MIN);
        }
        for (; i < a.length; i++) r = java.lang.Math.min(r, a[i]);
        return r;
    }

    @Override
    public double max(double[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= D.length()) {
            var acc = DoubleVector.fromArray(D, a, 0);
            for (i = D.length(); i < D.loopBound(a.length); i += D.length()) acc = acc.max(DoubleVector.fromArray(D, a, i));
            r = acc.reduceLanes(VectorOperators.MAX);
        }
        for (; i < a.length; i++) r = java.lang.Math.max(r, a[i]);
        return r;
    }

    @Override
    public double dot(double[] a, double[] b) {
        var acc = DoubleVector.zero(D);
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) {
            acc = DoubleVector.fromArray(D, a, i).fma(DoubleVector.fromArray(D, b, i), acc);
        }
        var r = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) r += a[i] * b[i];
        return r;
    }

    @Override
    public void add(double[] a, double[] b, double[] out) {
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) {
            DoubleVector.fromArray(D, a, i).add(DoubleVector.fromArray(D, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void sub(double[] a, double[] b, double[] out) {
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) {
            DoubleVector.fromArray(D, a, i).sub(DoubleVector.fromArray(D, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] - b[i];
    }

    @Override
    public void mul(double[] a, double[] b, double[] out) {
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) {
            DoubleVector.fromArray(D, a, i).mul(DoubleVector.fromArray(D, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] * b[i];
    }

    @Override
    public void scale(double[] a, double k, double[] out) {
        var i = 0;
        for (var up = D.loopBound(a.length); i < up; i += D.length()) {
            DoubleVector.fromArray(D, a, i).mul(k).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] * k;
    }

    // float

    @Override
    public float sum(float[] a) {
        var acc = FloatVector.zero(F);
        var i = 0;
        for (var up = F.loopBound(a.length); i < up; i += F.length()) acc = acc.add(FloatVector.fromArray(F, a, i));
        var r = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) r += a[i];
        return r;
    }

    @Override
    public float min(float[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= F.length()) {
            var acc = FloatVector.fromArray(F, a, 0);
            for (i = F.length(); i < F.loopBound(a.length); i += F.length()) acc = acc.min(FloatVector.fromArray(F, a, i));
            r = acc.reduceLanes(VectorOperators.MIN);
        }
        for (; i < a.length; i++) r = java.lang.Math.min(r, a[i]);
        return r;
    }

    @Override
    public float max(float[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= F.length()) {
            var acc = FloatVector.fromArray(F, a, 0);
            for (i = F.length(); i < F.loopBound(a.length); i += F.length()) acc = acc.max(FloatVector.fromArray(F, a, i));
            r = acc.reduceLanes(VectorOperators.MAX);
        }
        for (; i < a.length; i++) r = java.lang.Math.max(r, a[i]);
        return r;
    }

    @Override
    public float dot(float[] a, float[] b) {
        var acc = FloatVector.zero(F);
        var i = 0;
        for (var up = F.loopBound(a.length); i < up; i += F.length()) {
            acc = FloatVector.fromArray(F, a, i).fma(FloatVector.fromArray(F, b, i), acc);
        }
        var r = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) r += a[i] * b[i];
        return r;
    }

    @Override
    public void add(float[] a, float[] b, float[] out) {
        var i = 0;
        for (var up = F.loopBound(a.length); i < up; i += F.length()) {
            FloatVector.fromArray(F, a, i).add(FloatVector.fromArray(F, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void mul(float[] a, float[] b, float[] out) {
        var i = 0;
        for (var up = F.loopBound(a.length); i < up; i += F.length()) {
            FloatVector.fromArray(F, a, i).mul(FloatVector.fromArray(F, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] * b[i];
    }

    // int

    @Override
    public int sum(int[] a) {
        var acc = IntVector.zero(I);
        var i = 0;
        for (var up = I.loopBound(a.length); i < up; i += I.length()) acc = acc.add(IntVector.fromArray(I, a, i));
        var r = acc.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) r += a[i];
        return r;
    }

    @Override
    public int min(int[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= I.length()) {
            var acc = IntVector.fromArray(I, a, 0);
            for (i = I.length(); i < I.loopBound(a.length); i += I.length()) acc = acc.min(IntVector.fromArray(I, a, i));
            r = acc.reduceLanes(VectorOperators.MIN);
        }
        for (; i < a.length; i++) r = java.lang.Math.min(r, a[i]);
        return r;
    }

    @Override
    public int max(int[] a) {
        var r = a[0];
        var i = 0;
        if (a.length >= I.length()) {
            var acc = IntVector.fromArray(I, a, 0);
            for (i = I.length(); i < I.loopBound(a.length); i += I.length()) acc = acc.max(IntVector.fromArray(I, a, i));
            r = acc.reduceLanes(VectorOperators.MAX);
        }
        for (; i < a.length; i++) r = java.lang.Math.max(r, a[i]);
        return r;
    }

    @Override
    public void add(int[] a, int[] b, int[] out) {
        var i = 0;
        for (var up = I.loopBound(a.length); i < up; i += I.length()) {
            IntVector.fromArray(I, a, i).add(IntVector.fromArray(I, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void mul(int[] a, int[] b, int[] out) {
        var i = 0;
        for (var up = I.loopBound(a.length); i < up; i += I.length()) {
            IntVector.fromArray(I, a, i).mul(IntVector.fromArray(I, b, i)).intoArray(out, i);
        }
        for (; i < a.length; i++) out[i] = a[i] * b[i];
    }
}
