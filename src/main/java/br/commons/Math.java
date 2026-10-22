package br.commons;

import br.commons.math.MathEngine;
import org.jspecify.annotations.NullMarked;

/**
 * Operações numéricas sobre arrays com SIMD (Vector API).
 * <p>
 * A implementação ({@link MathEngine}) é escolhida no primeiro uso: SIMD se o módulo
 * {@code jdk.incubator.vector} estiver no runtime ({@code --add-modules jdk.incubator.vector}),
 * senão laço escalar equivalente. Reduções de ponto flutuante ({@code sum}, {@code dot}) somam em
 * ordem diferente da sequencial, então o último dígito pode divergir; {@code int} segue a aritmética
 * com overflow silencioso do Java.
 */
@NullMarked
public final class Math {

    private Math() {
    }

    public static int min(int a, int b) {
        return java.lang.Math.min(a, b);
    }

    // ---- double[] ----

    public static double sum(double[] a) {
        return Holder.ENGINE.sum(a);
    }

    public static double min(double[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.min(a);
    }

    public static double max(double[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.max(a);
    }

    public static double dot(double[] a, double[] b) {
        same(a.length, b.length);
        return Holder.ENGINE.dot(a, b);
    }

    /** {@code out[i] = a[i] + b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void add(double[] a, double[] b, double[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.add(a, b, out);
    }

    /** {@code out[i] = a[i] - b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void sub(double[] a, double[] b, double[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.sub(a, b, out);
    }

    /** {@code out[i] = a[i] * b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void mul(double[] a, double[] b, double[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.mul(a, b, out);
    }

    /** {@code out[i] = a[i] * k}; {@code out} pode ser {@code a}. */
    public static void scale(double[] a, double k, double[] out) {
        same(a.length, out.length);
        Holder.ENGINE.scale(a, k, out);
    }

    // ---- float[] ----

    public static float sum(float[] a) {
        return Holder.ENGINE.sum(a);
    }

    public static float min(float[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.min(a);
    }

    public static float max(float[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.max(a);
    }

    public static float dot(float[] a, float[] b) {
        same(a.length, b.length);
        return Holder.ENGINE.dot(a, b);
    }

    /** {@code out[i] = a[i] + b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void add(float[] a, float[] b, float[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.add(a, b, out);
    }

    /** {@code out[i] = a[i] * b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void mul(float[] a, float[] b, float[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.mul(a, b, out);
    }

    // ---- int[] ----

    public static int sum(int[] a) {
        return Holder.ENGINE.sum(a);
    }

    public static int min(int[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.min(a);
    }

    public static int max(int[] a) {
        nonEmpty(a.length);
        return Holder.ENGINE.max(a);
    }

    /** {@code out[i] = a[i] + b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void add(int[] a, int[] b, int[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.add(a, b, out);
    }

    /** {@code out[i] = a[i] * b[i]}, {@code out} pode ser {@code a} ou {@code b}. */
    public static void mul(int[] a, int[] b, int[] out) {
        same(a.length, b.length, out.length);
        Holder.ENGINE.mul(a, b, out);
    }

    private static void nonEmpty(int length) {
        if (length == 0) throw new IllegalArgumentException("array vazio");
    }

    private static void same(int... lengths) {
        for (var length : lengths) {
            if (length != lengths[0]) throw new IllegalArgumentException("arrays de tamanhos diferentes");
        }
    }

    /** Initialization-on-demand: a estratégia é escolhida no primeiro uso. */
    private static final class Holder {
        static final MathEngine ENGINE = MathEngine.select();
    }
}
