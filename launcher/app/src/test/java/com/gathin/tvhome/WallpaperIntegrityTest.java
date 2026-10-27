package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
import java.nio.file.*;
import java.security.*;

public class WallpaperIntegrityTest {
 @Test public void rejectsTraversalAndUnexpectedFiles(){
  assertTrue(WallpaperIntegrity.validName("abcdefghiJK-original.jpg"));
  assertTrue(WallpaperIntegrity.validName("abcdefghiJK-webp90.webp"));
  for(String s:new String[]{"../abcdefghiJK-original.jpg","abcdefghiJK-original.jpg.exe","/etc/passwd","a.jpg",null})assertFalse(WallpaperIntegrity.validName(s));
 }
 @Test public void detectsChangedImageBytes()throws Exception{
  Path dir=Files.createTempDirectory("wallpaper-test"),p=dir.resolve("abcdefghiJK-original.jpg");
  try{Files.write(p,new byte[]{1,2,3});StringBuilder hash=new StringBuilder();
   for(byte b:MessageDigest.getInstance("SHA-256").digest(new byte[]{1,2,3}))hash.append(String.format("%02x",b&255));
   assertTrue(WallpaperIntegrity.matches(p.toFile(),hash.toString()));
   Files.write(p,new byte[]{1,2,4});assertFalse(WallpaperIntegrity.matches(p.toFile(),hash.toString()));
  }finally{Files.deleteIfExists(p);Files.deleteIfExists(dir);}
 }
 @Test public void signatureBindsPublisherAndMetadata()throws Exception{
  KeyPairGenerator g=KeyPairGenerator.getInstance("RSA");g.initialize(2048);KeyPair key=g.generateKeyPair();
  byte[] bytes="catalogue".getBytes("UTF-8");Signature s=Signature.getInstance("SHA256withRSA");s.initSign(key.getPrivate());
  s.update("Home wallpaper catalogue v1\n".getBytes("UTF-8"));s.update(bytes);byte[] signed=s.sign();
  assertTrue(WallpaperIntegrity.verify(key.getPublic(),bytes,signed));
  assertFalse(WallpaperIntegrity.verify(key.getPublic(),"changed".getBytes("UTF-8"),signed));
  assertFalse(WallpaperIntegrity.verify(g.generateKeyPair().getPublic(),bytes,signed));
 }
 @Test public void validatesHashFormat(){assertFalse(WallpaperIntegrity.validHash("../"));assertFalse(WallpaperIntegrity.validHash(null));assertTrue(WallpaperIntegrity.validHash(String.join("",java.util.Collections.nCopies(64,"a"))));}
}
