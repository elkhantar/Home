package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.SystemClock;
import android.util.AtomicFile;
import android.util.Log;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;

/** Bing Spain daily archive. Cached images are usable before any network refresh completes. */
final class DailyPhoto {
    private static final String LIST = "https://www.bing.com/HPImageArchive.aspx?format=js&n=8&idx=0&mkt=es-ES";
    private static final long RETRY_MS = 30 * 60_000L;
    private static final long CACHE_BYTES = 24L * 1024 * 1024;
    private static final long FREE_RESERVE = 64L * 1024 * 1024;
    private static final int MAX_CACHED = 12;
    private static final ArchiveRetry retry = new ArchiveRetry(RETRY_MS);
    private static final Object metadataLock = new Object();
    private static volatile String lastGoodKey;

    static final class Photo {
        final String url, key, title, credit, sha256;
        final File localFile;
        Photo(String url, String key, String title, String credit) {
            this.url=url; this.key=key; this.title=title; this.credit=credit;this.localFile=null;this.sha256=null;
        }
        Photo(File file,String hash,String key,String title,String credit){
            this.url=null;this.localFile=file;this.sha256=hash;this.key=key;this.title=title;this.credit=credit;
        }
    }
    private DailyPhoto() {}
    static File dir(Context c) {
        File folder=new File(c.getCacheDir(), "wallpapers"); folder.mkdirs(); return folder;
    }
    static List<Photo> list(Context context,BooleanSupplier cancelled) {
        Context app=context.getApplicationContext();
        File file=new File(dir(app), "photos.json");
        JSONObject saved=readJson(file);
        List<Photo> old=parse(saved);
        String today=new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        if (saved != null && today.equals(saved.optString("day"))) return old;
        long now=SystemClock.elapsedRealtime();
        if (!retry.begin(now)) return old;
        if (!old.isEmpty()) {
            new Thread(() -> {
                refresh(app,today,()->false);
            }, "BingRefresh").start();
            return old;
        }
        return refresh(app,today,cancelled);
    }
    private static List<Photo> refresh(Context c, String today,BooleanSupplier cancelled) {
        try {
            return retry.complete(SystemClock::elapsedRealtime,cancelled,()->{
            JSONObject reply=new JSONObject(new String(SafeHttp.fetch(LIST,SafeHttp.JSON_LIMIT),StandardCharsets.UTF_8));
            JSONObject saved=new JSONObject().put("day",today).put("images",reply.getJSONArray("images"));
            List<Photo> photos=parse(saved);
            if (photos.isEmpty()) throw new IOException("Empty archive");
            if (cancelled.getAsBoolean()) throw new InterruptedIOException("Lifecycle cancellation");
            synchronized (metadataLock) {
                AtomicFile atomic=new AtomicFile(new File(dir(c),"photos.json"));
                FileOutputStream out=null;
                try {
                    out=atomic.startWrite(); out.write(saved.toString().getBytes(StandardCharsets.UTF_8)); atomic.finishWrite(out);
                } catch (IOException e) { if (out != null) atomic.failWrite(out); throw e; }
            }
            return photos;
            });
        } catch (Exception e) {
            Log.w("PersonalTV.Photo","Archive unavailable; keeping cached images");
            return parse(readJson(new File(dir(c),"photos.json")));
        }
    }
    private static JSONObject readJson(File f) {
        synchronized (metadataLock) {
        try (InputStream in=new AtomicFile(f).openRead()) {
            return new JSONObject(new String(SafeHttp.read(in,SafeHttp.JSON_LIMIT,Long.MAX_VALUE),StandardCharsets.UTF_8));
        } catch (Exception e) { return null; }
        }
    }
    private static List<Photo> parse(JSONObject saved) {
        List<Photo> photos=new ArrayList<>();
        JSONArray images=saved == null ? null : saved.optJSONArray("images");
        for (int i=0; images != null && i<Math.min(images.length(),16); i++) {
            JSONObject image=images.optJSONObject(i);
            if (image == null) continue;
            String base=image.optString("urlbase");
            if (!base.startsWith("/th?id=OHR.") || base.length()>512) continue;
            String title=image.optString("copyright", "Bing photo of the day"), credit="";
            if (title.length()>512) title=title.substring(0,512);
            int split=title.lastIndexOf(" (");
            if (split>0 && title.endsWith(")")) { credit=title.substring(split+2,title.length()-1); title=title.substring(0,split); }
            photos.add(new Photo("https://www.bing.com"+base+"_1920x1080.jpg",
                    base.replaceAll("[^A-Za-z0-9._-]","_"),title,credit));
        }
        return photos;
    }
    /** Network and decode run only on the caller's worker, never on the UI thread. */
    static boolean isCached(Context c, Photo p) {
        if(p.localFile!=null)return p.localFile.isFile();
        File file=new File(dir(c),p.key+".jpg");
        return file.isFile() && file.length()>0 && file.length()<=SafeHttp.IMAGE_LIMIT;
    }

