#!/usr/bin/env python3
"""Keybd tuş seslerini sentezler (lisans gerektirmeyen, tamamen üretilmiş sesler).

Kullanım:  python3 tools/gen_sounds.py
Çıktı:     app/src/main/assets/sounds/<paket>_<tür>_<n>.wav   (22.05 kHz, 16 bit, mono)
Türler:    key (birkaç çeşit), space, enter, del
"""
import os
import wave

import numpy as np

SR = 22050
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "sounds")
rng = np.random.default_rng(42)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack=0.001, decay=0.03):
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-6), 0, 1)
    return a * np.exp(-x / decay)


def noise(sec):
    return rng.uniform(-1, 1, int(SR * sec))


def lowpass(x, alpha):
    y = np.zeros_like(x)
    a = np.broadcast_to(np.asarray(alpha, dtype=np.float64), x.shape)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a[i] * (v - acc)
        y[i] = acc
    return y


def highpass(x, alpha):
    return x - lowpass(x, alpha)


def resonator(x, freq, q=30.0):
    """İki kutuplu rezonans filtresi (metalik tını için)."""
    w = 2 * np.pi * freq / SR
    r = 1 - w / (2 * q)
    a1, a2 = -2 * r * np.cos(w), r * r
    y = np.zeros_like(x)
    y1 = y2 = 0.0
    for i, v in enumerate(x):
        y0 = v - a1 * y1 - a2 * y2
        y[i] = y0
        y2, y1 = y1, y0
    return y / (np.max(np.abs(y)) + 1e-9)


def sine(freq, sec, phase=0.0):
    return np.sin(2 * np.pi * freq * t(sec) + phase)


def sweep(f0, f1, sec, curve=1.0):
    tt = t(sec)
    f = f0 + (f1 - f0) * (tt / sec) ** curve
    return np.sin(2 * np.pi * np.cumsum(f) / SR)


def pad(x, sec):
    n = int(SR * sec)
    return np.concatenate([x, np.zeros(max(0, n - len(x)))])[: max(n, len(x))]


def mix(*parts):
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def save(name, x, gain=0.9):
    x = np.asarray(x, dtype=np.float64)
    fade = min(len(x), int(SR * 0.004))
    if fade > 0:
        x[-fade:] *= np.linspace(1, 0, fade)
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    data = (x * 32767).astype("<i2").tobytes()
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


# ---------------------------------------------------------------- mekanik (clicky / mavi switch)
def mech_click(pitch=1.0, body=1.0):
    click = highpass(noise(0.006), 0.6) * env(int(SR * 0.006), 0.0002, 0.0015) * 1.2
    tick = resonator(noise(0.02), 4200 * pitch, 18) * env(int(SR * 0.02), 0.0003, 0.004) * 0.7
    bottom = lowpass(noise(0.05), 0.08) * env(int(SR * 0.05), 0.001, 0.012) * 1.4 * body
    thump = sine(180 * pitch, 0.05) * env(int(SR * 0.05), 0.001, 0.01) * 0.6 * body
    return pad(mix(click, tick, np.concatenate([np.zeros(int(SR * 0.008)), bottom]), np.concatenate([np.zeros(int(SR * 0.008)), thump])), 0.07)


# ---------------------------------------------------------------- mekanik (thock / derin)
def mech_thock(pitch=1.0, big=1.0):
    hit = lowpass(noise(0.08), 0.12) * env(int(SR * 0.08), 0.0008, 0.016 * big)
    body = sine(120 * pitch, 0.08) * env(int(SR * 0.08), 0.001, 0.02 * big) * 0.9
    knock = resonator(noise(0.04), 700 * pitch, 8) * env(int(SR * 0.04), 0.0005, 0.006) * 0.5
    return pad(mix(hit, body, knock), 0.1)


# ---------------------------------------------------------------- daktilo
def typewriter_key(pitch=1.0):
    strike = highpass(noise(0.01), 0.5) * env(int(SR * 0.01), 0.0001, 0.002) * 1.5
    metal = (resonator(noise(0.09), 2600 * pitch, 60) * 0.6 + resonator(noise(0.09), 3900 * pitch, 80) * 0.4) * env(int(SR * 0.09), 0.0002, 0.02)
    clunk = lowpass(noise(0.06), 0.1) * env(int(SR * 0.06), 0.001, 0.01) * 1.2
    return pad(mix(strike, metal, np.concatenate([np.zeros(int(SR * 0.012)), clunk])), 0.12)


def typewriter_bell():
    tt = t(1.0)
    bell = (np.sin(2 * np.pi * 2093 * tt) + 0.5 * np.sin(2 * np.pi * 4186 * tt * 1.002) + 0.3 * np.sin(2 * np.pi * 5600 * tt)) * np.exp(-tt / 0.35)
    carriage = lowpass(noise(0.35), 0.25) * np.linspace(0.2, 1, int(SR * 0.35)) * np.exp(-np.linspace(0, 3, int(SR * 0.35))) * 0.5
    return mix(typewriter_key(0.9) * 0.8, np.concatenate([np.zeros(int(SR * 0.05)), bell * 0.7]), np.concatenate([np.zeros(int(SR * 0.25)), carriage]))


# ---------------------------------------------------------------- yumuşak
def soft_tap(pitch=1.0):
    tap = lowpass(noise(0.03), 0.05) * env(int(SR * 0.03), 0.002, 0.007)
    tone = sine(320 * pitch, 0.04) * env(int(SR * 0.04), 0.002, 0.008) * 0.35
    return pad(mix(tap, tone), 0.05)


