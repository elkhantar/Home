package com.gathin.tvhome;
import java.io.*;
import java.security.*;

/** File names and payload hashes are restricted even after signature verification. */
final class WallpaperIntegrity {
    static boolean verify(PublicKey key,byte[] data,byte[] signature)throws Exception {
        if(data.length>1024*1024 || signature.length>8192)return false;
        Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(key);
        verifier.update("Home wallpaper catalogue v1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        verifier.update(data);return verifier.verify(signature);
    }
    static boolean validName(String s){return s!=null && s.matches("[A-Za-z0-9_-]{11}-(original\\.jpg|webp90\\.webp)");}
    static boolean validHash(String s){return s!=null && s.matches("[a-f0-9]{64}");}
    static byte[] readVerified(File file,String expected)throws Exception {
        if(!validName(file.getName()) || !validHash(expected) || !file.isFile() || file.length()<1 || file.length()>6*1024*1024)return null;
        byte[] bytes;try(InputStream in=new FileInputStream(file)){bytes=SafeHttp.read(in,6*1024*1024,Long.MAX_VALUE);}
        StringBuilder actual=new StringBuilder();
        for(byte v:MessageDigest.getInstance("SHA-256").digest(bytes))actual.append(String.format(java.util.Locale.ROOT,"%02x",v&255));
        return actual.toString().equals(expected)?bytes:null;
    }
    static boolean matches(File file,String expected)throws Exception {return readVerified(file,expected)!=null;}
}
