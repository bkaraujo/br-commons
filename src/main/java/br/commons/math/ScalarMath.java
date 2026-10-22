package br.commons.math;

import org.jspecify.annotations.NullMarked;

/** Strategy escalar: usada quando {@code jdk.incubator.vector} não está no runtime. */
@NullMarked
final class ScalarMath implements MathEngine {

    @Override
    public double sum(double[] a) {
        var r = 0.0;
        for (var x : a) r += x;
        return r;
    }

    @Override
    public double min(double[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.min(r, x);
        return r;
    }

    @Override
    public double max(double[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.max(r, x);
        return r;
    }

    @Override
    public double dot(double[] a, double[] b) {
        var r = 0.0;
        for (var i = 0; i < a.length; i++) r += a[i] * b[i];
        return r;
    }

    @Override
    public void add(double[] a, double[] b, double[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void sub(double[] a, double[] b, double[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] - b[i];
    }

    @Override
    public void mul(double[] a, double[] b, double[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] * b[i];
    }

    @Override
    public void scale(double[] a, double k, double[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] * k;
    }

    @Override
    public float sum(float[] a) {
        var r = 0f;
        for (var x : a) r += x;
        return r;
    }

    @Override
    public float min(float[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.min(r, x);
        return r;
    }

    @Override
    public float max(float[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.max(r, x);
        return r;
    }

    @Override
    public float dot(float[] a, float[] b) {
        var r = 0f;
        for (var i = 0; i < a.length; i++) r += a[i] * b[i];
        return r;
    }

    @Override
    public void add(float[] a, float[] b, float[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void mul(float[] a, float[] b, float[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] * b[i];
    }

    @Override
    public int sum(int[] a) {
        var r = 0;
        for (var x : a) r += x;
        return r;
    }

    @Override
    public int min(int[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.min(r, x);
        return r;
    }

    @Override
    public int max(int[] a) {
        var r = a[0];
        for (var x : a) r = java.lang.Math.max(r, x);
        return r;
    }

    @Override
    public void add(int[] a, int[] b, int[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] + b[i];
    }

    @Override
    public void mul(int[] a, int[] b, int[] out) {
        for (var i = 0; i < a.length; i++) out[i] = a[i] * b[i];
    }
}