# ---------------------------------------------------------------- baloncuk
def bubble(f0=380, f1=1150, sec=0.05):
    return sweep(f0, f1, sec, 1.6) * env(int(SR * sec), 0.002, sec / 2.5)


# ---------------------------------------------------------------- piyano (pentatonik notalar)
def piano(freq, sec=0.45):
    tt = t(sec)
    x = sum(np.sin(2 * np.pi * freq * h * tt) / (h * h) for h in range(1, 6)) * np.exp(-tt / 0.18)
    return x * np.clip(tt / 0.003, 0, 1)


# ---------------------------------------------------------------- komik sesler 🤡
def boing():
    tt = t(0.45)
    f = 180 + 220 * np.exp(-tt / 0.15) * (1 + 0.6 * np.sin(2 * np.pi * 14 * tt))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.18)


def quack():
    tt = t(0.16)
    f = 520 - 160 * tt / 0.16
    saw = 2 * ((np.cumsum(f) / SR) % 1) - 1
    return resonator(saw, 1100, 6) * 0.6 * np.minimum(1, tt / 0.01) * np.exp(-tt / 0.08) + lowpass(saw, 0.3) * 0.4 * np.exp(-tt / 0.06)


def slide_whistle(up=True):
    return sweep(500, 1900, 0.35, 0.8) * env(int(SR * 0.35), 0.02, 0.25) if up else sweep(1900, 400, 0.4, 1.2) * env(int(SR * 0.4), 0.01, 0.3)


def pew():
    return sweep(2200, 180, 0.18, 0.5) * env(int(SR * 0.18), 0.001, 0.07)


def squeak():
    tt = t(0.12)
    f = 1800 + 900 * np.sin(np.pi * tt / 0.12)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / 0.12) ** 0.5


def honk():
    tt = t(0.22)
    f = 330 + 8 * np.sin(2 * np.pi * 30 * tt)
    sq = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR))
    return lowpass(sq, 0.25) * np.minimum(1, tt / 0.01) * np.exp(-tt / 0.15)


def blip(freq):
    return sweep(freq, freq * 1.5, 0.07) * env(int(SR * 0.07), 0.001, 0.03)


def wahwah():
    parts = []
    for f in (392, 370, 349, 330):
        tt = t(0.22)
        tone = np.sign(np.sin(2 * np.pi * f * tt)) * 0.5 + np.sin(2 * np.pi * f * tt)
        parts.append(lowpass(tone, 0.2 + 0.15 * np.sin(np.pi * tt / 0.22)) * np.sin(np.pi * tt / 0.22))
    return np.concatenate(parts)


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".wav"):
            os.remove(os.path.join(OUT, f))

    for i, p in enumerate((1.0, 0.94, 1.06, 0.98)):
        save(f"mech_key_{i}", mech_click(p))
    save("mech_space_0", mech_click(0.75, 1.6))
    save("mech_enter_0", mech_click(0.85, 1.4))
    save("mech_del_0", mech_click(0.9, 1.2))

    for i, p in enumerate((1.0, 0.93, 1.07, 0.97)):
        save(f"thock_key_{i}", mech_thock(p))
    save("thock_space_0", mech_thock(0.7, 1.6))
    save("thock_enter_0", mech_thock(0.8, 1.4))
    save("thock_del_0", mech_thock(0.9, 1.2))

    for i, p in enumerate((1.0, 0.95, 1.05)):
        save(f"typewriter_key_{i}", typewriter_key(p))
    save("typewriter_space_0", mech_thock(0.8, 1.2) * 0.8 + pad(typewriter_key(0.7), 0.1)[: int(SR * 0.1)] * 0.3)
    save("typewriter_enter_0", typewriter_bell())
    save("typewriter_del_0", typewriter_key(0.85))

    for i, p in enumerate((1.0, 0.9, 1.1)):
        save(f"soft_key_{i}", soft_tap(p), 0.6)
    save("soft_space_0", soft_tap(0.7), 0.6)
    save("soft_enter_0", soft_tap(0.6), 0.6)
    save("soft_del_0", soft_tap(0.8), 0.6)

    for i, (a, b) in enumerate(((380, 1150), (330, 1000), (430, 1300), (300, 900))):
        save(f"bubble_key_{i}", bubble(a, b), 0.7)
    save("bubble_space_0", bubble(220, 700, 0.07), 0.7)
    save("bubble_enter_0", bubble(260, 1600, 0.1), 0.7)
    save("bubble_del_0", bubble(900, 300, 0.06), 0.7)

    notes = (523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5)  # C majör pentatonik
    for i, f in enumerate(notes):
        save(f"piano_key_{i}", piano(f), 0.7)
    save("piano_space_0", piano(261.63, 0.6), 0.7)
    save("piano_enter_0", mix(piano(523.25, 0.8), piano(659.25, 0.8), piano(783.99, 0.8)), 0.7)
    save("piano_del_0", piano(392.0, 0.35), 0.7)

    funny = [boing(), quack(), squeak(), pew(), honk(), blip(600), blip(900), slide_whistle(True)]
    for i, x in enumerate(funny):
        save(f"funny_key_{i}", x, 0.75)
    save("funny_space_0", boing(), 0.75)
    save("funny_enter_0", wahwah(), 0.75)
    save("funny_del_0", slide_whistle(False), 0.75)

    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(os.listdir(OUT))} ses, toplam {total // 1024} KB")


if __name__ == "__main__":
    main()
