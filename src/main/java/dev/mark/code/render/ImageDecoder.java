package dev.mark.code.render;

import dev.mark.code.Main;
import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

public class ImageDecoder {
    public static NativeImage decode(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException stbFailure) {
            Main.LOGGER.debug("STB decoder failed, falling back to ImageIO", stbFailure);
        }
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) throw new IOException("Unable to decode image (unknown format)");
        return convert(source);
    }

    private static NativeImage convert(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        NativeImage result = new NativeImage(width, height, false);
        int[] pixels = new int[width * height];
        source.getRGB(0, 0, width, height, pixels, 0, width);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = pixels[y * width + x];
                if ((argb & 0xFF000000) == 0) argb |= 0xFF000000;
                result.setColorArgb(x, y, argb);
            }
        }
        return result;
    }
}