    static synchronized Bitmap load(Context c, Photo p, int screenWidth) {
        if(p.localFile!=null)return LocalWallpapers.load(p,screenWidth);
        File folder=dir(c), file=new File(folder,p.key+".jpg");
        try {
            if (!file.isFile()) {
                if (!prepareDownload(folder,folder::getUsableSpace)) return null;
                byte[] data=SafeHttp.fetch(p.url,SafeHttp.IMAGE_LIMIT);
                BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(data,0,data.length,bounds);
                if (!validDimensions(bounds.outWidth,bounds.outHeight)) return null;
                File temp=new File(folder,p.key+".tmp");
                try {
                    try (OutputStream out=new FileOutputStream(temp)) { out.write(data); }
                    if (!temp.renameTo(file)) throw new IOException("Cannot save wallpaper");
                } finally { temp.delete(); }
            }
            file.setLastModified(System.currentTimeMillis());
            BitmapFactory.Options o=new BitmapFactory.Options(); o.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getPath(),o);
            if (!validDimensions(o.outWidth,o.outHeight) || file.length()>SafeHttp.IMAGE_LIMIT) { file.delete(); return null; }
            o.inJustDecodeBounds=false; o.inSampleSize=1;
            int target=Math.max(320,Math.min(screenWidth,1920));
            while (o.outWidth/o.inSampleSize>target || o.outHeight/o.inSampleSize>1080) o.inSampleSize*=2;
            o.inPreferredConfig=Bitmap.Config.RGB_565; o.inMutable=true;
            Bitmap bitmap=BitmapFactory.decodeFile(file.getPath(),o);
            if (bitmap == null) file.delete();
            else lastGoodKey=p.key;
            prune(folder,file);
            return bitmap;
        } catch (OutOfMemoryError e) {
            Log.w("PersonalTV.Photo","Decode memory unavailable; retaining cached photo"); return null;
        } catch (Exception e) {
            file.delete(); Log.w("PersonalTV.Photo","Wallpaper unavailable; keeping previous picture"); return null;
        }
    }
    static boolean validDimensions(int w,int h) {
        return w>=320 && h>=180 && w<=8192 && h<=8192 && (long)w*h<=16_777_216L;
    }
    static void prune(File folder,File keep) {
        prune(folder,keep,folder::getUsableSpace,FREE_RESERVE);
    }
    static boolean prepareDownload(File folder,LongSupplier space) {
        prune(folder,null,space,FREE_RESERVE+SafeHttp.IMAGE_LIMIT);
        return space.getAsLong()>=FREE_RESERVE+SafeHttp.IMAGE_LIMIT;
    }
    private static void prune(File folder,File keep,LongSupplier space,long reserve) {
        // load() is serialized, so no image download can own a .tmp file at this point.
        File[] abandoned=folder.listFiles((d,n)->n.endsWith(".tmp"));
        if (abandoned!=null) for(File file:abandoned) file.delete();
        File[] files=folder.listFiles((d,n)->n.endsWith(".jpg"));
        if (files == null) return;
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        if (keep==null && lastGoodKey!=null) {
            File previous=new File(folder,lastGoodKey+".jpg");
            if (previous.isFile()) keep=previous;
        }
        if (keep==null && files.length>0) keep=files[0];
        long bytes=0; int count=0;
        if (keep != null && keep.isFile()) {bytes=keep.length();count=1;}
        for (File file:files) {
            if (file.equals(keep)) continue;
            if (count>=MAX_CACHED || bytes+file.length()>CACHE_BYTES || space.getAsLong()<reserve) file.delete();
            else {bytes+=file.length();count++;}
        }
    }
}
