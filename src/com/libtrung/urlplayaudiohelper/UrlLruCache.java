package com.libtrung.urlplayaudiohelper;

public class UrlLruCache extends LruCache<String, String> {
    public UrlLruCache(int maxSize) {
        super(maxSize);
    }
}
