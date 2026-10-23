#!/usr/bin/env python3
"""Keybd tuş seslerini sentezler (lisans gerektirmeyen, tamamen üretilmiş sesler).

Kullanım:  python3 tools/gen_sounds.py
Çıktı:     app/src/main/assets/sounds/<paket>_<tür>_<n>.wav   (44.1 kHz, 16 bit, mono)
Türler:    key (birkaç çeşit), space, enter, del

Mekanik sesler fiziksel olarak modellenir: tuş kapağının çarpması (kısa geniş bantlı
darbe), kapağın ve kasanın rezonans modları (sönümlenen sinüsler), dibe vurma "thock"u,
clicky switch'lerde tık mekanizması, yay tınısı, tuşun geri kalkma sesi ve kısa oda yankısı.
"""
import os
import wave

import numpy as np

SR = 44100
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "sounds")
rng = np.random.default_rng(7)


# ---------------------------------------------------------------- temel araçlar
def n_(sec):
    return int(SR * sec)


def t(sec):
    return np.arange(n_(sec)) / SR


def noise(sec):
    return rng.standard_normal(n_(sec))


def onepole_lp(x, fc):
    """Tek kutuplu alçak geçiren (fc Hz)."""
    a = np.exp(-2 * np.pi * fc / SR)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc = (1 - a) * x[i] + a * acc
        y[i] = acc
    return y


def lp2(x, fc, order=3):
    """Art arda tek kutuplu filtreler: daha dik (order×6 dB/oktav) alçak geçiren."""
    for _ in range(order):
        x = onepole_lp(x, fc)
    return x


def hp(x, fc):
    return x - onepole_lp(x, fc)


def bandnoise(sec, lo, hi):
    return onepole_lp(hp(noise(sec), lo), hi)


def modes(sec, spec, jitter=0.0):
    """Sönümlenen sinüs modları: spec = [(frekans, sönüm_sn, genlik), ...]"""
    tt = t(sec)
    out = np.zeros_like(tt)
    for f, d, a in spec:
        f *= 1 + rng.uniform(-jitter, jitter)
        out += a * np.sin(2 * np.pi * f * tt + rng.uniform(0, 2 * np.pi)) * np.exp(-tt / d)
    return out * np.clip(tt / 0.0003, 0, 1)


def env(sec, attack, decay):
    tt = t(sec)
    return np.clip(tt / max(attack, 1e-5), 0, 1) * np.exp(-tt / decay)


def place(buf, x, at):
    i = n_(at)
    end = min(len(buf), i + len(x))
    buf[i:end] += x[: end - i]
    return buf


def room(x, amount=0.12, length=0.05):
    """Kısa oda yankısı: üstel sönen gürültü darbe yanıtıyla evrişim."""
    ir = noise(length) * np.exp(-t(length) / (length / 5))
    ir = lp2(ir, 4000, 2)
    ir /= np.max(np.abs(ir)) + 1e-9
    dry = np.concatenate([x, np.zeros(n_(length))])
    wet = np.convolve(x, ir)
    wet = np.concatenate([wet, np.zeros(max(0, len(dry) - len(wet)))])[: len(dry)]
    return dry + amount * wet / (np.max(np.abs(wet)) + 1e-9) * np.max(np.abs(x))


def save(name, x, gain=0.9):
    x = np.asarray(x, dtype=np.float64)
    x = x - np.mean(x)
    fade = min(len(x), n_(0.006))
    x[-fade:] *= np.linspace(1, 0, fade)
    x[: n_(0.0005)] *= np.linspace(0, 1, n_(0.0005))
    x = x / (np.max(np.abs(x)) + 1e-9) * gain
    data = (x * 32767).astype("<i2").tobytes()
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(data)


