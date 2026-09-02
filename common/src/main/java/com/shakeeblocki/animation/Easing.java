package com.shakeeblocki.animation;

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
            return 1.0F - t;
        }
    },
    EASE_OUT {
        @Override
        public float apply(float t) {
            float inv = 1.0F - t;
            return inv * inv;
        }
    },
    EASE_IN_OUT {
        @Override
        public float apply(float t) {
            if (t < 0.5F) {
                return 1.0F - (2.0F * t * t);
            }
            float inv = 1.0F - t;
            return 2.0F * inv * inv;
        }
    },
    EASE_OUT_BACK {
        @Override
        public float apply(float t) {
            float c1 = 1.70158F;
            float c3 = c1 + 1.0F;
            float inv = t - 1.0F;
            return 1.0F + c3 * inv * inv * inv + c1 * inv * inv;
        }
    };

    public abstract float apply(float t);
}
