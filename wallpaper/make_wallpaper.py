"""Procedural 4K wallpaper: savanna dusk with layered ridges and acacia silhouettes.
Top-left and bottom are kept calm/dark so the launcher's widgets stay legible."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 3840, 2160
rng = np.random.default_rng(254)  # Kenya's dialling code, why not
out = sys.argv[1]

y = np.linspace(0, 1, H)[:, None]
x = np.linspace(0, 1, W)[None, :]

def lerp(a, b, t):
    return a + (b - a) * t

# --- Sky: indigo -> violet -> ember, horizon around 68% height
stops = [
    (0.00, (14, 16, 38)),
    (0.30, (34, 30, 72)),
    (0.52, (98, 58, 104)),
    (0.63, (196, 104, 92)),
    (0.70, (242, 168, 102)),
    (1.00, (250, 196, 128)),
]
sky = np.zeros((H, W, 3), np.float32)
yy = np.broadcast_to(y, (H, W))
for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
    m = (yy >= p0) & (yy <= p1)
    t = ((yy - p0) / (p1 - p0))[m]
    t = t * t * (3 - 2 * t)
    for ch in range(3):
        sky[..., ch][m] = lerp(c0[ch], c1[ch], t)

# Sun glow, low and right of centre
sx, sy = 0.66, 0.70
d = np.sqrt(((x - sx) * (W / H)) ** 2 + (y - sy) ** 2)
glow = np.exp(-(d / 0.20) ** 2)[..., None]
halo = np.exp(-(d / 0.55) ** 2)[..., None]
sky += glow * np.array([255, 190, 120]) * 0.55 + halo * np.array([255, 140, 110]) * 0.18
sun = np.clip(1 - (d - 0.034) / 0.004, 0, 1)[..., None]
sky = lerp(sky, np.array([255, 230, 190], np.float32), sun * 0.9)

# Darken top-left for the greeting widget
tl = np.exp(-(((x - 0.0) * 1.3) ** 2 + ((y - 0.0) * 1.6) ** 2) / 0.35)[..., None]
sky *= 1 - 0.35 * tl

# Faint high clouds
def fbm1(n, octaves, seed):
    r = np.random.default_rng(seed)
    out = np.zeros(n)
    for o in range(octaves):
        k = 2 ** o * 3
        pts = r.normal(size=k + 3)
        xs = np.linspace(0, k, n)
        out += np.interp(xs, np.arange(k + 3), pts) / (1.8 ** o)
    return out

img = Image.fromarray(np.clip(sky, 0, 255).astype(np.uint8))
cloud = Image.new("L", (W // 4, H // 4), 0)
cd = ImageDraw.Draw(cloud)
for _ in range(26):
    cx = rng.uniform(0.05, 1.0) * W / 4
    cy = rng.uniform(0.30, 0.56) * H / 4
    rw = rng.uniform(60, 220)
    rh = rw * rng.uniform(0.05, 0.11)
    cd.ellipse([cx - rw, cy - rh, cx + rw, cy + rh], fill=int(rng.uniform(40, 90)))
cloud = cloud.filter(ImageFilter.GaussianBlur(14)).resize((W, H), Image.BICUBIC)
ca = np.asarray(cloud, np.float32)[..., None] / 255.0
base = np.asarray(img, np.float32)
cloud_col = np.array([255, 160, 140], np.float32)
base = base * (1 - ca * 0.5) + cloud_col * ca * 0.5

# Faint stars in the upper sky, fading toward the horizon
star = Image.new("L", (W, H), 0)
sd = ImageDraw.Draw(star)
for _ in range(520):
    px, py = rng.uniform(0, W), rng.uniform(0, 0.42) ** 1.6 / 0.42 ** 0.6 * H
    r = rng.choice([1.0, 1.4, 2.0], p=[0.7, 0.22, 0.08])
    sd.ellipse([px - r, py - r, px + r, py + r], fill=int(rng.uniform(80, 230)))
star = star.filter(ImageFilter.GaussianBlur(0.8))
sa = np.asarray(star, np.float32)[..., None] / 255.0
sa *= np.clip(1 - y / 0.45, 0, 1)[..., None]
base = base + (255 - base) * sa * 0.8

# --- Ridges (far -> near), atmospheric perspective
SS = 2  # supersample masks for anti-aliasing
ridges = [
    # (baseline, amplitude, octaves, seed, colour, haze)
    (0.705, 0.040, 5, 11, (120, 70, 96)),
    (0.745, 0.050, 5, 23, (78, 44, 74)),
    (0.800, 0.045, 6, 37, (40, 24, 46)),
    (0.870, 0.035, 6, 41, (16, 11, 22)),
]
masks = []
for base_y, amp, octv, seed, col in ridges:
    prof = fbm1(W * SS, octv, seed)
    prof = (prof - prof.min()) / (np.ptp(prof) + 1e-6)
    top = (base_y - amp * prof) * H * SS
    m = Image.new("L", (W * SS, H * SS), 0)
    md = ImageDraw.Draw(m)
    pts = [(i, float(top[i])) for i in range(0, W * SS, 4)] + [(W * SS, H * SS), (0, H * SS)]
    md.polygon(pts, fill=255)
    masks.append((m, top, col))

def acacia(draw, x0, ground, s, rr):
    """Flat-topped umbrella acacia silhouette."""
    h = s * rr.uniform(0.9, 1.15)
    lean = rr.uniform(-0.12, 0.12) * h
    cx, cy = x0 + lean, ground - h
    tw = max(2, s * 0.035)
    # trunk forking into 3 limbs
    fork = ground - h * 0.42
    draw.polygon([(x0 - tw, ground), (x0 + tw, ground),
                  (x0 + lean * 0.4 + tw * 0.7, fork), (x0 + lean * 0.4 - tw * 0.7, fork)], fill=255)
    for dx in (-0.55, 0.05, 0.6):
        ex, ey = cx + dx * h * 0.75, cy + h * 0.10
        fx = x0 + lean * 0.4
        draw.line([(fx, fork), (ex, ey)], fill=255, width=int(tw * 1.1))
    # canopy: several flattened ellipses forming a wide flat top
    cw = h * rr.uniform(0.95, 1.25)
    for k in range(9):
        ox = rr.uniform(-1, 1) * cw * 0.85
        oy = rr.uniform(-0.04, 0.08) * h
        rw = cw * rr.uniform(0.25, 0.45)
        rh = h * rr.uniform(0.07, 0.12)
        draw.ellipse([cx + ox - rw, cy + oy - rh, cx + ox + rw, cy + oy + rh], fill=255)

# trees on ridge 3 (mid) small, ridge 4 (near) larger; avoid the centre-bottom dock zone visually
tree_specs = [(2, [0.12, 0.34, 0.83], 70), (3, [0.06, 0.93], 190)]
for ridx, xs_frac, size in tree_specs:
    m, top, _ = masks[ridx]
    md = ImageDraw.Draw(m)
    for xf in xs_frac:
        xi = int(xf * W * SS)
        acacia(md, xi, float(top[xi]) + 6 * SS, size * SS * rng.uniform(0.85, 1.2), rng)

for m, _, col in masks:
    a = np.asarray(m.resize((W, H), Image.LANCZOS), np.float32)[..., None] / 255.0
    base = base * (1 - a) + np.array(col, np.float32) * a

# A few birds high in the sky, right side
bird = Image.new("L", (W * SS, H * SS), 0)
bd = ImageDraw.Draw(bird)
for _ in range(5):
    bx = rng.uniform(0.70, 0.86) * W * SS
    by = rng.uniform(0.38, 0.48) * H * SS
    bs = rng.uniform(10, 18) * SS
    bd.line([(bx - bs, by - bs * 0.35), (bx, by)], fill=255, width=3 * SS)
    bd.line([(bx, by), (bx + bs, by - bs * 0.4)], fill=255, width=3 * SS)
ba = np.asarray(bird.resize((W, H), Image.LANCZOS), np.float32)[..., None] / 255.0
base = base * (1 - ba * 0.75) + np.array([30, 20, 40], np.float32) * ba * 0.75

# Vignette + bottom fade for the dock
v = np.sqrt(((x - 0.5) * 1.1) ** 2 + ((y - 0.48) * 1.0) ** 2)
base *= (1 - 0.35 * np.clip(v - 0.25, 0, 1))[..., None]

# Film grain to prevent banding on big panels
base += rng.normal(0, 2.2, (H, W, 1)).astype(np.float32)

Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).save(out, quality=94, subsampling=0, optimize=True)
print("saved", out)