# ---------------------------------------------------------------- mekanik model
def keystroke(cap, thock, click=False, spring=0.025, release=0.35, bright=9000, size=1.0, dur=0.16, rev=0.12):
    """Gerçekçi tuş vuruşu.
    cap:   kapak/kasa modları [(f, decay, amp)]
    thock: dibe vurma modları [(f, decay, amp)]
    """
    buf = np.zeros(n_(dur))
    j = 0.04
    # 1) clicky: tık mekanizması dibe vurmadan ~5 ms önce
    t0 = 0.0
    if click:
        clk = hp(noise(0.0015), 2500) * env(0.0015, 0.0001, 0.0004) * 1.6
        clk_ring = modes(0.02, [(4300, 0.004, 0.6), (6100, 0.003, 0.35)], j)
        place(buf, clk, 0)
        place(buf, clk_ring, 0)
        t0 = 0.005
    # 2) dibe vurma: geniş bantlı darbe + modlar
    imp = lp2(noise(0.002), bright, 2) * env(0.002, 0.0001, 0.0005)
    place(buf, imp * 1.2, t0)
    place(buf, modes(dur - t0, [(f * size, d, a) for f, d, a in cap], j), t0)
    place(buf, modes(dur - t0, [(f * size, d, a) for f, d, a in thock], j) * 1.3, t0)
    place(buf, lp2(noise(0.03), 900 * size) * env(0.03, 0.0005, 0.006) * 2.0, t0)
    # 3) yay tınısı (çok hafif metalik)
    if spring > 0:
        place(buf, modes(0.05, [(rng.uniform(5500, 7500), 0.02, spring)]), t0 + 0.001)
    # 4) tuşun geri kalkması (daha zayıf ikinci darbe)
    if release > 0:
        up = t0 + rng.uniform(0.055, 0.075)
        rel = lp2(noise(0.0015), bright * 0.8, 2) * env(0.0015, 0.0001, 0.0004) * 0.6
        place(buf, rel * release, up)
        place(buf, modes(dur - up, [(f * size * 1.04, d * 0.7, a) for f, d, a in cap], j) * release * 0.6, up)
    return room(buf, rev)


def mech_clicky(size=1.0):
    return keystroke([(2100, 0.010, 0.5), (3600, 0.007, 0.35), (5200, 0.004, 0.2)],
                     [(420, 0.012, 0.6), (900, 0.008, 0.3)], click=True, size=size, bright=11000)


def mech_cream(size=1.0):
    return keystroke([(1150, 0.008, 0.25), (2300, 0.005, 0.12)],
                     [(230, 0.024, 1.0), (380, 0.016, 0.55), (620, 0.010, 0.25)],
                     click=False, spring=0.006, release=0.25, bright=2200, size=size, rev=0.14)


def laptop(size=1.0):
    return keystroke([(1500, 0.004, 0.5), (3100, 0.003, 0.3)], [(520, 0.006, 0.4)],
                     click=False, spring=0, release=0.2, bright=5000, size=size, dur=0.09, rev=0.05)


def phone_tick(pitch=1.0, dur=0.05):
    tick = lp2(hp(noise(0.002), 1500), 6000, 2) * env(0.002, 0.0001, 0.0006)
    ring = modes(dur, [(2600 * pitch, 0.003, 0.5), (1300 * pitch, 0.004, 0.25)])
    buf = np.zeros(n_(dur))
    place(buf, tick, 0)
    place(buf, ring, 0)
    return buf


def wood(size=1.0):
    buf = modes(0.18, [(720 * size, 0.035, 1.0), (1680 * size, 0.018, 0.5), (2950 * size, 0.010, 0.25)], 0.03)
    place(buf, onepole_lp(noise(0.004), 4000) * env(0.004, 0.0001, 0.001) * 0.6, 0)
    return room(buf, 0.1)


