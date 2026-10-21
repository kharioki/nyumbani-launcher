"""More procedural 4K wallpapers for the bundled set (no photos, nothing copyrighted).

    python make_more_wallpapers.py <out_dir>

Writes night-ridge.jpg, dawn-mist.jpg and dusk-abstract.jpg. Like make_wallpaper.py, each keeps
the top-left (greeting) and bottom (dock) calm so the glass widgets stay legible.
"""
import os
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 3840, 2160
SS = 2  # supersample ridge masks for anti-aliasing

y = np.linspace(0, 1, H)[:, None]
x = np.linspace(0, 1, W)[None, :]
yy = np.broadcast_to(y, (H, W))


def gradient(stops):
    """Vertical colour gradient through (position, rgb) stops, smoothstepped between them."""
    img = np.zeros((H, W, 3), np.float32)
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        m = (yy >= p0) & (yy <= p1)
        t = ((yy - p0) / (p1 - p0))[m]
        t = t * t * (3 - 2 * t)
        for ch in range(3):
            img[..., ch][m] = c0[ch] + (c1[ch] - c0[ch]) * t
    return img


def fbm1(n, octaves, seed):
    r = np.random.default_rng(seed)
    out = np.zeros(n)
    for o in range(octaves):
        k = 2 ** o * 3
        pts = r.normal(size=k + 3)
        xs = np.linspace(0, k, n)
        out += np.interp(xs, np.arange(k + 3), pts) / (1.8 ** o)
    return out


def ridge_alpha(base_y, amp, octaves, seed, smooth=1):
    """Alpha mask (H, W, 1) of the land below a fractal ridge line."""
    prof = fbm1(W * SS, octaves, seed)
    if smooth > 1:
        prof = np.convolve(prof, np.ones(smooth) / smooth, mode="same")
    prof = (prof - prof.min()) / (np.ptp(prof) + 1e-6)
    top = (base_y - amp * prof) * H * SS
    m = Image.new("L", (W * SS, H * SS), 0)
    pts = [(i, float(top[i])) for i in range(0, W * SS, 4)] + [(W * SS, H * SS), (0, H * SS)]
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return np.asarray(m.resize((W, H), Image.LANCZOS), np.float32)[..., None] / 255.0


def stars(rng, count, top_frac, fade_to):
    s = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(s)
    for _ in range(count):
        px, py = rng.uniform(0, W), rng.uniform(0, top_frac) ** 1.4 / top_frac ** 0.4 * H
        r = rng.choice([1.0, 1.4, 2.0, 2.6], p=[0.62, 0.25, 0.1, 0.03])
        d.ellipse([px - r, py - r, px + r, py + r], fill=int(rng.uniform(70, 235)))
    a = np.asarray(s.filter(ImageFilter.GaussianBlur(0.8)), np.float32)[..., None] / 255.0
    return a * np.clip(1 - y / fade_to, 0, 1)[..., None]


