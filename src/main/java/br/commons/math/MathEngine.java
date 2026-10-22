package br.commons.math;

import br.commons.Math;
import org.jspecify.annotations.NullMarked;

/** Strategy de {@link Math}: argumentos já validados; escolhida no primeiro uso. */
@NullMarked
public sealed interface MathEngine permits ScalarMathEngine, SimdMathEngine {

    double sum(double[] a);

    double min(double[] a);

    double max(double[] a);

    double dot(double[] a, double[] b);

    void add(double[] a, double[] b, double[] out);

    void sub(double[] a, double[] b, double[] out);

    void mul(double[] a, double[] b, double[] out);

    void scale(double[] a, double k, double[] out);

    float sum(float[] a);

    float min(float[] a);

    float max(float[] a);

    float dot(float[] a, float[] b);

    void add(float[] a, float[] b, float[] out);

    void mul(float[] a, float[] b, float[] out);

    int sum(int[] a);

    int min(int[] a);

    int max(int[] a);

    void add(int[] a, int[] b, int[] out);

    void mul(int[] a, int[] b, int[] out);

    static MathEngine select() {
        if (ModuleLayer.boot().findModule("jdk.incubator.vector").isEmpty()) return new ScalarMathEngine();
        return new SimdMathEngine();
    }
}