# ---------------------------------------------------------------- daktilo
def typewriter_key(size=1.0):
    buf = np.zeros(n_(0.2))
    place(buf, hp(noise(0.002), 2000) * env(0.002, 0.0001, 0.0005) * 1.8, 0)
    place(buf, modes(0.2, [(2450 * size, 0.06, 0.45), (3150 * size, 0.045, 0.35), (4700 * size, 0.03, 0.25), (6300 * size, 0.02, 0.15)], 0.03), 0)
    place(buf, onepole_lp(noise(0.05), 700) * env(0.05, 0.0005, 0.01) * 1.4, 0.006)   # harfin kâğıda vuruşu
    place(buf, modes(0.08, [(180, 0.02, 0.6)]), 0.006)
    return room(buf, 0.18, 0.08)


def typewriter_return():
    tt = t(1.1)
    bell = (np.sin(2 * np.pi * 2093 * tt) + 0.55 * np.sin(2 * np.pi * 4186 * 1.002 * tt)
            + 0.3 * np.sin(2 * np.pi * 5650 * tt)) * np.exp(-tt / 0.38)
    buf = np.zeros(n_(1.1))
    place(buf, typewriter_key(0.9) * 0.7, 0)
    place(buf, bell * 0.55, 0.03)
    for k in range(9):  # satır başı mandalı: art arda küçük tıklar
        place(buf, typewriter_key(1.2 + k * 0.02)[: n_(0.03)] * 0.25 * (1 - k / 12), 0.25 + k * 0.035)
    return buf


# ---------------------------------------------------------------- yumuşak / baloncuk / damla
def soft(size=1.0):
    tap = lp2(noise(0.05), 900 * size, 4) * env(0.05, 0.003, 0.008) * 6
    return room(tap + modes(0.05, [(300 * size, 0.01, 0.5), (620 * size, 0.006, 0.2)]), 0.08)


def bubble(f0, f1, sec=0.06):
    tt = t(sec)
    f = f0 + (f1 - f0) * (tt / sec) ** 1.6
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(sec, 0.002, sec / 2.5)


def drop(pitch=1.0):
    tt = t(0.09)
    f = (650 + 1100 * (1 - np.exp(-tt / 0.012))) * pitch
    blip = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.09, 0.001, 0.025)
    splash = bandnoise(0.02, 3000, 9000) * env(0.02, 0.0005, 0.004) * 0.25
    buf = np.zeros(n_(0.09))
    place(buf, blip, 0)
    place(buf, splash, 0)
    return room(buf, 0.15)


# ---------------------------------------------------------------- retro 8-bit
def square(freq, sec, duty=0.5):
    ph = (t(sec) * freq) % 1
    return np.where(ph < duty, 1.0, -1.0)


def retro(freq, sec=0.06, slide=1.0):
    tt = t(sec)
    f = freq * (1 + (slide - 1) * tt / sec)
    ph = np.cumsum(f) / SR % 1
    return lp2(np.where(ph < 0.5, 1.0, -1.0), 4500, 1) * env(sec, 0.001, sec / 2) * 0.6


# ---------------------------------------------------------------- piyano
def piano(freq, sec=0.5):
    tt = t(sec)
    x = sum(np.sin(2 * np.pi * freq * h * (1 + 0.0004 * h * h) * tt) / (h ** 1.6) * np.exp(-tt / (0.25 / h ** 0.5)) for h in range(1, 8))
    hammer = onepole_lp(noise(0.004), 3000) * env(0.004, 0.0001, 0.001) * 0.15
    x[: len(hammer)] += hammer
    return room(x * np.clip(tt / 0.002, 0, 1), 0.2, 0.1)


# ---------------------------------------------------------------- komik 🤡
def boing():
    tt = t(0.45)
    f = 180 + 220 * np.exp(-tt / 0.15) * (1 + 0.6 * np.sin(2 * np.pi * 14 * tt))
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.exp(-tt / 0.18)


def quack():
    tt = t(0.16)
    f = 520 - 160 * tt / 0.16
    saw = 2 * ((np.cumsum(f) / SR) % 1) - 1
    nasal = modes(0.16, [(1100, 0.05, 1.0), (2300, 0.03, 0.5)])
    return (onepole_lp(saw, 1800) * 0.6 + saw * nasal * 0.4) * np.minimum(1, tt / 0.01) * np.exp(-tt / 0.08)


