"""Synthesise the sounds of the cabinets and the arcade's music, and list them in sounds.json.

    python tools/generate_sounds.py

Every machine has its own four sounds: a play starting, the tick of its moving part (reels, pegs,
crank, hooves), a win and a loss. The arcade music is a chiptune loop. Everything is computed from
fixed seeds, so running the script twice writes the same files. Needs numpy and soundfile.
"""
import json
from pathlib import Path

import numpy as np
import soundfile

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / 'platforms/fabric-1.21.1/src/main/resources/assets/gamblingitems'
RATE = 22050
RNG = np.random.default_rng(7)


def t(duration):
    return np.arange(int(RATE * duration)) / RATE


def env(length, attack=0.005, decay=0.2):
    """A percussive envelope: a quick attack, then an exponential fall."""
    time = np.arange(length) / RATE
    rise = np.clip(time / max(attack, 1e-4), 0, 1)
    return rise * np.exp(-time / max(decay, 1e-4))


def tone(freq, duration, wave='sine', decay=0.25, attack=0.004, vibrato=0.0):
    time = t(duration)
    phase = 2 * np.pi * np.cumsum(np.full(len(time), freq) * (1 + vibrato * np.sin(2 * np.pi * 6 * time))) / RATE
    if wave == 'square':
        signal = np.sign(np.sin(phase)) * 0.6
    elif wave == 'saw':
        signal = 2 * ((phase / (2 * np.pi)) % 1) - 1
    elif wave == 'triangle':
        signal = 2 * np.abs(2 * ((phase / (2 * np.pi)) % 1) - 1) - 1
    else:
        signal = np.sin(phase)
    return signal * env(len(time), attack, decay)


def sweep(f1, f2, duration, wave='sine', decay=1.0):
    time = t(duration)
    freq = f1 * (f2 / f1) ** (time / duration)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    signal = np.sin(phase) if wave == 'sine' else 2 * ((phase / (2 * np.pi)) % 1) - 1
    return signal * env(len(time), 0.01, decay)


def lowpass(signal, cutoff):
    alpha = 1 - np.exp(-2 * np.pi * cutoff / RATE)
    out = np.empty_like(signal)
    value = 0.0
    for index, sample in enumerate(signal):
        value += alpha * (sample - value)
        out[index] = value
    return out


def bandpass(signal, low, high):
    return lowpass(signal, high) - lowpass(signal, low)


def noise(duration, decay=0.05, low=200, high=6000, attack=0.002):
    raw = RNG.uniform(-1, 1, int(RATE * duration))
    return bandpass(raw, low, high) * env(len(raw), attack, decay) * 2


def seq(*parts):
    """Sounds one after another; a number is a pause of that many seconds."""
    out = []
    for part in parts:
        out.append(np.zeros(int(RATE * part)) if isinstance(part, (int, float)) else part)
    return np.concatenate(out)


def mix(*parts):
    length = max(len(p) for p in parts)
    out = np.zeros(length)
    for part in parts:
        out[:len(part)] += part
    return out


def at(offset, part):
    return np.concatenate([np.zeros(int(RATE * offset)), part])


def arp(freqs, step, wave='square', decay=0.18, hold=None):
    return mix(*[at(i * step, tone(f, hold or step * 3, wave, decay)) for i, f in enumerate(freqs)])


def note(name):
    names = {'C': -9, 'D': -7, 'E': -5, 'F': -4, 'G': -2, 'A': 0, 'B': 2}
    semis = names[name[0]] + (1 if '#' in name else 0) + 12 * (int(name[-1]) - 4)
    return 440 * 2 ** (semis / 12)


def chord(names, duration, wave='saw', decay=0.6):
    return mix(*[tone(note(n), duration, wave, decay) for n in names])


