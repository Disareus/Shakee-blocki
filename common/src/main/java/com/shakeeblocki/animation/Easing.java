package com.shakeeblocki.animation;

/**
 * Easing curves that map normalized animation progress from 0 to 1.
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
            return Math.clamp(t, 0.0F, 1.0F);
        }
    },
    EASE_OUT {
        @Override
        public float apply(float t) {
            float inv = 1.0F - Math.clamp(t, 0.0F, 1.0F);
            return 1.0F - inv * inv;
        }
    },
    EASE_IN_OUT {
        @Override
        public float apply(float t) {
            float clamped = Math.clamp(t, 0.0F, 1.0F);
            if (clamped < 0.5F) {
                return 2.0F * clamped * clamped;
            }
            float inv = clamped - 1.0F;
            return 1.0F - 0.5F * inv * inv;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float t) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = Math.clamp(t, 0.0F, 1.0F) - 1.0F;
            return 1.0F + c3 * inv * inv * inv + c1 * inv * inv;
        }
    },
    BOUNCE_OUT {
        @Override
        public float apply(float t) {
            float x = 1.0F - Math.clamp(t, 0.0F, 1.0F);
            float n1 = 7.5625F;
            float d1 = 2.75F;
            if (x < 1.0F / d1) {
                return 1.0F - n1 * x * x;
            } else if (x < 2.0F / d1) {
                x -= 1.5F / d1;
                return 1.0F - (n1 * x * x + 0.75F);
            } else if (x < 2.5F / d1) {
                x -= 2.25F / d1;
                return 1.0F - (n1 * x * x + 0.9375F);
            }
            x -= 2.625F / d1;
            return 1.0F - (n1 * x * x + 0.984375F);
        }
    };

    public abstract float apply(float t);
}
