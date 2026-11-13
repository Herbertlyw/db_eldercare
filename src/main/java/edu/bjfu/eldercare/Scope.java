package edu.bjfu.eldercare;

import org.springframework.stereotype.Service;
import java.util.*;
import static edu.bjfu.eldercare.Store.id;

@Service
public class Scope {
    private final Store db;
    public Scope(Store db) { this.db=db; }
    public boolean regulator(Auth.User u) { return Set.of("STREET","REGULATOR").contains(u.role()); }
    public String communityFilter(Auth.User u,String column,List<Object> args) {
        args.add(regulator(u)?u.id():u.community());
        return regulator(u)?column+" IN(SELECT community_id FROM account_scope WHERE account_id=?)":column+"=?";
    }
    public void community(Auth.User u,long community) {
        if(regulator(u)?db.count("SELECT COUNT(*) FROM account_scope WHERE account_id=? AND community_id=?",u.id(),community)==0:u.community()!=community) throw new Forbidden();
    }
    public String elderFilter(Auth.User u,List<Object> p) {
        if(u.role().equals("FAMILY")) {p.add(u.id());return "EXISTS(SELECT 1 FROM guardian g WHERE g.elder_id=e.id AND g.account_id=? AND g.active=1)";}
        if(u.role().equals("CAREGIVER")) {
            p.add(u.id());p.add(u.id());p.add(u.id());
            return "(EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.id AND c.account_id=? AND d.status IN('WAITING','ACCEPTED')) OR EXISTS(SELECT 1 FROM v_plan_context p JOIN caregiver c ON c.id=p.caregiver_id WHERE p.elder_id=e.id AND c.account_id=? AND p.status='ACTIVE' AND p.starts_at<=NOW() AND p.ends_at>NOW()) OR EXISTS(SELECT 1 FROM emergency z WHERE z.elder_id=e.id AND z.responded_by=?))";
        }
        if(u.role().equals("MANAGER")) {p.add(u.organization());return "EXISTS(SELECT 1 FROM service_order o JOIN v_dispatch_context d ON d.order_id=o.id JOIN caregiver c ON c.id=d.caregiver_id WHERE o.elder_id=e.id AND c.organization_id=?)";}
        return communityFilter(u,"e.community_id",p);
    }
    public void elder(Auth.User u,long elder) {List<Object> args=new ArrayList<>();String f=elderFilter(u,args);args.add(elder);if(db.count("SELECT COUNT(*) FROM elder e WHERE "+f+" AND e.id=?",args.toArray())==0)throw new Forbidden();}
    public void worker(Auth.User u,long worker) {
        var c=db.one("SELECT c.*,a.community_id FROM caregiver c JOIN account a ON a.id=c.account_id WHERE c.id=?",worker);
        if(u.role().equals("MANAGER")){if(!Objects.equals(u.organization(),id(c,"organization_id")))throw new Forbidden();}
        else if(u.role().equals("CAREGIVER")){if(id(c,"account_id")!=u.id())throw new Forbidden();}
        else community(u,id(c,"community_id"));
    }
    public Map<String,Object> order(Auth.User u,long order) {
        var o=db.one("SELECT * FROM service_order WHERE id=?",order);
        if(u.role().equals("MANAGER")||u.role().equals("CAREGIVER")) {
            if(db.count("SELECT COUNT(*) FROM v_dispatch_context d JOIN caregiver c ON c.id=d.caregiver_id WHERE d.order_id=? AND "+(u.role().equals("MANAGER")?"c.organization_id=?":"c.account_id=?"),order,u.role().equals("MANAGER")?u.organization():u.id())==0)throw new Forbidden();
        } else if(u.role().equals("FAMILY"))elder(u,id(o,"elder_id")); else community(u,id(o,"community_id"));
        return o;
    }
    public Map<String,Object> claim(Auth.User u,long claim) {
        var c=db.one("SELECT * FROM claim WHERE id=?",claim);
        if(u.role().equals("FAMILY"))elder(u,id(c,"elder_id"));else community(u,id(c,"community_id"));return c;
    }
    public void organization(Auth.User u,long organization) {
        if(u.role().equals("MANAGER")){if(!Objects.equals(u.organization(),organization))throw new Forbidden();}
        else community(u,db.count("SELECT community_id FROM organization WHERE id=?",organization));
    }
    public Map<String,Object> inspection(Auth.User u,long inspection) {
        var i=db.one("SELECT i.*,a.community_id FROM inspection i JOIN account a ON a.id=i.inspector_id WHERE i.id=?",inspection);
        if(i.get("order_id")!=null)order(u,id(i,"order_id"));else worker(u,id(i,"caregiver_id"));return i;
    }
}
