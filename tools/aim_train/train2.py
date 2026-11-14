"""Neural aim trainer v2: longer history, target angular velocity, swing timer, a bigger network, block validation.

python train2.py out.json aim_*.csv            # train the v2 model (22 inputs)
python train2.py out.json aim_*.csv --v1       # the old 14-input features (for a fair comparison)
Needs only numpy. The recorder columns are the same as for train.py.
"""
import sys, json
import numpy as np

COLS = 17
YAW_SIGNED_V2 = (0, 4, 7, 8, 10, 12, 14, 19)
YAW_SIGNED_V1 = (0, 4, 7, 8, 10)


def load_segments(paths):
    segs = []
    for p in paths:
        d = np.genfromtxt(p, delimiter=',', skip_header=1)
        if d.ndim == 2 and d.shape[1] == COLS:
            segs.append(d)
    return segs


def features_v2(d):
    """22 features per row, computed from the consecutive rows of one recording."""
    n = len(d)
    X = np.zeros((n, 22))
    X[:, :8] = d[:, :8]
    X[:, 8:12] = d[:, 8:12]                       # last two achieved turns (yaw, pitch)
    prev_d1 = d[:, 8:10]
    for k, (src, dst) in enumerate(((2, 12), (3, 14))):
        shifted = np.zeros((n, 2))
        shifted[src:] = prev_d1[:-src] if src < n else 0
        X[:, dst:dst + 2] = shifted               # turns 3 and 4 ticks ago
    X[:, 16] = d[:, 12]                           # weapon charge
    X[:, 17] = d[:, 13]                           # on the ground
    X[:, 18] = np.degrees(np.arctan2(0.3, np.maximum(d[:, 2], 0.3)))   # half size of the body, degrees
    # how fast the target moves on the screen: change of the error plus the turn that was made
    prev_err = np.vstack([d[:1, :2], d[:-1, :2]])
    X[:, 19] = d[:, 0] - prev_err[:, 0] + d[:, 8]
    X[:, 20] = d[:, 1] - prev_err[:, 1] + d[:, 9]
    # ticks since the last swing
    since = np.zeros(n)
    last = -40
    for i in range(n):
        if d[i, 14] > 0.5:
            last = i
        since[i] = min(40, i - last)
    X[:, 21] = since
    return X


def features_v1(d):
    return d[:, :14].copy()


def clip_features(X, v1):
    X[:, 0] = np.clip(X[:, 0], -180, 180)
    X[:, 1] = np.clip(X[:, 1], -90, 90)
    yaw_hist = (8, 10) if v1 else (8, 10, 12, 14)
    pit_hist = (9, 11) if v1 else (9, 11, 13, 15)
    for i in yaw_hist:
        X[:, i] = np.clip(X[:, i], -60, 60)
    for i in pit_hist:
        X[:, i] = np.clip(X[:, i], -20, 20)
    if not v1:
        X[:, 19] = np.clip(X[:, 19], -40, 40)
        X[:, 20] = np.clip(X[:, 20], -15, 15)
    return X


def mirror(X, Y, v1):
    X = X.copy(); Y = Y.copy()
    for i in (YAW_SIGNED_V1 if v1 else YAW_SIGNED_V2):
        X[:, i] = -X[:, i]
    Y[:, 0] = -Y[:, 0]
    return X, Y


class Net:
    def __init__(self, feats, hid, rng):
        self.feats, self.hid = feats, hid
        self.W1 = rng.normal(0, 1 / np.sqrt(feats), (feats, hid)); self.b1 = np.zeros(hid)
        self.W2 = rng.normal(0, 1 / np.sqrt(hid), (hid, hid)); self.b2 = np.zeros(hid)
        self.W3 = rng.normal(0, 0.1 / np.sqrt(hid), (hid, 4)); self.b3 = np.zeros(4)
        self.params = ['W1', 'b1', 'W2', 'b2', 'W3', 'b3']

    def forward(self, x):
        h1 = np.tanh(x @ self.W1 + self.b1)
        h2 = np.tanh(h1 @ self.W2 + self.b2)
        return h2 @ self.W3 + self.b3, (x, h1, h2)


def nll_of(net, X, Y):
    o, _ = net.forward(X)
    mu = o[:, :2]; ls = np.clip(o[:, 2:], -4, 2)
    return np.mean(ls + 0.5 * (mu - Y) ** 2 * np.exp(-2 * ls)), np.mean((mu - Y) ** 2, 0)


