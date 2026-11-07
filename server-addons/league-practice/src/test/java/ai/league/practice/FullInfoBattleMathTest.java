package ai.league.practice;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FullInfoBattleMathTest {
    private FullInfoBattleMath.Fighter mon(int hp,int max,int speed){return new FullInfoBattleMath.Fighter(hp,max,speed,1,0);}
    private FullInfoBattleMath.Move attack(String id,int damage,int priority){return FullInfoBattleMath.Move.attack(id,damage,priority);}
    private FullInfoBattleMath.Move heal(){return new FullInfoBattleMath.Move("roost",0,0,0,0,0,50,1,1,1,0,false,0);}
    private FullInfoBattleMath.Move agility(){return new FullInfoBattleMath.Move("agility",0,0,0,0,0,0,1,1,2,0,false,0);}
    @Test void priorityKoIsPreferredToSlowerAttackOrFutileHealing(){
        var result=FullInfoBattleMath.stay(mon(30,100,100),mon(20,100,150),List.of(heal(),attack("darkpulse",100,0),attack("suckerpunch",50,1)),List.of(attack("icebeam",100,0)),false);
        assertEquals("suckerpunch",result.move());
    }
    @Test void comparesBothPrioritiesRatherThanTreatingAnyPriorityAsFaster(){
        var result=FullInfoBattleMath.stay(mon(30,100,200),mon(20,100,100),List.of(attack("machpunch",50,1),attack("extremespeed",50,2)),List.of(attack("extremespeed",100,2)),false);
        assertEquals("extremespeed",result.move());
    }
    @Test void rejectsRecoilSuicideWhenSafeAttackWins(){
        var recoil=new FullInfoBattleMath.Move("doubleedge",90,0,1.0/3,0,0,0,1,1,1,1,false,0);
        var result=FullInfoBattleMath.stay(mon(25,100,150),mon(100,100,100),List.of(recoil,attack("earthquake",60,0)),List.of(attack("splash",0,0)),false);
        assertEquals("earthquake",result.move());
    }
    @Test void counterWithPriorityWinsEvenAfterTakingTheSwitchHit(){
        var enemy=mon(175,175,222);var attacks=List.of(attack("astralbarrage",180,0));
        double stay=FullInfoBattleMath.stay(mon(247,247,150),enemy,List.of(attack("dynamaxcannon",90,0)),attacks,false).score();
        double swap=FullInfoBattleMath.switchIn(mon(201,201,166),enemy,List.of(attack("suckerpunch",350,1)),List.of(attack("astralbarrage",75,0)),0,false);
        assertTrue(swap>stay+20);
    }
    @Test void switchingIntoLethalHazardsIsNotAFreeCounter(){
        double safe=FullInfoBattleMath.switchIn(mon(100,100,150),mon(100,100,100),List.of(attack("attack",150,0)),List.of(attack("attack",30,0)),0,false);
        double unsafe=FullInfoBattleMath.switchIn(mon(20,100,150),mon(100,100,100),List.of(attack("attack",150,0)),List.of(attack("attack",30,0)),25,false);
        assertTrue(safe>unsafe+100);
    }
    @Test void fullHpRecoveryDoesNotBeatAttacking(){
        var result=FullInfoBattleMath.stay(mon(100,100,150),mon(100,100,100),List.of(heal(),attack("attack",60,0)),List.of(attack("attack",20,0)),false);
        assertEquals("attack",result.move());
    }
    @Test void necessaryRecoveryIsKept(){
        var result=FullInfoBattleMath.stay(mon(33,100,150),mon(100,100,100),List.of(heal(),attack("attack",45,0)),List.of(attack("attack",30,0)),false);
        assertEquals("roost",result.move());
    }
    @Test void noSpeedBoostWhenAlreadyFaster(){
        var result=FullInfoBattleMath.stay(mon(100,100,200),mon(100,100,100),List.of(agility(),attack("attack",60,0)),List.of(attack("attack",20,0)),false);
        assertEquals("attack",result.move());
    }
    @Test void trickRoomReversesSpeedWithoutReversingPriority(){
        var own=List.of(attack("normal",100,0),attack("priority",100,1));
        var foe=List.of(attack("attack",100,0));
        assertEquals("priority",FullInfoBattleMath.stay(mon(50,100,200),mon(50,100,100),own,foe,true).move());
    }
    @Test void considersSuckerPunchFailureAgainstStatusAndSwitches(){
        var sucker=new FullInfoBattleMath.Move("suckerpunch",100,1,0,0,0,0,1,1,1,1,true,0);
        var own=List.of(sucker,attack("darkpulse",100,0));var foe=List.of(attack("status",0,0));
        assertEquals("darkpulse",FullInfoBattleMath.stay(mon(100,100,150),mon(50,100,100),own,foe,false).move());
        double onSwitch=FullInfoBattleMath.opponentSwitch(mon(100,100,150),mon(50,100,100),own,foe,sucker,0,false);
        assertTrue(Double.isFinite(onSwitch));
    }
}