MACHINES = {
    'slot_machine': {
        'start': lambda: mix(noise(0.25, 0.08, 60, 600), at(0.05, seq(*[x for _ in range(5) for x in (noise(0.02, 0.01, 2000, 7000), 0.03)]))),
        'tick': lambda: noise(0.04, 0.012, 2500, 8000),
        'win': lambda: mix(arp([note('C5'), note('E5'), note('G5'), note('C6')], 0.09, 'sine', 0.5), at(0.36, tone(note('C6'), 0.9, 'sine', 0.5))),
        'lose': lambda: seq(tone(note('E4'), 0.16, 'triangle', 0.12), tone(note('C4'), 0.3, 'triangle', 0.2)),
    },
    'lucky_wheel': {
        'start': lambda: mix(noise(0.6, 0.3, 300, 2500, 0.15), noise(0.05, 0.02, 1000, 4000)),
        'tick': lambda: noise(0.05, 0.015, 1200, 3000),
        'win': lambda: seq(chord(['C4', 'E4', 'G4'], 0.18, 'saw', 0.15), chord(['F4', 'A4', 'C5'], 0.18, 'saw', 0.15), chord(['G4', 'B4', 'D5', 'G5'], 0.7, 'saw', 0.4)) * 0.5,
        'lose': lambda: sweep(300, 120, 0.7, 'saw', 0.5) * 0.6,
    },
    'coin_pusher': {
        'start': lambda: mix(tone(3200, 0.4, 'sine', 0.12), tone(4150, 0.4, 'sine', 0.1), at(0.12, tone(3600, 0.3, 'sine', 0.09))),
        'tick': lambda: mix(tone(3800, 0.15, 'sine', 0.04), tone(5200, 0.15, 'sine', 0.03)),
        'win': lambda: mix(*[at(i * 0.05 + RNG.uniform(0, 0.02), mix(tone(RNG.uniform(3000, 4800), 0.2, 'sine', 0.06), tone(RNG.uniform(4800, 6000), 0.2, 'sine', 0.05))) for i in range(18)]) * 0.5,
        'lose': lambda: noise(0.3, 0.12, 60, 500),
    },
    'prize_claw': {
        'start': lambda: sweep(70, 160, 0.5, 'saw', 1.0) * 0.4,
        'tick': lambda: tone(110, 0.12, 'saw', 0.2, vibrato=0.05) * 0.4,
        'win': lambda: arp([note('G4'), note('C5'), note('E5'), note('G5'), note('E5'), note('G5')], 0.08, 'square', 0.12) * 0.6,
        'lose': lambda: seq(tone(note('D4'), 0.25, 'saw', 0.3, vibrato=0.02), tone(note('C#4'), 0.25, 'saw', 0.3, vibrato=0.02), tone(note('C4'), 0.6, 'saw', 0.5, vibrato=0.04)) * 0.4,
    },
    'pachinko': {
        'start': lambda: mix(sweep(200, 1800, 0.25, 'sine', 0.3), noise(0.2, 0.06, 1500, 6000)),
        'tick': lambda: tone(2600, 0.1, 'sine', 0.025),
        'win': lambda: arp([note(n) for n in ('C5', 'D5', 'E5', 'G5', 'A5', 'C6', 'D6', 'E6')], 0.05, 'sine', 0.2),
        'lose': lambda: tone(220, 0.25, 'square', 0.08) * 0.5,
    },
    'capsule_gacha': {
        'start': lambda: seq(*[x for _ in range(4) for x in (noise(0.03, 0.01, 800, 5000), 0.05)]),
        'tick': lambda: mix(noise(0.03, 0.008, 1500, 6000), tone(900, 0.03, 'square', 0.01) * 0.3),
        'win': lambda: mix(noise(0.08, 0.02, 200, 3000) * 1.4, at(0.08, arp([note('E6'), note('G6'), note('B6'), note('E7')], 0.06, 'sine', 0.25))),
        'lose': lambda: mix(noise(0.08, 0.02, 200, 3000), at(0.08, tone(note('A3'), 0.3, 'triangle', 0.2))),
    },
    'high_stakes': {
        'start': lambda: sweep(80, 900, 0.6, 'saw', 1.0) * 0.4,
        'tick': lambda: mix(tone(55, 0.2, 'sine', 0.08), at(0.12, tone(50, 0.2, 'sine', 0.07))),
        'win': lambda: mix(chord(['C4', 'G4', 'C5', 'E5', 'G5'], 1.4, 'saw', 0.8) * 0.35, sweep(400, 2400, 0.8, 'sine', 0.6) * 0.3),
        'lose': lambda: sweep(900, 60, 0.7, 'saw', 0.6) * 0.4,
    },
    'plinko': {
        'start': lambda: sweep(900, 300, 0.2, 'sine', 0.15),
        'tick': lambda: tone(950, 0.06, 'sine', 0.02) + tone(1900, 0.06, 'sine', 0.01) * 0.3,
        'win': lambda: arp([note(n) for n in ('E5', 'G5', 'E6', 'C6', 'D6', 'G6')], 0.07, 'square', 0.1) * 0.6,
        'lose': lambda: arp([note('E4'), note('C4'), note('A3')], 0.1, 'square', 0.08) * 0.5,
    },
    'big_six': {
        'start': lambda: mix(noise(0.8, 0.4, 200, 1500, 0.2), noise(0.06, 0.02, 600, 2000)),
        'tick': lambda: noise(0.05, 0.018, 500, 1500) * 1.2,
        'win': lambda: seq(chord(['G3', 'D4', 'G4'], 0.14, 'saw', 0.1), 0.02, chord(['G3', 'D4', 'G4'], 0.14, 'saw', 0.1), 0.02, chord(['C4', 'E4', 'G4', 'C5'], 0.8, 'saw', 0.45)) * 0.45,
        'lose': lambda: sweep(140, 90, 0.5, 'saw', 0.4) * 0.6,
    },
    'video_poker': {
        'start': lambda: seq(*[x for _ in range(5) for x in (noise(0.025, 0.008, 2000, 8000), 0.02)]),
        'tick': lambda: noise(0.04, 0.01, 1800, 7000),
        'win': lambda: arp([note('A5'), note('C#6'), note('E6'), note('A6')], 0.06, 'sine', 0.3),
        'lose': lambda: tone(120, 0.25, 'square', 0.15) * 0.4,
    },
    'horse_race': {
        'start': lambda: seq(*[tone(note(n), d, 'triangle', d * 1.5) for n, d in (
            ('G4', 0.12), ('C5', 0.12), ('E5', 0.12), ('G5', 0.3), ('E5', 0.12), ('G5', 0.5))]),
        'tick': lambda: mix(noise(0.05, 0.015, 80, 900), at(0.07, noise(0.05, 0.015, 80, 900))),
        'win': lambda: mix(noise(1.4, 0.7, 300, 3000, 0.2) * 0.7, arp([note('C5'), note('E5'), note('G5'), note('C6')], 0.1, 'triangle', 0.4)),
        'lose': lambda: noise(0.9, 0.4, 200, 1200, 0.1) * 0.6,
    },
}


