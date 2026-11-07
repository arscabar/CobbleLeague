package ai.league.practice;

import java.util.List;

/** Bounded two-turn maximin search. Works on immutable numbers, never on live battle objects. */
public final class FullInfoBattleMath {
    private FullInfoBattleMath() {}
    public record Fighter(int hp,int maximum,int speed,double value,int residual) {}
    public record Move(String id,int damage,int priority,double recoil,int selfCost,double drain,int heal,
                       double attackBoost,double specialBoost,double speedBoost,int category,boolean conditional,
                       double utility) {
        public static Move attack(String id,int damage,int priority) {
            return new Move(id,damage,priority,0,0,0,0,1,1,1,1,false,0);
        }
    }
    private record State(double ours,double theirs,double ourAttack,double ourSpecial,double ourSpeed,
                         double theirAttack,double theirSpecial,double theirSpeed,double utility,String ourLast,String theirLast) {}
    public record Evaluation(String move,double score) {}

    public static Evaluation stay(Fighter us,Fighter foe,List<Move> ourMoves,List<Move> foeMoves,boolean trickRoom) {
        return stayWithFirst(us,foe,ourMoves,foeMoves,trickRoom,null);
    }
    public static Evaluation stayWithFirst(Fighter us,Fighter foe,List<Move> ourMoves,List<Move> foeMoves,boolean trickRoom,String firstId) {
        State start=new State(us.hp,foe.hp,1,1,1,1,1,1,0,"","");
        double best=-Double.MAX_VALUE;String move="";
        for(Move first:ourMoves) {
            if(firstId!=null&&!first.id.equals(firstId))continue;
            double worst=Double.MAX_VALUE;
            for(Move enemy:foeMoves) {
                State next=turn(start,first,enemy,us,foe,trickRoom);
                double value=terminal(next)?score(next,us,foe):bestNext(next,us,foe,ourMoves,foeMoves,trickRoom);
                worst=Math.min(worst,value);
            }
            if(worst>best+0.0001){best=worst;move=first.id;}
        }
        return new Evaluation(move,best);
    }
    public static double opponentSwitch(Fighter us,Fighter foe,List<Move> ourMoves,List<Move> foeMoves,Move first,int hazards,boolean trickRoom){
        State start=new State(us.hp,foe.hp-Math.max(0,hazards),1,1,1,1,1,1,0,"","");
        if(terminal(start))return score(start,us,foe);
        State hit=act(start,first,true,us,foe,false);
        if(terminal(hit))return score(hit,us,foe);
        return bestNext(residual(hit,us,foe),us,foe,ourMoves,foeMoves,trickRoom);
    }

    public static double switchIn(Fighter us,Fighter foe,List<Move> ourMoves,List<Move> foeMoves,int hazards,boolean trickRoom) {
        double worst=Double.MAX_VALUE;
        for(Move enemy:foeMoves) {
            State initial=new State(us.hp-Math.max(0,hazards),foe.hp,1,1,1,1,1,1,0,"","");
            State entry=initial.ours<=0?initial:act(initial,enemy,false,us,foe,true);
            if(!terminal(entry))entry=residual(entry,us,foe);
            double result=terminal(entry)?score(entry,us,foe):bestNext(entry,us,foe,ourMoves,foeMoves,trickRoom);
            worst=Math.min(worst,result);
        }
        return worst;
    }
    private static double bestNext(State start,Fighter us,Fighter foe,List<Move> own,List<Move> enemy,boolean room) {
        double best=-Double.MAX_VALUE;
        for(Move move:own) {
            double worst=Double.MAX_VALUE;
            for(Move response:enemy)worst=Math.min(worst,score(turn(start,move,response,us,foe,room),us,foe));
            best=Math.max(best,worst);
        }
        return best;
    }
    private static State turn(State s,Move own,Move enemy,Fighter us,Fighter foe,boolean room) {
        boolean first=own.priority!=enemy.priority?own.priority>enemy.priority:
                room?us.speed*s.ourSpeed<foe.speed*s.theirSpeed:us.speed*s.ourSpeed>foe.speed*s.theirSpeed;
        // Speed ties use the unfavorable order; never assume the random tie is won.
        State next;
        if(first){next=act(s,own,true,us,foe,enemy.damage>0);if(!terminal(next))next=act(next,enemy,false,us,foe,own.damage>0);}
        else{next=act(s,enemy,false,us,foe,own.damage>0);if(!terminal(next))next=act(next,own,true,us,foe,enemy.damage>0);}
        return terminal(next)?next:residual(next,us,foe);
    }
    private static State act(State s,Move move,boolean own,Fighter us,Fighter foe,boolean targetAttacks) {
        double hp=own?s.ours:s.theirs,target=own?s.theirs:s.ours;
        double atk=own?s.ourAttack:s.theirAttack,spa=own?s.ourSpecial:s.theirSpecial,spe=own?s.ourSpeed:s.theirSpeed;
        double damage=move.conditional&&!targetAttacks?0:move.damage*(move.category==1?atk:move.category==2?spa:1);
        double dealt=Math.max(0,Math.min(target,damage));target-=dealt;
        hp=Math.min(own?us.maximum:foe.maximum,hp+move.heal+dealt*move.drain);
        if(dealt>0)hp-=Math.floor(dealt*move.recoil)+move.selfCost;
        atk=Math.min(4,atk*move.attackBoost);spa=Math.min(4,spa*move.specialBoost);spe=Math.min(4,spe*move.speedBoost);
        double utility=s.utility+(move.id.equals(own?s.ourLast:s.theirLast)?0:(own?1:-1)*move.utility)-(own?1:0);
        if(own&&move.conditional&&!targetAttacks)utility-=8;
        return own?new State(hp,target,atk,spa,spe,s.theirAttack,s.theirSpecial,s.theirSpeed,utility,move.id,s.theirLast):
                new State(target,hp,s.ourAttack,s.ourSpecial,s.ourSpeed,atk,spa,spe,utility,s.ourLast,move.id);
    }
    private static State residual(State s,Fighter us,Fighter foe){return new State(Math.min(us.maximum,s.ours+us.residual),Math.min(foe.maximum,s.theirs+foe.residual),s.ourAttack,s.ourSpecial,s.ourSpeed,s.theirAttack,s.theirSpecial,s.theirSpeed,s.utility,s.ourLast,s.theirLast);}
    private static boolean terminal(State s){return s.ours<=0||s.theirs<=0;}
    private static double score(State s,Fighter us,Fighter foe) {
        double ourLoss=(us.hp-Math.max(0,s.ours))/Math.max(1.0,us.maximum);
        double theirLoss=(foe.hp-Math.max(0,s.theirs))/Math.max(1.0,foe.maximum);
        return 90*(theirLoss*foe.value-ourLoss*us.value)+(s.theirs<=0?140*foe.value:0)-(s.ours<=0?165*us.value:0)+s.utility;
    }
}
