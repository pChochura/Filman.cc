import { LektorDucking } from '../../data/models/lektorModels.js';

const DUCK_STEPS = 6;
const DUCK_STEP_DELAY_MS = 20;
const RESTORE_STEPS = 7;
const RESTORE_STEP_DELAY_MS = 40;

export function createLektorVolumeDucker(onDuckingVolumeChange) {
  let duckingVolume = LektorDucking.DEFAULT_VOLUME;
  let isDucking = false;
  let appliedVolume = 1.0;
  let activeTimerId = null;

  function cancelRamp() {
    if (activeTimerId !== null) {
      clearTimeout(activeTimerId);
      activeTimerId = null;
    }
  }

  function rampTo(target) {
    cancelRamp();
    const start = appliedVolume;
    if (Math.abs(start - target) < 0.0001) return;

    const isDown = target < start;
    const steps = isDown ? DUCK_STEPS : RESTORE_STEPS;
    const stepDelay = isDown ? DUCK_STEP_DELAY_MS : RESTORE_STEP_DELAY_MS;
    let step = 0;

    const tick = () => {
      step++;
      const fraction = step / steps;
      const value = step === steps ? target : start + (target - start) * fraction;
      appliedVolume = value;
      onDuckingVolumeChange?.(value);
      if (step < steps) {
        activeTimerId = setTimeout(tick, stepDelay);
      } else {
        activeTimerId = null;
      }
    };

    tick();
  }

  return {
    getDuckingVolume() {
      return duckingVolume;
    },
    setDuckingVolume(volume) {
      duckingVolume = LektorDucking.fromStored(volume);
      if (isDucking) rampTo(duckingVolume);
    },
    duck() {
      isDucking = true;
      rampTo(duckingVolume);
    },
    restore() {
      isDucking = false;
      rampTo(1.0);
    },
    releaseImmediate() {
      cancelRamp();
      isDucking = false;
      if (appliedVolume !== 1.0) {
        appliedVolume = 1.0;
        onDuckingVolumeChange?.(1.0);
      }
    },
  };
}
