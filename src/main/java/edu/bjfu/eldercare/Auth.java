package edu.bjfu.eldercare;
import org.springframework.stereotype.Service;import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;import org.springframework.transaction.support.TransactionTemplate;import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;import java.util.*;import java.util.concurrent.*;
@Service public class Auth {
 public record User(long id,String name,String role,long community,Long organization){}
 private record Session(User user,Instant expires){}
 private final ConcurrentHashMap<String,Session> sessions=new ConcurrentHashMap<>();
 private final Store db;private final TransactionTemplate tx;private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
 public Auth(Store db,PlatformTransactionManager tm){this.db=db;tx=new TransactionTemplate(tm);}
 public String password(String raw){return encoder.encode(raw);}
 public Map<String,Object> login(String username,String password){
 if(username==null||password==null||username.length()>50||password.length()>72)throw new Rule("用户名或密码错误");
 User u=tx.execute(status->{var rs=db.rows("SELECT * FROM account WHERE username=? FOR UPDATE",username);if(rs.isEmpty()){encoder.matches(password,"$2a$12$vJeA1taUYd6WjjfayB8Ue.6SAnx2b6luEaoPPkAzcKc4F6DbsHvRS");return null;}var a=rs.get(0);
 if(!Boolean.TRUE.equals(a.get("active")))return null;
 Object locked=a.get("locked_until");if(locked!=null&&((java.sql.Timestamp)locked).toLocalDateTime().isAfter(LocalDateTime.now()))return null;
 if(!encoder.matches(password,(String)a.get("password_hash"))){db.update("UPDATE account SET failures=failures+1,locked_until=IF(failures>=5,NOW()+INTERVAL 15 MINUTE,NULL) WHERE id=?",a.get("id"));return null;}
 db.update("UPDATE account SET failures=0,locked_until=NULL WHERE id=?",a.get("id"));return new User(Store.id(a,"id"),username,(String)a.get("role"),Store.id(a,"community_id"),a.get("organization_id")==null?null:Store.id(a,"organization_id"));});
 if(u==null)throw new Rule("用户名或密码错误，或账户暂时锁定");
 sessions.entrySet().removeIf(e->e.getValue().expires.isBefore(Instant.now()));String token=UUID.randomUUID()+"-"+UUID.randomUUID();sessions.put(token,new Session(u,Instant.now().plusSeconds(1800)));return Map.of("token",token,"user",u,"expiresIn",1800);
 }
 public User require(String auth){if(auth==null||!auth.startsWith("Bearer "))throw new Unauthorized();Session s=sessions.get(auth.substring(7));if(s==null||s.expires.isBefore(Instant.now()))throw new Unauthorized();return s.user;}
 public void logout(String auth){if(auth!=null)sessions.remove(auth.replace("Bearer ",""));}
 public static void role(User u,String...roles){if(!Arrays.asList(roles).contains(u.role))throw new Forbidden();}
}
class Unauthorized extends RuntimeException{}
class Forbidden extends RuntimeException{}
