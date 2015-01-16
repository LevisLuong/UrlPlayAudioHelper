package com.libtrung.urlplayaudiohelper;

public final class UrlAudioCache extends SoftReferenceHashTable<String, StringURL> {
    private static UrlAudioCache mInstance = new UrlAudioCache();

    public static UrlAudioCache getInstance() {
        return mInstance;
    }

    private UrlAudioCache() {
    }
}
