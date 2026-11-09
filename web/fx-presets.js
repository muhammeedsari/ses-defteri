/* 4.17: ready-made sounds as data. A new effect needs no code: add an entry here and its two texts
   (fxc_<id> name, fxcd_<id> one line) to an i18n file. Every step is one of the editor's own tools (Workspace op)
   with its settings; they run one after another. voices: the "Change voice" characters
   (vst = semitones, tempo = speed without pitch change, fx = a character sound). */
window.SD_FX = {
  voices: {
    female: { vst: 4, tempo: 1, speed: 1 }, male: { vst: -4, tempo: 1, speed: 1 }, robot: { vst: 0, tempo: 1, speed: 1, fx: 'robot' },
    cartoon: { vst: 7, tempo: 1.1, speed: 1 }, girl: { vst: 6, tempo: 1.05, speed: 1 }, oldman: { vst: -3, tempo: 0.9, speed: 1 }
  },
  chains: [
    { id: 'warm', steps: [{ op: 'eqpreset', gains: [3, -1, 0, 2, 1] }, { op: 'comp', thr: -24, ratio: 3 }] },
    { id: 'trailer', steps: [{ op: 'vchange', semitones: -4 }, { op: 'eqpreset', gains: [4, 0, 0, 2, 0] }, { op: 'reverb', room: 0.8, mix: 0.25 }] },
    { id: 'stadium', steps: [{ op: 'eqpreset', gains: [-6, 0, 2, 4, -2] }, { op: 'delay', ms: 180, fb: 0.25, mix: 0.25 }, { op: 'reverb', room: 0.9, mix: 0.35 }] },
    { id: 'cave', steps: [{ op: 'reverb', room: 1, mix: 0.5 }, { op: 'delay', ms: 420, fb: 0.3, mix: 0.2 }] },
    { id: 'nextroom', steps: [{ op: 'eqpreset', gains: [-2, 0, -3, -8, -12] }, { op: 'reverb', room: 0.5, mix: 0.45 }] },
    { id: 'walkie', steps: [{ op: 'voicefx', preset: 'radio' }, { op: 'eqpreset', gains: [-12, 0, 4, 4, -12] }, { op: 'comp', thr: -30, ratio: 6 }] },
    { id: 'oldradio', steps: [{ op: 'voicefx', preset: 'radio' }, { op: 'lofi' }] },
    { id: 'ghost', steps: [{ op: 'reverse' }, { op: 'reverb', room: 0.95, mix: 0.6 }, { op: 'reverse' }] },
    { id: 'dream', steps: [{ op: 'reverb', room: 0.95, mix: 0.45 }, { op: 'delay', ms: 350, fb: 0.4, mix: 0.25 }, { op: 'widen' }] },
    { id: 'choir', steps: [{ op: 'double' }, { op: 'widen' }, { op: 'reverb', room: 0.85, mix: 0.35 }] }
  ]
};
