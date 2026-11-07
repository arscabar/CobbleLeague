package ai.league.practice;

import com.cobblemon.mod.common.api.battles.model.PokemonBattle;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.moves.categories.DamageCategories;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.battles.*;
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.entity.npc.NPCEntity;
import com.cobblemon.mod.common.Cobblemon;
import com.google.gson.JsonObject;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Full-party information, two-turn survival and counter preservation for this addon's single battles. */
public final class PracticePartyPlanner {
    private static final Map<UUID,Map<String,Object>> LAST=new ConcurrentHashMap<>();
    private static final Map<UUID,SwitchMemory> SWITCHES=new ConcurrentHashMap<>();
    private static final Set<String> HEAL=Set.of("recover","roost","slackoff","softboiled","milkdrink","shoreup","synthesis","morningsun","moonlight");
    private static final Set<String> CONTACT=Set.of("doubleedge","flareblitz","bravebird","wavecrash","headsmash","wildcharge","volttackle","woodhammer","headcharge","closecombat","dragonclaw","suckerpunch","extremespeed","uturn","flipturn","knockoff","surgingstrikes","wickedblow");
    public record Plan(UUID switchTo,String move,double score,String reason) {}
    private record SwitchMemory(int turn,UUID opponent) {}
    private record Pair(List<FullInfoBattleMath.Move> ours,List<FullInfoBattleMath.Move> theirs,int ourBest,int theirBest) {}
    private final PracticeTacticalBattleAI math=PracticeTacticalBattleAI.INSTANCE;
    private final PokemonBattle battle;
    private final PracticeBattleFacts facts;
    private final BattlePokemon current,opponent;
    private final List<BattlePokemon> allies,enemies;
    private final ShowdownMoveset request;
    private final String baseline;
    private final double baselineScore;
    private final boolean tera;
    private final Map<String,Pair> pairs=new HashMap<>();
    private final Map<UUID,Double> values=new HashMap<>();