def music():
    """A chiptune loop for the arcade: square lead, triangle bass, noise hats, 120 bpm, sixteen bars."""
    beat = 0.5
    bars = [('C', ['C5', 'E5', 'G5', 'E5', 'C5', 'G4', 'A4', 'B4']),
            ('A', ['A4', 'C5', 'E5', 'C5', 'A4', 'E5', 'D5', 'C5']),
            ('F', ['F4', 'A4', 'C5', 'A4', 'F5', 'E5', 'D5', 'C5']),
            ('G', ['G4', 'B4', 'D5', 'G5', 'F5', 'D5', 'B4', 'G4'])]
    total = int(RATE * beat * 4 * len(bars) * 2)
    out = np.zeros(total + RATE)
    position = 0.0
    for repeat in range(2):
        for root, melody in bars:
            for index, name in enumerate(melody):
                start = int(RATE * (position + index * beat / 2))
                lead = tone(note(name), beat / 2 * 0.95, 'square', 0.18) * 0.18
                out[start:start + len(lead)] += lead
            for quarter in range(4):
                start = int(RATE * (position + quarter * beat))
                bass_note = note(root + '2') * (1.5 if quarter % 2 else 1)
                bass = tone(bass_note, beat * 0.9, 'triangle', 0.4) * 0.35
                out[start:start + len(bass)] += bass
                hat = noise(0.05, 0.015, 5000, 9000) * 0.12
                hat_start = start + int(RATE * beat / 2)
                out[hat_start:hat_start + len(hat)] += hat
            position += beat * 4
    out = out[:total]
    # The end fades into the start, so the loop has no click.
    fade = int(RATE * 0.02)
    out[:fade] *= np.linspace(0, 1, fade)
    out[-fade:] *= np.linspace(1, 0, fade)
    return out


def write(relative, signal):
    path = ASSETS / 'sounds' / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    peak = max(1e-6, float(np.max(np.abs(signal))))
    signal = signal / peak * 0.85
    soundfile.write(str(path), signal.astype(np.float32), RATE, format='OGG', subtype='VORBIS')


def main():
    sounds_json = ASSETS / 'sounds.json'
    events = json.loads(sounds_json.read_text(encoding='utf-8'))
    for machine, kinds in MACHINES.items():
        for kind, make in kinds.items():
            write(f'machine/{machine}/{kind}.ogg', make())
            events[f'machine.{machine}.{kind}'] = {
                'subtitle': f'subtitles.gamblingitems.machine.{kind}',
                'sounds': [f'gamblingitems:machine/{machine}/{kind}']}
    write('music/arcade.ogg', music())
    events['music.arcade'] = {'sounds': [{'name': 'gamblingitems:music/arcade', 'stream': True}]}
    sounds_json.write_text(json.dumps(events, indent=2) + '\n', encoding='utf-8')
    print(len(MACHINES) * 4 + 1, 'sounds')


if __name__ == '__main__':
    main()
