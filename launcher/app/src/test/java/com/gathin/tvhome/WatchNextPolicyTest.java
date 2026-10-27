package com.gathin.tvhome;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class WatchNextPolicyTest {
    private WatchNextPolicy.Item item(long id,int kind,long when,long position,long duration,String intent) {
        return new WatchNextPolicy.Item(id,"tv.example","Show","Episode",intent,kind,when,position,duration);
    }
    @Test public void selectsOnlyContinueAndNextNotRecommendationsOrWatchlists() {
        List<WatchNextPolicy.Item> result=WatchNextPolicy.select(Arrays.asList(
                item(1,0,10,20,100,"example:a"),item(2,1,11,0,0,"example:b"),
                item(3,2,12,0,0,"example:c"),item(4,3,13,0,0,"example:d")));
        assertEquals(2,result.size());assertEquals(2,result.get(0).id);
    }
    @Test public void completedItemsAreExcludedButUnknownProgressStillWorks() {
        List<WatchNextPolicy.Item> result=WatchNextPolicy.select(Arrays.asList(
                item(1,0,10,100,100,"example:a"),item(2,0,9,200,100,"example:b"),
                item(3,0,8,0,0,"example:c"),item(4,1,7,0,0,"example:d")));
        assertEquals(2,result.size());assertEquals(3,result.get(0).id);
    }
    @Test public void deduplicatesKeepingTheNewestAndLimitsToTen() {
        List<WatchNextPolicy.Item> source=new ArrayList<>();
        source.add(item(100,0,100,1,10,"example:0"));
        for(int n=0;n<30;n++)source.add(item(n,0,n,1,10,"example:"+n));
        List<WatchNextPolicy.Item> result=WatchNextPolicy.select(source);
        assertEquals(10,result.size());assertEquals(100,result.get(0).id);
        assertEquals(29,result.get(1).id);
    }
    @Test public void rejectsIncompleteOrOversizedRows() {
        WatchNextPolicy.Item bad=new WatchNextPolicy.Item(1,"tv.example","","","example:a",0,0,1,10);
        assertTrue(WatchNextPolicy.select(Arrays.asList(null,bad,item(2,0,0,0,0,""),
                item(3,0,0,0,0,String.join("",Collections.nCopies(8193,"x"))))).isEmpty());
    }
    @Test public void forbidsLocalFilesContentAndExecutableDataSchemes() {
        for(String s:Arrays.asList("file","FILE","content","javascript","data","intent","android.resource",null,""))
            assertFalse(WatchNextPolicy.allowedScheme(s));
        assertTrue(WatchNextPolicy.allowedScheme("https"));assertTrue(WatchNextPolicy.allowedScheme("netflix"));
    }
    @Test public void progressHandlesUnknownAndLargeValuesWithoutOverflow() {
        assertEquals(0,WatchNextPolicy.progress(item(1,0,0,0,0,"example:a")));
        assertEquals(50,WatchNextPolicy.progress(item(1,0,0,Long.MAX_VALUE/2,Long.MAX_VALUE,"example:a")));
        assertEquals(100,WatchNextPolicy.progress(item(1,0,0,20,10,"example:a")));
    }
}
