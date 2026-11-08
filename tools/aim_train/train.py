import sys, json, glob
import numpy as np

FEATS = 14
HID = 48

def load(paths):
    rows = []
    for p in paths:
        d = np.genfromtxt(p, delimiter=',', skip_header=1)
        if d.ndim == 2 and d.shape[1] == 17:
            rows.append(d)
    return np.vstack(rows)

def mirror(d):
    m = d.copy()
    for i in (0, 4, 7, 8, 10, 15):      # yaw-signed columns
        m[:, i] = -m[:, i]
    return m

def prep(d):
    X = d[:, :FEATS].copy()
    Y = d[:, 15:17].copy()
    X[:, 0] = np.clip(X[:, 0], -180, 180)
    X[:, 1] = np.clip(X[:, 1], -90, 90)
    for i in (8, 10): X[:, i] = np.clip(X[:, i], -60, 60)
    for i in (9, 11): X[:, i] = np.clip(X[:, i], -20, 20)
    Y[:, 0] = np.clip(Y[:, 0], -60, 60)
    Y[:, 1] = np.clip(Y[:, 1], -20, 20)
    return X, Y

class Net:
    def __init__(self, rng):
        self.W1 = rng.normal(0, 1/np.sqrt(FEATS), (FEATS, HID)); self.b1 = np.zeros(HID)
        self.W2 = rng.normal(0, 1/np.sqrt(HID), (HID, HID)); self.b2 = np.zeros(HID)
        self.W3 = rng.normal(0, 0.1/np.sqrt(HID), (HID, 4)); self.b3 = np.zeros(4)
        self.params = ['W1','b1','W2','b2','W3','b3']
    def forward(self, x):
        h1 = np.tanh(x @ self.W1 + self.b1)
        h2 = np.tanh(h1 @ self.W2 + self.b2)
        o = h2 @ self.W3 + self.b3
        return o, (x, h1, h2)

def train(Xtr, Ytr, Xva, Yva, epochs=150, seed=1):
    rng = np.random.default_rng(seed)
    net = Net(rng)
    m = {k: np.zeros_like(getattr(net, k)) for k in net.params}
    v = {k: np.zeros_like(getattr(net, k)) for k in net.params}
    t = 0; best = (1e9, None)
    n = len(Xtr)
    for ep in range(epochs):
        idx = rng.permutation(n)
        for s in range(0, n, 256):
            b = idx[s:s+256]
            x, y = Xtr[b], Ytr[b]
            o, (x_, h1, h2) = net.forward(x)
            mu = o[:, :2]; ls = np.clip(o[:, 2:], -4, 2)
            inv = np.exp(-2*ls)
            diff = mu - y
            # NLL = ls + 0.5*diff^2*exp(-2 ls)
            go_mu = diff*inv/len(b)
            go_ls = (1 - diff**2*inv)/len(b)
            go = np.concatenate([go_mu, go_ls], 1)
            g = {}
            g['W3'] = h2.T @ go; g['b3'] = go.sum(0)
            gh2 = (go @ net.W3.T) * (1 - h2**2)
            g['W2'] = h1.T @ gh2; g['b2'] = gh2.sum(0)
            gh1 = (gh2 @ net.W2.T) * (1 - h1**2)
            g['W1'] = x_.T @ gh1; g['b1'] = gh1.sum(0)
            t += 1
            lr = 2e-3 * (0.3 if ep > epochs*0.6 else 1.0)
            for k in net.params:
                m[k] = 0.9*m[k] + 0.1*g[k]; v[k] = 0.999*v[k] + 0.001*g[k]**2
                mh = m[k]/(1-0.9**t); vh = v[k]/(1-0.999**t)
                setattr(net, k, getattr(net, k) - lr*mh/(np.sqrt(vh)+1e-8))
        if ep % 10 == 9 or ep == epochs-1:
            o, _ = net.forward(Xva); mu = o[:, :2]; ls = np.clip(o[:, 2:], -4, 2)
            nll = np.mean(ls + 0.5*(mu-Yva)**2*np.exp(-2*ls))
            mse = np.mean((mu-Yva)**2, 0)
            print(f"ep {ep+1:3d} val nll {nll:.4f} mse yaw {mse[0]:.3f} pitch {mse[1]:.3f}")
            if nll < best[0]:
                best = (nll, {k: getattr(net, k).copy() for k in net.params})
    for k, val in best[1].items(): setattr(net, k, val)
    return net

if __name__ == '__main__':
    paths = [a for a in sys.argv[2:] if not a.startswith('--')]
    out = sys.argv[1]
    d = load(paths)
    # drop rows where the player stands inside the target / garbage
    d = d[(d[:, 2] > 0.3)]
    if '--all' not in sys.argv:
        # keep the engaged parts of fights: from 8 ticks before to 4 ticks after each swing
        att = d[:, 14] > 0.5
        keep = np.zeros(len(d), bool)
        for i in np.where(att)[0]:
            keep[max(0, i-8):min(len(d), i+4)] = True
        d = d[keep]
    print("rows", len(d))
    n = len(d); cut = int(n*0.9)
    tr, va = d[:cut], d[cut:]
    Xtr, Ytr = prep(np.vstack([tr, mirror(tr)]))
    Xva, Yva = prep(np.vstack([va, mirror(va)]))
    mean = Xtr.mean(0); std = Xtr.std(0) + 1e-6
    ysc = np.array([9.0, 2.5])
    net = train((Xtr-mean)/std, Ytr/ysc, (Xva-mean)/std, Yva/ysc)
    model = {"mean": mean.tolist(), "std": std.tolist(), "ysc": ysc.tolist(), "hid": HID, "feats": FEATS,
             "W1": net.W1.tolist(), "b1": net.b1.tolist(), "W2": net.W2.tolist(), "b2": net.b2.tolist(),
             "W3": net.W3.tolist(), "b3": net.b3.tolist()}
    json.dump(model, open(out, 'w'))
    print("saved", out)