def finish(base, rng, out, grain=1.8, vignette=0.32):
    v = np.sqrt(((x - 0.5) * 1.1) ** 2 + ((y - 0.48) * 1.0) ** 2)
    base = base * (1 - vignette * np.clip(v - 0.25, 0, 1))[..., None]
    base = base + rng.normal(0, grain, (H, W, 1)).astype(np.float32)
    Image.fromarray(np.clip(base, 0, 255).astype(np.uint8)).save(out, quality=92, subsampling=0, optimize=True)
    print("saved", out, os.path.getsize(out) // 1024, "KB")


def night_ridge(out):
    """Deep-blue night: stars, a faint galaxy band, a crescent moon and layered blue ridges."""
    rng = np.random.default_rng(2160)
    base = gradient([
        (0.00, (6, 9, 26)),
        (0.35, (12, 22, 54)),
        (0.62, (26, 48, 92)),
        (0.75, (44, 72, 120)),
        (1.00, (20, 32, 60)),
    ])
    # Galaxy band: a soft diagonal streak from upper right toward the horizon.
    band_d = np.abs((y - 0.10) - (1 - x) * 0.55) / 0.11
    band = np.exp(-band_d ** 2)[..., None] * np.clip(1 - y / 0.7, 0, 1)[..., None]
    base += band * np.array([70, 80, 120], np.float32) * 0.45
    base = base + (255 - base) * stars(rng, 900, 0.62, 0.72) * 0.85

    # Crescent moon, upper right, with a wide soft halo.
    mx, my, mr = 0.78, 0.22, 0.045
    d = np.sqrt(((x - mx) * (W / H)) ** 2 + (y - my) ** 2)
    base += np.exp(-(d / 0.25) ** 2)[..., None] * np.array([90, 110, 150], np.float32) * 0.35
    disc = np.clip((mr - d) / 0.003, 0, 1)
    d2 = np.sqrt(((x - mx - 0.018) * (W / H)) ** 2 + (y - my + 0.012) ** 2)
    shadow = np.clip((mr * 0.96 - d2) / 0.003, 0, 1)
    moon = (disc * (1 - shadow))[..., None]
    base = base * (1 - moon) + np.array([236, 238, 226], np.float32) * moon

    for base_y, amp, octv, seed, col in [
        (0.66, 0.10, 5, 3, (38, 58, 98)),
        (0.74, 0.09, 5, 5, (24, 38, 72)),
        (0.83, 0.07, 6, 8, (13, 22, 46)),
        (0.91, 0.05, 6, 13, (6, 10, 24)),
    ]:
        a = ridge_alpha(base_y, amp, octv, seed)
        base = base * (1 - a) + np.array(col, np.float32) * a
        # Thin mist sitting in each valley.
        mist = np.exp(-((y - base_y + 0.01) / 0.025) ** 2)[..., None] * (1 - a) * 0.12
        base = base + (np.array([120, 150, 200], np.float32) - base) * mist
    finish(base, rng, out)


def dawn_mist(out):
    """Pale dawn over rolling hills, with bands of mist between them."""
    rng = np.random.default_rng(530)
    base = gradient([
        (0.00, (58, 66, 104)),
        (0.30, (118, 112, 150)),
        (0.52, (206, 160, 162)),
        (0.62, (246, 200, 170)),
        (1.00, (236, 206, 186)),
    ])
    sx, sy = 0.70, 0.58
    d = np.sqrt(((x - sx) * (W / H)) ** 2 + (y - sy) ** 2)
    base += np.exp(-(d / 0.16) ** 2)[..., None] * np.array([255, 220, 170], np.float32) * 0.45
    base += np.exp(-(d / 0.6) ** 2)[..., None] * np.array([255, 190, 160], np.float32) * 0.12
    # Calm the top-left corner for the greeting widget.
    tl = np.exp(-(((x * 1.3) ** 2) + ((y * 1.6) ** 2)) / 0.35)[..., None]
    base *= 1 - 0.30 * tl

    hills = [
        (0.62, 0.05, 4, 21, (176, 142, 160)),
        (0.68, 0.06, 4, 34, (140, 112, 138)),
        (0.75, 0.06, 5, 55, (104, 84, 112)),
        (0.83, 0.05, 5, 89, (70, 56, 82)),
        (0.92, 0.04, 5, 144, (40, 32, 52)),
    ]
    for i, (base_y, amp, octv, seed, col) in enumerate(hills):
        a = ridge_alpha(base_y, amp, octv, seed, smooth=200)
        base = base * (1 - a) + np.array(col, np.float32) * a
        if i < len(hills) - 1:
            # Mist pooled just above the next hill: brighter for the far layers.
            fog = np.exp(-((y - base_y - 0.03) / 0.035) ** 2)[..., None] * (0.55 - i * 0.09)
            base = base + (np.array([250, 226, 214], np.float32) - base) * fog
    finish(base, rng, out, grain=1.6, vignette=0.28)


def dusk_abstract(out):
    """Dark abstract gradient: large soft colour fields on near-black."""
    rng = np.random.default_rng(64)
    base = np.zeros((H, W, 3), np.float32) + np.array([10, 10, 22], np.float32)
    small_w, small_h = W // 8, H // 8
    fields = [
        # (cx, cy, radius, colour, strength)
        (0.82, 0.30, 0.45, (40, 110, 140), 0.85),
        (0.60, 0.75, 0.40, (120, 40, 110), 0.70),
        (0.25, 0.55, 0.35, (60, 40, 140), 0.55),
        (0.95, 0.85, 0.30, (200, 110, 70), 0.45),
        (0.45, 0.20, 0.25, (30, 70, 120), 0.40),
    ]
    sx = np.linspace(0, 1, small_w)[None, :]
    sy = np.linspace(0, 1, small_h)[:, None]
    layer = np.zeros((small_h, small_w, 3), np.float32)
    for cx, cy, r, col, k in fields:
        d = np.sqrt(((sx - cx) * (W / H)) ** 2 + (sy - cy) ** 2)
        layer += np.exp(-(d / r) ** 2)[..., None] * np.array(col, np.float32) * k
    img = Image.fromarray(np.clip(layer, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(18))
    base += np.asarray(img.resize((W, H), Image.BICUBIC), np.float32)
    # Keep the corners where the widgets sit quiet.
    tl = np.exp(-(((x * 1.2) ** 2) + ((y * 1.5) ** 2)) / 0.30)[..., None]
    bottom = np.clip((y - 0.70) / 0.30, 0, 1)[..., None]
    base *= (1 - 0.45 * tl) * (1 - 0.35 * bottom)
    finish(base, rng, out, grain=2.0, vignette=0.25)


if __name__ == "__main__":
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out_dir, exist_ok=True)
    night_ridge(os.path.join(out_dir, "night-ridge.jpg"))
    dawn_mist(os.path.join(out_dir, "dawn-mist.jpg"))
    dusk_abstract(os.path.join(out_dir, "dusk-abstract.jpg"))
