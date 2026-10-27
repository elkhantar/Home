package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import java.util.*;

/** Read-only TV-provider access. No selections, writes, accounts or remote artwork downloads. */
final class WatchNextReader {
    static final String PERMISSION="android.permission.READ_TV_LISTINGS";
    static final Uri URI=Uri.parse("content://android.media.tv/watch_next_program");
    private static final String[] COLUMNS={"_id","package_name","title","episode_title","intent_uri",
            "watch_next_type","last_engagement_time_utc_millis","last_playback_position_millis","duration_millis","browsable"};
    static List<WatchNextPolicy.Item> read(Context context, CancellationSignal cancel) {
        List<WatchNextPolicy.Item> items=new ArrayList<>();
        try (Cursor c=context.getContentResolver().query(URI,COLUMNS,null,null,"last_engagement_time_utc_millis DESC, _id DESC",cancel)) {
            int scanned=0;
            while (c!=null && scanned++<WatchNextPolicy.SCAN_LIMIT && c.moveToNext()) {
                cancel.throwIfCanceled();
                if (c.getInt(9)==0) continue;
                String pkg=text(c,1,256), title=text(c,2,512), episode=text(c,3,512), link=text(c,4,8192);
                WatchNextPolicy.Item i=new WatchNextPolicy.Item(c.getLong(0),pkg,title,episode,link,c.getInt(5),
                        c.getLong(6),c.getLong(7),c.getLong(8));
                if (safeIntent(context,i)!=null) items.add(i);
            }
        }
        return WatchNextPolicy.select(items);
    }
    private static String text(Cursor c,int column,int max) {
        String s=c.getString(column);return s==null?"":s.length()>max?"":s;
    }
    /** Retain app-provided scalar deep-link extras but forbid cross-package targets or URI grants. */
    static Intent safeIntent(Context context,WatchNextPolicy.Item i) {
        if (i.pkg==null || i.pkg.isEmpty() || i.pkg.equals(context.getPackageName())) return null;
        try {
            Intent parsed=Intent.parseUri(i.intent,Intent.URI_INTENT_SCHEME);
            if (!Intent.ACTION_VIEW.equals(parsed.getAction()) || parsed.getSelector()!=null
                    || (parsed.getPackage()!=null && !i.pkg.equals(parsed.getPackage()))
                    || (parsed.getComponent()!=null && !i.pkg.equals(parsed.getComponent().getPackageName()))
                    || parsed.getData()==null || !WatchNextPolicy.allowedScheme(parsed.getData().getScheme())) return null;
            parsed.setPackage(i.pkg);
            parsed.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            parsed.setClipData(null);
            ResolveInfo resolved=context.getPackageManager().resolveActivity(parsed,0);
            if (resolved==null || resolved.activityInfo==null || !resolved.activityInfo.exported
                    || !i.pkg.equals(resolved.activityInfo.packageName)) return null;
            return parsed;
        } catch (Exception e) {return null;}
    }
}
