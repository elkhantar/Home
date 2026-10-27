package com.gathin.tvhome;

import java.util.*;

/** Pure selection rules for app-published progress. Never invent a next episode. */
final class WatchNextPolicy {
    static final int LIMIT = 10, SCAN_LIMIT = 1000;
    static final class Item {
        final long id, engagement, position, duration;
        final int kind;
        final String pkg, title, episode, intent;
        Item(long id, String pkg, String title, String episode, String intent, int kind,
             long engagement, long position, long duration) {
            this.id=id; this.pkg=pkg; this.title=title; this.episode=episode; this.intent=intent;
            this.kind=kind; this.engagement=engagement; this.position=position; this.duration=duration;
        }
    }
    static boolean allowedScheme(String scheme) {
        if (scheme == null || scheme.isEmpty()) return false;
        String s=scheme.toLowerCase(Locale.ROOT);
        return !Arrays.asList("file","content","javascript","data","intent","android.resource").contains(s);
    }
    static List<Item> select(List<Item> input) {
        List<Item> sorted=new ArrayList<>();
        for (Item i:input) {
            if (i == null || i.pkg == null || i.pkg.isEmpty() || i.title == null || i.title.trim().isEmpty()
                    || i.intent == null || i.intent.isEmpty() || i.intent.length()>8192
                    || (i.kind!=0 && i.kind!=1) || (i.kind==0 && i.duration>0 && i.position>=i.duration)) continue;
            sorted.add(i);
        }
        sorted.sort((a,b)-> {int c=Long.compare(b.engagement,a.engagement);return c!=0?c:Long.compare(b.id,a.id);});
        Set<String> seen=new HashSet<>(); List<Item> result=new ArrayList<>();
        for (Item i:sorted) {
            if (seen.add(i.pkg+"\n"+i.intent)) result.add(i);
            if (result.size()==LIMIT) break;
        }
        return result;
    }
    static int progress(Item i) {
        if (i.kind!=0 || i.duration<=0 || i.position<=0) return 0;
        return (int)Math.min(100,100d*i.position/i.duration);
    }
}
