package com.splitforce.storage;

import java.util.Locale;
import java.util.Set;

/** Raster image types accepted for uploads. SVG is deliberately excluded: it can carry script. */
public final class ImageTypes {

    public static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic", "image/heif"
    );

    /** Sent with every stored blob so that, even if a hostile file slips in, the browser won't run it. */
    public static final String BLOB_CSP = "default-src 'none'; sandbox";

    private ImageTypes() {}

    public static String normalize(String contentType) {
        return contentType == null ? "" : contentType.toLowerCase(Locale.ROOT).trim();
    }

    public static boolean isAllowed(String contentType) {
        return ALLOWED.contains(normalize(contentType));
    }
}
