package com.gathin.tvhome;

import org.junit.Test;
import java.io.*;
import static org.junit.Assert.*;

public class SafeHttpTest {
    @Test public void permitsOnlyFixedHttpsSources() throws Exception {
        assertEquals("www.bing.com", SafeHttp.checkedUrl("https://www.bing.com/HPImageArchive.aspx").getHost());
        assertEquals("api.open-meteo.com", SafeHttp.checkedUrl("https://api.open-meteo.com/v1/forecast").getHost());
        String[] denied={"http://www.bing.com/x","https://www.bing.com.evil.test/x",
                "https://user:password@www.bing.com/x","https://www.bing.com:8443/x",
                "https://127.0.0.1/private","file:///etc/passwd"};
        for(String url:denied) {
            try {SafeHttp.checkedUrl(url);fail("Allowed "+url);} catch(IOException expected) { }
        }
    }
    @Test public void acceptsExactlyTheByteBudget() throws Exception {
        assertEquals(128,SafeHttp.read(new ByteArrayInputStream(new byte[128]),128,Long.MAX_VALUE).length);
    }
    @Test public void rejectsOversizedAndNeverEndingStreams() throws Exception {
        InputStream endless=new InputStream() {public int read(){return 0;}};
        try {SafeHttp.read(endless,1024,Long.MAX_VALUE);fail("Unbounded read");} catch(IOException expected) { }
    }
    @Test public void expiresBeforeReadingWhenBudgetHasElapsed() throws Exception {
        InputStream forbidden=new InputStream() {public int read(){throw new AssertionError("Expired read started");}};
        try {SafeHttp.read(forbidden,128,0);fail("Deadline ignored");} catch(InterruptedIOException expected) { }
    }
    @Test public void cancellationDoesNotReadAnotherByte() throws Exception {
        Thread.currentThread().interrupt();
        try {SafeHttp.read(new ByteArrayInputStream(new byte[1]),128,Long.MAX_VALUE);fail("Cancellation ignored");}
        catch(InterruptedIOException expected) { }
        finally {Thread.interrupted();}
    }
    @Test public void rejectsDecompressionBombDimensionsAndCorruptImages() {
        assertTrue(DailyPhoto.validDimensions(1920,1080));
        assertTrue(DailyPhoto.validDimensions(3840,2160));
        assertFalse(DailyPhoto.validDimensions(8192,8192));
        assertFalse(DailyPhoto.validDimensions(Integer.MAX_VALUE,Integer.MAX_VALUE));
        assertFalse(DailyPhoto.validDimensions(-1,-1));
    }
    @Test public void cachePruningHonorsByteBudgetAndKeepsCurrentPhoto() throws Exception {
        File folder=java.nio.file.Files.createTempDirectory("tv-photo-cache-").toFile();
        try {
            for(int i=0;i<20;i++) {
                try(RandomAccessFile f=new RandomAccessFile(new File(folder,i+".jpg"),"rw")) { f.setLength(3L*1024*1024); }
            }
            File keep=new File(folder,"0.jpg"); keep.setLastModified(1);
            File metadata=new File(folder,"photos.json"); assertTrue(metadata.createNewFile());
            DailyPhoto.prune(folder,keep);
            long bytes=0;int count=0;
            for(File f:folder.listFiles()) if(f.getName().endsWith(".jpg")){bytes+=f.length();count++;}
            assertTrue(keep.isFile()); assertTrue(metadata.isFile());
            assertTrue(count<=12); assertTrue(bytes<=24L*1024*1024);
        } finally {for(File f:folder.listFiles()) f.delete();folder.delete();}
    }
    @Test public void lowSpaceAdmissionPreservesLastFallbackWithoutAReplacement() throws Exception {
        File folder=java.nio.file.Files.createTempDirectory("tv-low-space-").toFile();
        try {
            File fallback=new File(folder,"fallback.jpg");
            try(RandomAccessFile file=new RandomAccessFile(fallback,"rw")){file.setLength(2L*1024*1024);}
            File partial=new File(folder,"abandoned.tmp"); assertTrue(partial.createNewFile());
            assertFalse(DailyPhoto.prepareDownload(folder,()->63L*1024*1024));
            assertTrue(fallback.isFile()); assertFalse(partial.exists());
        } finally {for(File f:folder.listFiles()) f.delete();folder.delete();}
    }
}
