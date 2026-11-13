package edu.bjfu.eldercare;
import org.springframework.stereotype.Component;import org.springframework.scheduling.annotation.Scheduled;import org.springframework.transaction.annotation.Transactional;import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component @ConditionalOnProperty(name="app.jobs",havingValue="true",matchIfMissing=true)
public class Jobs{
 private final Store db;private final Simulator simulator;public Jobs(Store d,Simulator simulator){db=d;this.simulator=simulator;}
 @Scheduled(fixedDelay=30000,initialDelay=30000) @Transactional public void tick(){db.update("SET @actor='scheduler'");db.call("expire_plans");db.call("escalate_events");db.call("remind_assessments");simulator.tick();}
}
