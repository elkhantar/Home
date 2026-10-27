package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;

/** Signed, offline collection in our own external-files folder. No storage permission required. */
final class LocalWallpapers {
    private static String cachedVersion;
    private static Snapshot cached;
    static final class Snapshot {
        final boolean configured;final List<DailyPhoto.Photo> photos;
        Snapshot(boolean configured,List<DailyPhoto.Photo> photos){this.configured=configured;this.photos=photos;}
    }
    static File folder(Context c){File base=c.getExternalFilesDir(null);return base==null?null:new File(base,"wallpapers");}
    static long offset(Context c){return c.getSharedPreferences("local_wallpapers",0).getLong("offset",0);}
    static void advance(Context c){c.getSharedPreferences("local_wallpapers",0).edit().putLong("offset",offset(c)+1).apply();}
    static String version(Context c){
        File folder=folder(c);if(folder==null)return "none";
        File manifest=new File(folder,"catalogue.json"),sig=new File(folder,"catalogue.sig");
        return manifest.lastModified()+":"+manifest.length()+":"+sig.lastModified()+":webp90:"+offset(c);
    }
    static synchronized Snapshot read(Context c){
        File folder=folder(c);File manifest=folder==null?null:new File(folder,"catalogue.json");
        if(manifest==null || !manifest.isFile())return new Snapshot(false,Collections.emptyList());
        String token=version(c);if(token.equals(cachedVersion) && cached!=null)return cached;
        try {
            byte[] bytes;try(InputStream in=new FileInputStream(manifest)){bytes=SafeHttp.read(in,1024*1024,Long.MAX_VALUE);}
            byte[] signature;try(InputStream in=new FileInputStream(new File(folder,"catalogue.sig"))){signature=SafeHttp.read(in,8192,Long.MAX_VALUE);}
            android.content.pm.PackageInfo pkg=c.getPackageManager().getPackageInfo(c.getPackageName(),PackageManager.GET_SIGNATURES);
            PublicKey key=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(pkg.signatures[0].toByteArray())).getPublicKey();
            if(!WallpaperIntegrity.verify(key,bytes,signature))throw new SecurityException("Untrusted wallpaper catalogue");
            JSONObject document=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
            if(document.getInt("schema_version")!=1)throw new IOException("Unsupported wallpaper catalogue");
            JSONArray records=document.getJSONArray("photos");if(records.length()>500)throw new IOException("Too many wallpapers");
            List<DailyPhoto.Photo> photos=new ArrayList<>();
            for(int n=0;n<records.length();n++){
                JSONObject row=records.getJSONObject(n);String id=row.getString("id");
                if(!id.matches("[A-Za-z0-9_-]{11}"))continue;
                String quality="webp90";
                JSONObject variant=row.getJSONObject("variants").getJSONObject(quality);
                String name=variant.getString("file"),hash=variant.getString("sha256");
                if(!WallpaperIntegrity.validName(name) || !name.startsWith(id+"-") || !WallpaperIntegrity.validHash(hash))continue;
                File image=new File(folder,name).getCanonicalFile();if(!folder.getCanonicalFile().equals(image.getParentFile()))continue;
                String caption=clean(row.optString("photographer_caption"),2048),location=clean(row.optString("location"),512);
                String author=clean(row.optString("photographer"),256);
                String date=clean(row.optString("date_published"),64);
                if(date.length()>=10 && date.substring(0,10).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))date=date.substring(0,10);else date="";
                String title=join(caption,location);String credit=author.isEmpty()?"":author+" / Unsplash";
                // Clearly identify the date as publication, never as the date taken.
                if(!date.isEmpty())credit=join(credit,c.getString(R.string.wallpaper_published,date));
                photos.add(new DailyPhoto.Photo(image,hash,id+"-"+quality,title,credit));
            }
            photos.sort(Comparator.comparing(p->p.key));
            // Stable shuffle: metadata enrichment does not reorder the collection.
            Collections.shuffle(photos,new Random(0x484f4d45L));
            cached=new Snapshot(true,Collections.unmodifiableList(photos));cachedVersion=token;return cached;
        }catch(Exception e){android.util.Log.w("Home.Wallpaper","Local catalogue unavailable; retaining last picture");
            // Fail closed: never switch back to Bing after a local collection was configured.
            return new Snapshot(true,Collections.emptyList());}
    }
    private static String clean(String s,int max){if(s==null || s.equals("null"))return "";return s.replaceAll("[\\p{Cntrl}]"," ").trim().substring(0,Math.min(max,s.replaceAll("[\\p{Cntrl}]"," ").trim().length()));}
    private static String join(String a,String b){return a.isEmpty()?b:b.isEmpty()?a:a+"  \u00b7  "+b;}
    static Bitmap load(DailyPhoto.Photo p,int screenWidth){
        try {
            byte[] bytes=WallpaperIntegrity.readVerified(p.localFile,p.sha256);if(bytes==null)return null;
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
            if(!DailyPhoto.validDimensions(o.outWidth,o.outHeight))return null;
            o.inJustDecodeBounds=false;o.inSampleSize=1;
            int target=Math.max(320,Math.min(screenWidth,1920));
            while(o.outWidth/o.inSampleSize>target || o.outHeight/o.inSampleSize>1080)o.inSampleSize*=2;
            o.inPreferredConfig=Bitmap.Config.ARGB_8888;o.inMutable=true;
            return BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);
        }catch(Exception|OutOfMemoryError e){return null;}
    }
}
