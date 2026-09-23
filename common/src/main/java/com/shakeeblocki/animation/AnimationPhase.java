package com.shakeeblocki.animation;

/**
 * Lifecycle phases for dynamic block animations.
 */
public enum AnimationPhase {
    /** Actively simulating physics, spring oscillations, or transformations. */
    SIMULATING,
    /** Converging/settling towards equilibrium upon mining release or placement completion. */
    SETTLING,
    /** World block render restored; temporary grace period while chunk mesh recompiles. */
    RESTORING
}
