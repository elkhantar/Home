package com.gathin.tvhome;

import java.io.*;
import java.net.*;
import java.util.Locale;

/** Small fixed-source HTTPS client. No credentials, cookies, redirects or arbitrary hosts. */
final class SafeHttp {
    static final int JSON_LIMIT = 128 * 1024;
    static final int IMAGE_LIMIT = 6 * 1024 * 1024;
    private SafeHttp() {}

    static URL checkedUrl(String value) throws IOException {
        URL url = new URL(value);
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(url.getProtocol()) || url.getUserInfo() != null
                || (url.getPort() != -1 && url.getPort() != 443)
                || !(host.equals("www.bing.com") || host.equals("api.open-meteo.com")))
            throw new IOException("Unapproved data source");
        return url;
    }

    static byte[] fetch(String value, int limit) throws IOException {
        HttpURLConnection c = (HttpURLConnection) checkedUrl(value).openConnection();
        long deadline = System.nanoTime() + 20_000_000_000L;
        c.setConnectTimeout(6000);
        c.setReadTimeout(6000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "PersonalTVHome/1.0");
        try {
            if (c.getResponseCode() != 200) throw new IOException("Data source unavailable");
            if (c.getContentLengthLong() > limit) throw new IOException("Response exceeds size limit");
            try (InputStream in = c.getInputStream()) { return read(in, limit, deadline); }
        } finally { c.disconnect(); }
    }

    static byte[] read(InputStream in, int limit, long deadline) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(limit, 16384));
        byte[] buf = new byte[16384];
        for (;;) {
            if (Thread.currentThread().isInterrupted() || System.nanoTime() > deadline)
                throw new InterruptedIOException("Request cancelled or expired");
            int n = in.read(buf);
            if (n < 0) return out.toByteArray();
            if (n > limit - out.size()) throw new IOException("Response exceeds size limit");
            out.write(buf, 0, n);
        }
    }
}