    private PracticePartyPlanner(BattlePokemon current,BattlePokemon opponent,List<BattlePokemon> allies,
            List<BattlePokemon> enemies,ShowdownMoveset request,String baseline,double baselineScore,boolean tera,PokemonBattle battle) {
        this.current=current;this.opponent=opponent;this.allies=allies;this.enemies=enemies;this.request=request;
        this.baseline=baseline;this.baselineScore=baselineScore;this.tera=tera;this.battle=battle;this.facts=PracticeBattleFacts.read(battle);
    }
    public static Map<String,Object> latest(UUID battle){return LAST.getOrDefault(battle,Map.of());}
    public static void release(UUID battle){LAST.remove(battle);SWITCHES.remove(battle);}
    public static BattlePokemon replacement(ActiveBattlePokemon slot,BattleSide side,PokemonBattle battle){
        var candidates=slot.getActor().getPokemonList().stream().filter(BattlePokemon::canBeSentOut).limit(6).toList();
        var enemies=Arrays.stream(side.getOppositeSide().getActors()).flatMap(a->a.getPokemonList().stream()).filter(p->p.getHealth()>0&&!p.getGone()).limit(6).toList();
        if(candidates.isEmpty()||enemies.isEmpty())return candidates.isEmpty()?null:candidates.getFirst();
        var active=side.getOppositeSide().getActivePokemon().stream().map(ActiveBattlePokemon::getBattlePokemon).filter(Objects::nonNull).filter(p->p.getHealth()>0).findFirst().orElse(null);
        var planner=new PracticePartyPlanner(candidates.getFirst(),active==null?enemies.getFirst():active,candidates,enemies,null,"",0,false,battle);
        BattlePokemon best=candidates.getFirst();double score=-Double.MAX_VALUE;
        for(var mon:candidates){double sum=0,now=0;for(var foe:enemies){Pair p=planner.pair(mon,foe);var original=planner.fighter(mon);
            var entered=new FullInfoBattleMath.Fighter(Math.max(0,original.hp()-planner.entryHazards(mon)),original.maximum(),original.speed(),original.value(),original.residual());
            double value=FullInfoBattleMath.stay(entered,planner.fighter(foe),p.ours,p.theirs,planner.facts.room()).score();sum+=value;if(foe==active)now=value;}
            double value=active==null?sum/enemies.size():now*.8+sum/enemies.size()*.2;if(value>score){score=value;best=mon;}
        }
        return best;
    }
    public static Plan choose(ActiveBattlePokemon slot,BattleSide side,ShowdownMoveset request,String baseline,
            double baselineScore,boolean tera,PokemonBattle battle) {
        if(side.getActivePokemon().size()!=1)return null;
        var target=side.getOppositeSide().getActivePokemon().stream().map(ActiveBattlePokemon::getBattlePokemon).filter(Objects::nonNull).filter(p->p.getHealth()>0).findFirst().orElse(null);
        if(target==null)return null;
        var all=slot.getActor().getPokemonList().stream().filter(p->p.getHealth()>0&&!p.getGone()).limit(6).toList();
        var foes=Arrays.stream(side.getOppositeSide().getActors()).flatMap(a->a.getPokemonList().stream()).filter(p->p.getHealth()>0&&!p.getGone()).limit(6).toList();
        return new PracticePartyPlanner(slot.getBattlePokemon(),target,all,foes,request,baseline,baselineScore,tera,battle).plan();
    }
    private Plan plan() {
        List<Map<String,Object>> matrix=new ArrayList<>();
        for(var ally:allies)for(var enemy:enemies){Pair p=pair(ally,enemy);matrix.add(Map.of("ally",ally.getEffectedPokemon().getSpecies().getName(),"enemy",enemy.getEffectedPokemon().getSpecies().getName(),"outgoing",p.ourBest,"incoming",p.theirBest));}
        for(var ally:allies){int unique=0;for(var enemy:enemies){
            long answers=allies.stream().filter(a->{Pair p=pair(a,enemy);return p.ourBest>=enemy.getHealth()&&p.theirBest*2<a.getHealth();}).count();
            Pair p=pair(ally,enemy);if(answers==1&&p.ourBest>=enemy.getHealth()&&p.theirBest*2<ally.getHealth())unique++;
        }values.put(ally.getUuid(),1+Math.min(3,unique)*.15);}
        Pair active=pair(current,opponent);
        var own=fighter(current);var foe=fighter(opponent);
        var stay=FullInfoBattleMath.stay(own,foe,active.ours,active.theirs,facts.room());
        if(enemies.size()>1){
            FullInfoBattleMath.Evaluation robust=null;
            for(var first:active.ours){
                var evaluated=FullInfoBattleMath.stayWithFirst(own,foe,active.ours,active.theirs,facts.room(),first.id());
                double risk=Double.MAX_VALUE;
                for(var counter:enemies)if(counter!=opponent){Pair p=pair(current,counter);var hit=p.ours.stream().filter(m->m.id().equals(first.id())).findFirst().orElse(null);
                    if(hit!=null)risk=Math.min(risk,FullInfoBattleMath.opponentSwitch(own,fighter(counter),p.ours,p.theirs,hit,entryHazards(counter),facts.room()));
                }
                double score=risk==Double.MAX_VALUE?evaluated.score():Math.min(evaluated.score(),evaluated.score()*.75+risk*.25);
                if(robust==null||score>robust.score())robust=new FullInfoBattleMath.Evaluation(first.id(),score);
            }
            if(robust!=null)stay=robust;
        }
        double best=stay.score();UUID switchTo=null;List<Map<String,Object>> alternatives=new ArrayList<>();
        if(!request.getTrapped())for(var candidate:allies){
            if(candidate==current||!candidate.canBeSentOut())continue;
            Pair p=pair(candidate,opponent);int entry=entryHazards(candidate);
            double value=FullInfoBattleMath.switchIn(fighter(candidate),foe,p.ours,p.theirs,entry,facts.room())-10;
            SwitchMemory last=SWITCHES.get(battle.getBattleId());
            if(last!=null&&last.opponent.equals(opponent.getUuid())&&battle.getTurn()-last.turn<=2)value-=20;
            // Account for losing a safe future counter by heavily chipping it now.
            alternatives.add(Map.of("pokemon",candidate.getEffectedPokemon().getSpecies().getName(),"score",value,"entryHazards",entry));
            if(value>best+8){best=value;switchTo=candidate.getUuid();}
        }
        String reason=switchTo==null?"현재 대면: 두 턴 중 최악의 응수를 비교해 기술 선택":"교체 진입 피해와 다음 턴 응수를 포함한 생존·처치 기대가 더 높음";
        var report=new LinkedHashMap<String,Object>();report.put("turn",battle.getTurn());report.put("allies",allies.size());report.put("opponents",enemies.size());
        report.put("mode","full-party-two-turn-maximin");report.put("reason",reason);report.put("stayScore",stay.score());report.put("chosenScore",best);
        report.put("activeOpponent",opponent.getEffectedPokemon().getSpecies().getName());report.put("activeSelf",current.getEffectedPokemon().getSpecies().getName());
        report.put("opponentParty",enemies.stream().map(PracticePartyPlanner::snapshot).toList());
        report.put("move",stay.move());report.put("switchTo",switchTo==null?"":switchTo.toString());report.put("candidates",alternatives);report.put("matchups",matrix);
        LAST.put(battle.getBattleId(),report);
        if(switchTo!=null)SWITCHES.put(battle.getBattleId(),new SwitchMemory(battle.getTurn(),opponent.getUuid()));
        return new Plan(switchTo,stay.move(),best,reason);
    }
    private FullInfoBattleMath.Fighter fighter(BattlePokemon p){
        int speed=math.plannerSpeed(p,battle),residual=0;String item=item(p),ability=ability(p),weather=facts.text("weather");
        if(!PracticeBattleFacts.bool(facts.data,"weatherSuppressed")&&(ability.equals("swiftswim")&&Set.of("raindance","primordialsea").contains(weather)||ability.equals("chlorophyll")&&Set.of("sunnyday","desolateland").contains(weather)||ability.equals("sandrush")&&weather.equals("sandstorm")||ability.equals("slushrush")&&Set.of("hail","snow").contains(weather)))speed*=2;
        if(battle!=null&&PracticeBattleFacts.bool(facts.side(p.getActor().getShowdownId()),"tailwind"))speed*=2;
        if(item.equals("leftovers"))residual+=p.getMaxHealth()/16;
        if(item.equals("blacksludge"))residual+=hasType(p,"poison")?p.getMaxHealth()/16:-p.getMaxHealth()/8;
        var status=p.getEffectedPokemon().getStatus();
        if(status!=null&&!ability.equals("magicguard")){
            String name=status.getStatus().getShowdownName();
            if(ability.equals("poisonheal")&&Set.of("psn","tox").contains(name))residual+=p.getMaxHealth()/8;
            else if(name.equals("brn"))residual-=p.getMaxHealth()/16;else if(Set.of("psn","tox").contains(name))residual-=p.getMaxHealth()/8;
        }
        return new FullInfoBattleMath.Fighter(p.getHealth(),p.getMaxHealth(),speed,values.getOrDefault(p.getUuid(),1.0),residual);
    }
    private Pair pair(BattlePokemon a,BattlePokemon b){return pairs.computeIfAbsent(a.getUuid()+":"+b.getUuid(),key->{
        var own=profiles(a,b,request!=null&&a==current, a==current&&tera);var enemy=profiles(b,a,false,false);
        return new Pair(own,enemy,own.stream().mapToInt(FullInfoBattleMath.Move::damage).max().orElse(0),enemy.stream().mapToInt(FullInfoBattleMath.Move::damage).max().orElse(0));
    });}
    private List<FullInfoBattleMath.Move> profiles(BattlePokemon attacker,BattlePokemon defender,boolean active,boolean attackerTera) {
        var result=new ArrayList<FullInfoBattleMath.Move>();
        Set<String> allowed=new HashSet<>();
        if(active)request.getMoves().stream().filter(InBattleMove::canBeUsed).forEach(m->allowed.add(id(m.getId())));
        else if(battle!=null&&attacker.getActor().getRequest()!=null&&attacker.getActor().getActivePokemon().stream().anyMatch(s->s.getBattlePokemon()==attacker)) {
            var req=attacker.getActor().getRequest().getActive();
            if(req!=null&&!req.isEmpty())req.getFirst().getMoves().stream().filter(InBattleMove::canBeUsed).forEach(m->allowed.add(id(m.getId())));
        }
        for(var move:attacker.getEffectedPokemon().getMoveSet()){
            String name=id(move.getName());if(move.getCurrentPp()<=0||(!allowed.isEmpty()&&!allowed.contains(name)))continue;
            var t=move.getTemplate();boolean status=t.getDamageCategory().equals(DamageCategories.INSTANCE.getSTATUS());
            int damage=status?0:math.estimateMoveDamage(attacker,defender,name,attackerTera,defender==current&&tera,battle);
            damage=fieldDamage(attacker,defender,name,damage);
            String ability=ability(attacker),item=item(attacker);
            double recoil=Set.of("doubleedge","flareblitz","bravebird","woodhammer","wavecrash","headcharge","volttackle").contains(name)?1.0/3:
                    Set.of("wildcharge").contains(name)?.25:"headsmash".equals(name)?.5:0;
            int cost="lifeorb".equals(item)?Math.max(1,attacker.getMaxHealth()/10):0;
            if("chloroblast".equals(name)||"steelbeam".equals(name)||"mindblown".equals(name))cost+=attacker.getMaxHealth()/2;
            if("struggle".equals(name))cost+=attacker.getMaxHealth()/4;
            if("rockhead".equals(ability))recoil=0;
            if("magicguard".equals(ability)){recoil=0;cost=0;}
            if("rockyhelmet".equals(item(defender))&&CONTACT.contains(name)&&!"magicguard".equals(ability))cost+=attacker.getMaxHealth()/6;
            double drain=Set.of("drainingkiss","oblivionwing").contains(name)?.75:Set.of("gigadrain","drainpunch","hornleech","bitterblade","paraboliccharge","matchagotcha").contains(name)?.5:0;
            int heal=HEAL.contains(name)?attacker.getMaxHealth()/2:0;
            double atk=1,spa=1,spe=1;
            switch(name){case "swordsdance"->atk=boost(attacker,Stats.ATTACK,2);case "nastyplot"->spa=boost(attacker,Stats.SPECIAL_ATTACK,2);
                case "dragondance"->{atk=boost(attacker,Stats.ATTACK,1);spe=boost(attacker,Stats.SPEED,1);}case "quiverdance"->{spa=boost(attacker,Stats.SPECIAL_ATTACK,1);spe=boost(attacker,Stats.SPEED,1);}
                case "calmmind"->spa=boost(attacker,Stats.SPECIAL_ATTACK,1);case "agility","rockpolish","autotomize"->spe=boost(attacker,Stats.SPEED,2);
                case "shiftgear"->{atk=boost(attacker,Stats.ATTACK,1);spe=boost(attacker,Stats.SPEED,2);}case "bulkup"->atk=boost(attacker,Stats.ATTACK,1);}
            double utility=0;
            if(status&&heal==0&&atk==1&&spa==1&&spe==1){
                // Retain existing validated support scoring; unsupported status moves are not fabricated attacks.
                if(active&&name.equals(baseline)&&baselineScore>0)utility=Math.min(24,baselineScore*.24);
                if(Set.of("stealthrock","spikes","toxicspikes","stickyweb").contains(name))utility=hazardUtility(attacker,name);
            }
            int category=Set.of("seismictoss","nightshade","superfang","ruination","naturesmadness","endeavor","finalgambit").contains(name)?0:t.getDamageCategory().equals(DamageCategories.INSTANCE.getPHYSICAL())?1:2;
            int priority=math.plannerPriority(attacker,name);
            if(name.equals("grassyglide")&&!facts.text("terrain").equals("grassyterrain"))priority=t.getPriority();
            result.add(new FullInfoBattleMath.Move(name,damage,priority,recoil,cost,drain,heal,atk,spa,spe,category,"suckerpunch".equals(name),utility));
        }
        if(result.isEmpty())result.add(FullInfoBattleMath.Move.attack("struggle",0,0));
        return result;
    }
    private double hazardUtility(BattlePokemon attacker,String name){
        if(battle==null)return 10;
        JsonObject target=facts.side(attacker.getActor().getSide().getOppositeSide().getActors()[0].getShowdownId());
        boolean exists=switch(name){case "stealthrock"->PracticeBattleFacts.bool(target,"rock");case "stickyweb"->PracticeBattleFacts.bool(target,"web");case "spikes"->PracticeBattleFacts.number(target,"spikes")>=3;default->PracticeBattleFacts.number(target,"toxic")>=2;};
        return exists?0:Math.min(24,4.0*enemies.size());
    }
    private int entryHazards(BattlePokemon p){
        if(battle==null||"heavydutyboots".equals(item(p))||"magicguard".equals(ability(p)))return 0;
        JsonObject side=facts.side(p.getActor().getShowdownId());double damage=0;
        if(PracticeBattleFacts.bool(side,"rock")){
            double eff=1;for(var type:p.getEffectedPokemon().getTypes())eff*=PracticeBattleMath.getSingleTypeEffectiveness("rock",type.getName());
            damage+=p.getMaxHealth()*eff/8;
        }
        int spikes=PracticeBattleFacts.number(side,"spikes");if(grounded(p)&&spikes>0)damage+=p.getMaxHealth()*(spikes>=3?.25:spikes==2?1.0/6:.125);
        return (int)Math.floor(damage);
    }
    private int fieldDamage(BattlePokemon a,BattlePokemon b,String move,int damage){
        if(damage==0)return 0;var t=Moves.INSTANCE.getByName(move);if(t==null)return damage;String type=t.getElementalType().getName();double multiplier=1;
        if(!PracticeBattleFacts.bool(facts.data,"weatherSuppressed")){
            String weather=facts.text("weather");boolean rain=Set.of("raindance","primordialsea").contains(weather),sun=Set.of("sunnyday","desolateland").contains(weather);
            if(rain&&"fire".equals(type))multiplier*=weather.equals("primordialsea")?0:.5;if(rain&&"water".equals(type))multiplier*=1.5;
            if(sun&&"water".equals(type))multiplier*=weather.equals("desolateland")?0:.5;if(sun&&"fire".equals(type))multiplier*=1.5;
        }
        if(battle!=null){var side=facts.side(b.getActor().getShowdownId());if(!Set.of("brickbreak","psychicfangs").contains(move)){
            if(PracticeBattleFacts.bool(side,"veil")||t.getDamageCategory().equals(DamageCategories.INSTANCE.getPHYSICAL())&&PracticeBattleFacts.bool(side,"reflect")||t.getDamageCategory().equals(DamageCategories.INSTANCE.getSPECIAL())&&PracticeBattleFacts.bool(side,"screen"))multiplier*=.5;
        }}
        if("psychicterrain".equals(facts.text("terrain"))&&grounded(b)&&math.plannerPriority(a,move)>0)return 0;
        return (int)Math.floor(damage*multiplier);
    }
    private static boolean hasType(BattlePokemon p,String name){for(var type:p.getEffectedPokemon().getTypes())if(type.getName().equals(name))return true;return false;}
    private static boolean grounded(BattlePokemon p){return !hasType(p,"flying")&&!ability(p).equals("levitate");}
    private static String ability(BattlePokemon p){return id(p.getEffectedPokemon().getAbility().getName());}
    private static String item(BattlePokemon p){var stack=p.getEffectedPokemon().heldItem();return stack.isEmpty()?"":id(stack.getItem().builtInRegistryHolder().key().location().getPath());}
    private static String id(String s){return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
    private static double boost(BattlePokemon p,Stats stat,int extra){int old=p.getStatChanges().getOrDefault(stat,0);return PracticeBattleMath.stageMultiplier(Math.min(6,old+extra))/PracticeBattleMath.stageMultiplier(old);}
    private static Map<String,Object> snapshot(BattlePokemon p){var m=p.getEffectedPokemon();var row=new LinkedHashMap<String,Object>();
        row.put("species",m.getSpecies().getName());row.put("form",m.getForm().getName());row.put("hp",p.getHealth());row.put("maximum",p.getMaxHealth());
        row.put("stats",List.of(m.getAttack(),m.getDefence(),m.getSpecialAttack(),m.getSpecialDefence(),m.getSpeed()));
        row.put("ability",ability(p));row.put("item",item(p));row.put("moves",m.getMoveSet().getMoves().stream().map(v->Map.of("id",v.getName(),"pp",v.getCurrentPp())).toList());return row;
    }

    /** Select from fresh NPC copies, with healed level-50 copies of the opponent's full party. */
    public static void selectLead(ServerPlayer player,NPCEntity npc) {
        var ours=new ArrayList<BattlePokemon>();var theirs=new ArrayList<BattlePokemon>();
        for(var mon:npc.getParty())ours.add(detached(mon,player));
        for(var mon:Cobblemon.INSTANCE.getStorage().getParty(player))theirs.add(detached(mon,player));
        if(ours.isEmpty()||theirs.isEmpty())return;
        var planner=new PracticePartyPlanner(ours.getFirst(),theirs.getFirst(),ours,theirs,null,"",0,false,null);
        int selected=0;double best=-Double.MAX_VALUE;
        for(int i=0;i<ours.size();i++){
            var a=ours.get(i);double sum=0,worst=Double.MAX_VALUE,lead=0;
            for(int j=0;j<theirs.size();j++){var b=theirs.get(j);Pair p=planner.pair(a,b);double score=FullInfoBattleMath.stay(planner.fighter(a),planner.fighter(b),p.ours,p.theirs,false).score();sum+=score;worst=Math.min(worst,score);if(j==0)lead=score;}
            double role=a.getEffectedPokemon().getMoveSet().getMoves().stream().anyMatch(m->Set.of("stealthrock","stickyweb","trickroom").contains(m.getName()))?12:0;
            double score=lead*.55+(sum/theirs.size())*.30+worst*.15+role;
            if(score>best){best=score;selected=i;}
        }
        if(selected!=0)npc.getParty().swap(0,selected);
    }
    private static BattlePokemon detached(Pokemon original,ServerPlayer owner){var copy=original.clone(false,owner.registryAccess());copy.setLevel(50);copy.heal();return new BattlePokemon(copy,copy,new ArrayList<>(),new ArrayList<>());}
}
