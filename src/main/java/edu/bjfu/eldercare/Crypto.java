package edu.bjfu.eldercare;
import org.springframework.stereotype.Component;
import javax.crypto.*;import javax.crypto.spec.*;import java.security.*;import java.util.*;import java.nio.charset.StandardCharsets;
@Component public class Crypto {
 private final byte[] key;
 public Crypto(){String k=System.getenv("FIELD_KEY");if(k==null||k.length()!=64)throw new IllegalStateException("FIELD_KEY 必须为 32 字节十六进制密钥");key=HexFormat.of().parseHex(k);}
 public String hash(String s){try{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return HexFormat.of().formatHex(m.doFinal(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public String encrypt(String s){try{byte[] iv=new byte[12];new SecureRandom().nextBytes(iv);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,iv));byte[] enc=c.doFinal(s.getBytes(StandardCharsets.UTF_8));return Base64.getEncoder().encodeToString(iv)+":"+Base64.getEncoder().encodeToString(enc);}catch(Exception e){throw new IllegalStateException(e);}}
}
