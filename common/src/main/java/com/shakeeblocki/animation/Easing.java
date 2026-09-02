package com.shakeeblocki.animation;

/**
 * Mathematical easing curves for interpolation and animation decay.
 * Standard public-domain formulas (Penner & Hermite polynomials).
 */
public enum Easing {
    CONSTANT {
        @Override
        public float apply(float t) {
            return 1.0F;
        }
    },
    LINEAR {
        @Override
        public float apply(float t) {
            return Math.clamp(1.0F - t, 0.0F, 1.0F);
        }
    },
    EASE_OUT {
        @Override
        public float apply(float t) {
            float inv = Math.clamp(1.0F - t, 0.0F, 1.0F);
            return inv * inv;
        }
    },
    EASE_IN_OUT {
        @Override
        public float apply(float t) {
            float clamped = Math.clamp(t, 0.0F, 1.0F);
            if (clamped < 0.5F) {
                return 1.0F - (2.0F * clamped * clamped);
            }
            float inv = 1.0F - clamped;
            return 2.0F * inv * inv;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float t) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = Math.clamp(t, 0.0F, 1.0F) - 1.0F;
            return Math.max(0.0F, 1.0F + c3 * inv * inv * inv + c1 * inv * inv);
        }
    },
    BOUNCE_OUT {
        @Override
        public float apply(float t) {
            float x = Math.clamp(1.0F - t, 0.0F, 1.0F);
            float n1 = 7.5625F;
            float d1 = 2.75F;
            if (x < 1.0F / d1) {
                return n1 * x * x;
            } else if (x < 2.0F / d1) {
                x -= 1.5F / d1;
                return n1 * x * x + 0.75F;
            } else if (x < 2.5F / d1) {
                x -= 2.25F / d1;
                return n1 * x * x + 0.9375F;
            } else {
                x -= 2.625F / d1;
                return n1 * x * x + 0.984375F;
            }
        }
    };

    public abstract float apply(float t);
}