def train(Xtr, Ytr, Xva, Yva, hid, epochs=160, seed=1, l2=1e-5, verbose=True):
    rng = np.random.default_rng(seed)
    net = Net(Xtr.shape[1], hid, rng)
    m = {k: np.zeros_like(getattr(net, k)) for k in net.params}
    v = {k: np.zeros_like(getattr(net, k)) for k in net.params}
    t = 0; best = (1e9, None); n = len(Xtr)
    for ep in range(epochs):
        idx = rng.permutation(n)
        for s in range(0, n, 256):
            b = idx[s:s + 256]
            x, y = Xtr[b], Ytr[b]
            o, (x_, h1, h2) = net.forward(x)
            mu = o[:, :2]; ls = np.clip(o[:, 2:], -4, 2)
            inv = np.exp(-2 * ls); diff = mu - y
            go = np.concatenate([diff * inv / len(b), (1 - diff ** 2 * inv) / len(b)], 1)
            g = {'W3': h2.T @ go + l2 * net.W3, 'b3': go.sum(0)}
            gh2 = (go @ net.W3.T) * (1 - h2 ** 2)
            g['W2'] = h1.T @ gh2 + l2 * net.W2; g['b2'] = gh2.sum(0)
            gh1 = (gh2 @ net.W2.T) * (1 - h1 ** 2)
            g['W1'] = x_.T @ gh1 + l2 * net.W1; g['b1'] = gh1.sum(0)
            t += 1
            lr = 2e-3 * (0.3 if ep > epochs * 0.6 else 1.0) * (0.3 if ep > epochs * 0.85 else 1.0)
            for k in net.params:
                m[k] = 0.9 * m[k] + 0.1 * g[k]; v[k] = 0.999 * v[k] + 0.001 * g[k] ** 2
                setattr(net, k, getattr(net, k) - lr * (m[k] / (1 - 0.9 ** t)) / (np.sqrt(v[k] / (1 - 0.999 ** t)) + 1e-8))
        if ep % 10 == 9 or ep == epochs - 1:
            nll, mse = nll_of(net, Xva, Yva)
            if verbose:
                print(f"ep {ep + 1:3d} val nll {nll:.4f} mse yaw {mse[0]:.3f} pitch {mse[1]:.3f}")
            if nll < best[0]:
                best = (nll, {k: getattr(net, k).copy() for k in net.params})
    for k, val in best[1].items():
        setattr(net, k, val)
    return net, best[0]


def build(segs, v1, engaged_only=True):
    Xs, Ys = [], []
    for d in segs:
        d = d[d[:, 2] > 0.3]
        X = features_v1(d) if v1 else features_v2(d)
        Y = d[:, 15:17].copy()
        if engaged_only:
            att = d[:, 14] > 0.5
            keep = np.zeros(len(d), bool)
            for i in np.where(att)[0]:
                keep[max(0, i - 8):min(len(d), i + 4)] = True
            X, Y = X[keep], Y[keep]
        X = clip_features(X, v1)
        Y[:, 0] = np.clip(Y[:, 0], -60, 60); Y[:, 1] = np.clip(Y[:, 1], -20, 20)
        Xs.append(X); Ys.append(Y)
    return np.vstack(Xs), np.vstack(Ys)


def main():
    out = sys.argv[1]
    paths = [a for a in sys.argv[2:] if not a.startswith('--')]
    v1 = '--v1' in sys.argv
    hid = 48 if v1 else 96
    segs = load_segments(paths)
    X, Y = build(segs, v1, '--all' not in sys.argv)
    print("rows", len(X), "features", X.shape[1], "hidden", hid)
    n = len(X); cut = int(n * 0.88)
    Xtr, Ytr, Xva, Yva = X[:cut], Y[:cut], X[cut:], Y[cut:]       # the end of the recordings is the validation
    mx, my = mirror(Xtr, Ytr, v1)
    Xtr, Ytr = np.vstack([Xtr, mx]), np.vstack([Ytr, my])
    mx, my = mirror(Xva, Yva, v1)
    Xva, Yva = np.vstack([Xva, mx]), np.vstack([Yva, my])
    mean = Xtr.mean(0); std = Xtr.std(0) + 1e-6
    ysc = np.array([9.0, 2.5])
    best = None
    for seed in (1, 2, 3):
        net, nll = train((Xtr - mean) / std, Ytr / ysc, (Xva - mean) / std, Yva / ysc, hid, seed=seed, verbose=(seed == 1))
        print(f"seed {seed}: best val nll {nll:.4f}")
        if best is None or nll < best[0]:
            best = (nll, net)
    nll, net = best
    print(f"FINAL val nll {nll:.4f}")
    model = {"mean": mean.tolist(), "std": std.tolist(), "ysc": ysc.tolist(), "hid": hid, "feats": X.shape[1]}
    for k in net.params:
        model[k] = getattr(net, k).tolist()
    json.dump(model, open(out, 'w'))
    print("saved", out)


if __name__ == '__main__':
    main()
