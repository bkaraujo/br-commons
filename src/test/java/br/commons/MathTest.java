package br.commons;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class MathTest {

    private static final int[] SIZES = {1, 3, 7, 16, 17, 100, 1003};

    @Test
    void doubleOperationsMatchScalar() {
        var random = new Random(1);
        for (var n : SIZES) {
            var a = random.doubles(n, -100, 100).toArray();
            var b = random.doubles(n, -100, 100).toArray();
            double sum = 0, dot = 0, min = a[0], max = a[0];
            var add = new double[n];
            var mul = new double[n];
            var sub = new double[n];
            var scale = new double[n];
            for (var i = 0; i < n; i++) {
                sum += a[i];
                dot += a[i] * b[i];
                min = java.lang.Math.min(min, a[i]);
                max = java.lang.Math.max(max, a[i]);
                add[i] = a[i] + b[i];
                sub[i] = a[i] - b[i];
                mul[i] = a[i] * b[i];
                scale[i] = a[i] * 2.5;
            }
            assertEquals(sum, Math.sum(a), 1e-6);
            assertEquals(dot, Math.dot(a, b), 1e-6);
            assertEquals(min, Math.min(a));
            assertEquals(max, Math.max(a));
            var out = new double[n];
            Math.add(a, b, out);
            assertArrayEquals(add, out);
            Math.sub(a, b, out);
            assertArrayEquals(sub, out);
            Math.mul(a, b, out);
            assertArrayEquals(mul, out);
            Math.scale(a, 2.5, out);
            assertArrayEquals(scale, out);
        }
    }

    @Test
    void floatOperationsMatchScalar() {
        var random = new Random(2);
        for (var n : SIZES) {
            var a = new float[n];
            var b = new float[n];
            for (var i = 0; i < n; i++) {
                a[i] = random.nextInt(200) - 100;
                b[i] = random.nextInt(200) - 100;
            }
            float sum = 0, dot = 0, min = a[0], max = a[0];
            var add = new float[n];
            var mul = new float[n];
            for (var i = 0; i < n; i++) {
                sum += a[i];
                dot += a[i] * b[i];
                min = java.lang.Math.min(min, a[i]);
                max = java.lang.Math.max(max, a[i]);
                add[i] = a[i] + b[i];
                mul[i] = a[i] * b[i];
            }
            assertEquals(sum, Math.sum(a), 1e-3f);
            assertEquals(dot, Math.dot(a, b), 1e-3f);
            assertEquals(min, Math.min(a));
            assertEquals(max, Math.max(a));
            var out = new float[n];
            Math.add(a, b, out);
            assertArrayEquals(add, out);
            Math.mul(a, b, out);
            assertArrayEquals(mul, out);
        }
    }

    @Test
    void intOperationsMatchScalar() {
        var random = new Random(3);
        for (var n : SIZES) {
            var a = random.ints(n, -1000, 1000).toArray();
            var b = random.ints(n, -1000, 1000).toArray();
            int sum = 0, min = a[0], max = a[0];
            var add = new int[n];
            var mul = new int[n];
            for (var i = 0; i < n; i++) {
                sum += a[i];
                min = java.lang.Math.min(min, a[i]);
                max = java.lang.Math.max(max, a[i]);
                add[i] = a[i] + b[i];
                mul[i] = a[i] * b[i];
            }
            assertEquals(sum, Math.sum(a));
            assertEquals(min, Math.min(a));
            assertEquals(max, Math.max(a));
            var out = new int[n];
            Math.add(a, b, out);
            assertArrayEquals(add, out);
            Math.mul(a, b, out);
            assertArrayEquals(mul, out);
        }
    }

    @Test
    void rejectsEmptyAndMismatched() {
        assertThrows(IllegalArgumentException.class, () -> Math.min(new double[0]));
        assertThrows(IllegalArgumentException.class, () -> Math.dot(new double[2], new double[3]));
        assertThrows(IllegalArgumentException.class, () -> Math.add(new int[2], new int[2], new int[3]));
    }
}