def slide_whistle(up=True):
    sec = 0.35 if up else 0.4
    tt = t(sec)
    f = (500 + 1400 * (tt / sec) ** 0.8) if up else (1900 - 1500 * (tt / sec) ** 1.2)
    vib = 1 + 0.01 * np.sin(2 * np.pi * 6 * tt)
    return np.sin(2 * np.pi * np.cumsum(f * vib) / SR) * env(sec, 0.02, sec * 0.7)


def pew():
    tt = t(0.18)
    f = 2200 * np.exp(-tt / 0.05) + 180
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * env(0.18, 0.001, 0.07)


def squeak():
    tt = t(0.12)
    f = 1800 + 900 * np.sin(np.pi * tt / 0.12)
    return np.sin(2 * np.pi * np.cumsum(f) / SR) * np.sin(np.pi * tt / 0.12) ** 0.5


def honk():
    tt = t(0.22)
    f = 330 + 8 * np.sin(2 * np.pi * 30 * tt)
    sq = np.sign(np.sin(2 * np.pi * np.cumsum(f) / SR))
    return onepole_lp(sq, 1600) * np.minimum(1, tt / 0.01) * np.exp(-tt / 0.15)


def wahwah():
    parts = []
    for f in (392, 370, 349, 330):
        tt = t(0.22)
        tone = np.sign(np.sin(2 * np.pi * f * tt)) * 0.5 + np.sin(2 * np.pi * f * tt)
        parts.append(onepole_lp(tone, 1200) * np.sin(np.pi * tt / 0.22))
    return np.concatenate(parts)


# ---------------------------------------------------------------- venom 🖤 (ıslak, organik)
def squelch(f0=1600, f1=280, sec=0.09, low=90, wet=1.0):
    """Islak 'şlap' sesi: aşağı kayan dar bantlı gürültü + titreşen genlik + derin taban."""
    tt = t(sec)
    src = noise(sec)
    fc = f0 * (f1 / f0) ** (tt / sec)
    # zamanla değişen rezonans: kısa pencerelerde bant geçiren
    out = np.zeros_like(src)
    win = n_(0.004)
    for s in range(0, len(src), win):
        seg = src[s:s + win + n_(0.002)]
        f = fc[min(s, len(fc) - 1)]
        out[s:s + len(seg)] += lp2(hp(seg, f * 0.6), f * 1.3, 3)[: len(out) - s]
    wobble = 1 + 0.5 * np.sin(2 * np.pi * rng.uniform(28, 40) * tt)
    body = np.sin(2 * np.pi * low * tt * (1 - 0.3 * tt / sec)) * np.exp(-tt / (sec / 2.5)) * 1.1
    x = out / (np.max(np.abs(out)) + 1e-9) * env(sec, 0.002, sec / 3) * wobble * wet + body
    return room(x, 0.1, 0.04)


