package com.shakeeblocki.animation.physics;

/**
 * Analytical Damped Harmonic Oscillator (Spring-Damper system).
 * Provides mathematically exact, frame-rate independent physics simulation
 * for block bouncing, settling, and vibration impulses.
 */
public final class DampedSpring {
    private final float naturalFrequency; // Angular frequency (stiffness)
    private final float dampingRatio;     // Damping ratio (zeta): < 1 underdamped, 1 critical, > 1 overdamped
    private final float dampedFrequency;  // Angular frequency of damped oscillation

    public DampedSpring(float stiffness, float dampingRatio) {
        this.naturalFrequency = Math.max(0.1F, stiffness);
        this.dampingRatio = Math.clamp(dampingRatio, 0.0F, 1.5F);

        if (this.dampingRatio < 1.0F) {
            this.dampedFrequency = (float) (this.naturalFrequency * Math.sqrt(1.0F - this.dampingRatio * this.dampingRatio));
        } else {
            this.dampedFrequency = 0.0F;
        }
    }

    /**
     * Calculates the position at time t given initial displacement and velocity from equilibrium.
     * Equilibrium is at 0.0.
     *
     * @param t time in seconds or normalized units
     * @param initialDisplacement initial distance from target (x0)
     * @param initialVelocity initial speed towards target (v0)
     * @return current displacement from equilibrium
     */
    public float evaluate(float t, float initialDisplacement, float initialVelocity) {
        if (t <= 0.0F) return initialDisplacement;

        // Underdamped oscillation (bouncy, natural feel)
        if (this.dampingRatio < 1.0F) {
            float envelope = (float) Math.exp(-this.dampingRatio * this.naturalFrequency * t);
            float cosPart = (float) Math.cos(this.dampedFrequency * t);
            float sinPart = (float) Math.sin(this.dampedFrequency * t);

            float b = (initialVelocity + this.dampingRatio * this.naturalFrequency * initialDisplacement) / this.dampedFrequency;
            return envelope * (initialDisplacement * cosPart + b * sinPart);
        }

        // Critically damped (fastest settle with zero overshoot)
        if (Math.abs(this.dampingRatio - 1.0F) < 0.001F) {
            float envelope = (float) Math.exp(-this.naturalFrequency * t);
            return envelope * (initialDisplacement + (initialVelocity + this.naturalFrequency * initialDisplacement) * t);
        }

        // Overdamped (sluggish, heavy feel e.g. for obsidian or metal)
        float alpha = (float) (this.naturalFrequency * Math.sqrt(this.dampingRatio * this.dampingRatio - 1.0F));
        float r1 = -this.naturalFrequency * this.dampingRatio + alpha;
        float r2 = -this.naturalFrequency * this.dampingRatio - alpha;

        float c1 = (initialVelocity - r2 * initialDisplacement) / (r1 - r2);
        float c2 = initialDisplacement - c1;

        return (float) (c1 * Math.exp(r1 * t) + c2 * Math.exp(r2 * t));
    }
}
