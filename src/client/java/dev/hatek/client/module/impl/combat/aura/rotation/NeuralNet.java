package dev.hatek.client.module.impl.combat.aura.rotation;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.random.RandomGenerator;

/**
 * A tiny feed-forward network (14 inputs, two tanh layers, 4 outputs: the mean and the log-spread of the yaw and pitch
 * change of one tick), trained offline on the player's own recorded aim (see AimRecorder). Pure Java, no game classes.
 */
public final class NeuralNet {
    private final int feats;
    private final int hid;
    private final double[] mean;
    private final double[] std;
    private final double[] ysc;
    private final double[][] w1;
    private final double[] b1;
    private final double[][] w2;
    private final double[] b2;
    private final double[][] w3;
    private final double[] b3;

    private NeuralNet(JsonObject json) {
        this.feats = json.get("feats").getAsInt();
        this.hid = json.get("hid").getAsInt();
        this.mean = vector(json, "mean");
        this.std = vector(json, "std");
        this.ysc = vector(json, "ysc");
        this.w1 = matrix(json, "W1");
        this.b1 = vector(json, "b1");
        this.w2 = matrix(json, "W2");
        this.b2 = vector(json, "b2");
        this.w3 = matrix(json, "W3");
        this.b3 = vector(json, "b3");
    }

    public static NeuralNet load(InputStream stream) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return new NeuralNet(new Gson().fromJson(reader, JsonObject.class));
        }
    }

    private static double[] vector(JsonObject json, String key) {
        var array = json.getAsJsonArray(key);
        double[] out = new double[array.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = array.get(i).getAsDouble();
        }
        return out;
    }

    private static double[][] matrix(JsonObject json, String key) {
        var array = json.getAsJsonArray(key);
        double[][] out = new double[array.size()][];
        for (int i = 0; i < out.length; i++) {
            var row = array.get(i).getAsJsonArray();
            out[i] = new double[row.size()];
            for (int j = 0; j < out[i].length; j++) {
                out[i][j] = row.get(j).getAsDouble();
            }
        }
        return out;
    }

    public int features() {
        return this.feats;
    }

    /**
     * Returns {meanYaw, meanPitch, spreadYaw, spreadPitch} in degrees per tick for the feature vector (the order of the
     * recorder columns, errYaw first).
     */
    public double[] predict(double[] x) {
        double[] z = new double[this.feats];
        for (int i = 0; i < this.feats; i++) {
            z[i] = (x[i] - this.mean[i]) / this.std[i];
        }
        double[] h1 = layer(z, this.w1, this.b1);
        double[] h2 = layer(h1, this.w2, this.b2);
        double[] out = new double[4];
        for (int k = 0; k < 4; k++) {
            double sum = this.b3[k];
            for (int j = 0; j < this.hid; j++) {
                sum += h2[j] * this.w3[j][k];
            }
            out[k] = sum;
        }
        double spreadYaw = Math.exp(Math.max(-4.0, Math.min(2.0, out[2]))) * this.ysc[0];
        double spreadPitch = Math.exp(Math.max(-4.0, Math.min(2.0, out[3]))) * this.ysc[1];
        return new double[]{out[0] * this.ysc[0], out[1] * this.ysc[1], spreadYaw, spreadPitch};
    }

    private double[] layer(double[] in, double[][] w, double[] b) {
        double[] out = new double[this.hid];
        for (int j = 0; j < this.hid; j++) {
            double sum = b[j];
            for (int i = 0; i < in.length; i++) {
                sum += in[i] * w[i][j];
            }
            out[j] = Math.tanh(sum);
        }
        return out;
    }

    /** One sampled step: the mean plus {@code temperature} times the learned spread of noise. */
    public double[] sample(double[] x, double temperature, RandomGenerator random) {
        double[] p = predict(x);
        return new double[]{p[0] + temperature * p[2] * random.nextGaussian(), p[1] + temperature * p[3] * random.nextGaussian()};
    }
}