def growl(sec=0.32):
    tt = t(sec)
    f = 68 * (1 + 0.06 * np.sin(2 * np.pi * 9 * tt)) * (1 - 0.15 * tt / sec)
    saw = 2 * ((np.cumsum(f) / SR) % 1) - 1
    rough = 1 + 0.6 * bandnoise(sec, 20, 60) / 3
    formant = onepole_lp(saw * rough, 700) + 0.4 * modes(sec, [(420, sec / 2, 1.0), (1100, sec / 3, 0.5)]) * saw
    return room(formant * env(sec, 0.03, sec * 0.6), 0.15, 0.08)


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith(".wav"):
            os.remove(os.path.join(OUT, f))

    def pack(name, key_fn, sizes, space, enter, dele, gain=0.9):
        for i, s in enumerate(sizes):
            save(f"{name}_key_{i}", key_fn(s), gain)
        save(f"{name}_space_0", space, gain)
        save(f"{name}_enter_0", enter, gain)
        save(f"{name}_del_0", dele, gain)

    sz = (1.0, 0.96, 1.04, 0.98, 1.02, 0.94)
    pack("mech", mech_clicky, sz, mech_clicky(0.8), mech_clicky(0.88), mech_clicky(0.92))
    pack("thock", mech_cream, sz, keystroke([(900, 0.01, 0.2)], [(170, 0.035, 1.0), (300, 0.02, 0.5)], spring=0.01, release=0.3, bright=3500, dur=0.2),
         mech_cream(0.85), mech_cream(0.92))
    pack("laptop", laptop, sz[:4], laptop(0.85), laptop(0.9), laptop(0.95), 0.8)
    pack("phone", lambda s: phone_tick(s), (1.0, 0.98, 1.02), phone_tick(0.8), phone_tick(0.7, 0.06), phone_tick(0.6, 0.06), 0.7)
    pack("wood", wood, sz[:4], wood(0.75), wood(0.6), wood(0.85), 0.8)
    pack("typewriter", typewriter_key, (1.0, 0.95, 1.05, 0.98), typewriter_key(0.8) * 0.9, typewriter_return(), typewriter_key(0.88))
    pack("soft", soft, (1.0, 0.9, 1.1), soft(0.7), soft(0.6), soft(0.8), 0.6)
    pack("bubble", lambda s: bubble(380 * s, 1150 * s), (1.0, 0.88, 1.12, 0.8), bubble(220, 700, 0.08), bubble(260, 1600, 0.11), bubble(900, 300, 0.07), 0.7)
    pack("drop", drop, (1.0, 0.9, 1.1, 0.95, 1.05), drop(0.7), drop(0.6), drop(1.3), 0.75)
    notes = (523.25, 587.33, 659.25, 783.99, 880.0, 1046.5, 1174.66, 1318.5)
    for i, f in enumerate(notes):
        save(f"piano_key_{i}", piano(f), 0.7)
    save("piano_space_0", piano(261.63, 0.7), 0.7)
    save("piano_enter_0", piano(523.25, 0.9) + piano(659.25, 0.9) + piano(783.99, 0.9), 0.7)
    save("piano_del_0", piano(392.0, 0.4), 0.7)
    for i, f in enumerate((660, 784, 880, 988, 1047, 1175)):
        save(f"retro_key_{i}", retro(f, 0.05, 1.08), 0.6)
    save("retro_space_0", retro(330, 0.07, 0.9), 0.6)
    save("retro_enter_0", np.concatenate([retro(523, 0.05), retro(659, 0.05), retro(784, 0.05), retro(1047, 0.09)]), 0.6)
    save("retro_del_0", retro(880, 0.08, 0.5), 0.6)
    funny = [boing(), quack(), squeak(), pew(), honk(), retro(600, 0.07, 1.5), retro(900, 0.07, 1.5), slide_whistle(True)]
    for i, x in enumerate(funny):
        save(f"funny_key_{i}", x, 0.75)
    save("funny_space_0", boing(), 0.75)
    save("funny_enter_0", wahwah(), 0.75)
    save("funny_del_0", slide_whistle(False), 0.75)
    for i, (a, b, s) in enumerate(((1700, 300, 0.085), (1500, 260, 0.09), (1900, 340, 0.08), (1600, 280, 0.095), (1800, 320, 0.088))):
        save(f"venom_key_{i}", squelch(a, b, s), 0.85)
    save("venom_space_0", squelch(1100, 160, 0.14, 60), 0.85)
    save("venom_enter_0", growl(), 0.9)
    save("venom_del_0", squelch(300, 1500, 0.1, 120), 0.85)  # tersine "şlürp"

    total = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    print(f"{len(os.listdir(OUT))} ses, toplam {total // 1024} KB")


if __name__ == "__main__":
    main()
