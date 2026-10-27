import java.nio.file.*;
import java.security.*;
/** Offline catalogue signing. The private key stays on the PC, never on the TV. */
public final class SignWallpaperCatalogue {
 public static void main(String[] args)throws Exception {
  char[] password="android".toCharArray();
  KeyStore store=KeyStore.getInstance(new java.io.File(args[0]),password);
  Signature signer=Signature.getInstance("SHA256withRSA");
  signer.initSign((PrivateKey)store.getKey("cosy",password));
  signer.update("Home wallpaper catalogue v1\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
  signer.update(Files.readAllBytes(Paths.get(args[1])));
  Files.write(Paths.get(args[2]),signer.sign());
 }
}
