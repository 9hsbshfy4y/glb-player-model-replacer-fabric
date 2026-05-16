package dev.mark.code.config;

public final class Config {
    public static boolean replaceSelf = true;
    public static boolean replaceOthers = true;
    public static Models activePreset = Models.SAHUR;

    public enum Models {
        SAHUR("Tung Tung Sahur", "sahur.glb", 0.2f, 0.13f, -90f, 1.0f),
        AMOGUS("Amogus", "amogus.glb", 0.0f, 0.0f, 180f, 1.0f);

        public final String displayName;
        public final String resourcePath;
        public final float forwardOffset;
        public final float leftOffset;
        public final float yawOffsetDeg;
        public final float scaleMultiplier;

        Models(String displayName, String resourcePath, float forwardOffset, float leftOffset, float yawOffsetDeg, float scaleMultiplier) {
            this.displayName = displayName;
            this.resourcePath = resourcePath;
            this.forwardOffset = forwardOffset;
            this.leftOffset = leftOffset;
            this.yawOffsetDeg = yawOffsetDeg;
            this.scaleMultiplier = scaleMultiplier;
        }
    }
}
