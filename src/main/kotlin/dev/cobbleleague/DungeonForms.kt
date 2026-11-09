package dev.cobbleleague

import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

/** Optional integration uses the installed Mega Showdown registry, never a hardcoded stone map. */
internal object DungeonForms {
    private const val ROOT="com.github.yajatkaul.mega_showdown."
    private fun isClass(item:Item,suffix:String)=item.javaClass.name==ROOT+suffix
    fun isMegaStone(item:Item)=mega(item)
    private fun mega(item:Item)=isClass(item,"item.custom.mega.MegaStone")
    private fun held(item:Item)=isClass(item,"item.custom.form_change.FormChangeHeldItem")
    private fun speciesAllowed(item:Item,mon:Pokemon):Boolean {
        val field=item.javaClass.getDeclaredField("pokemons").apply{isAccessible=true}
        return (field.get(item) as List<*>).contains(mon.species.name)
    }
    private fun megaAllowed(item:Item,mon:Pokemon):Boolean=runCatching {
        val type=Class.forName(ROOT+"gimmick.MegaGimmick")
        val locator=Class.forName(ROOT+"utils.RegistryLocator")
        val value=locator.getMethod("getComponent",Class::class.java,ItemStack::class.java).invoke(null,type,ItemStack(item))
        val component=if(value is java.util.Optional<*>)value.orElse(null)else value
        component!=null && (type.getMethod("pokemons").invoke(component) as List<*>).contains(mon.species.name)
    }.getOrDefault(false)
    private fun interaction(item:Item)=item.javaClass.name.startsWith(ROOT+"item.custom.form_change.") && item.javaClass.methods.any{it.name=="canUseOnPokemon"&&it.parameterCount==2}
    fun special(item:Item)=mega(item)||held(item)||interaction(item)
    fun eligible(item:Item,mon:Pokemon):Boolean=runCatching {
        when {
            mega(item)->megaAllowed(item,mon)
            held(item)->speciesAllowed(item,mon)
            interaction(item)->mon.entity!=null && item.javaClass.getMethod("canUseOnPokemon",ItemStack::class.java,Pokemon::class.java).invoke(item,ItemStack(item),mon) as Boolean
            else->true
        }
    }.getOrDefault(false)
    fun trainingEligible(id:String,mon:Pokemon)=when(id){
        DungeonTraining.SOUP->mon.dmaxLevel<10 && mon.species.resourceIdentifier.path !in setOf("zacian","zamazenta","eternatus")
        DungeonTraining.GMAX->!mon.gmaxFactor&&mon.species.forms.any{"gmax" in it.labels}
        else->true
    }
    fun products(mon:Pokemon):List<DungeonProduct> = buildList {
        BuiltInRegistries.ITEM.filter{special(it)&&eligible(it,mon)}.forEach{item->
            val id=BuiltInRegistries.ITEM.getKey(item).toString()
            if(id!=BuiltInRegistries.ITEM.getKey(mon.heldItem().item).toString())add(DungeonProduct(id,3,if(mega(item))"메가스톤" else "폼 체인지"))
        }
        if(trainingEligible(DungeonTraining.SOUP,mon))add(DungeonProduct(DungeonTraining.SOUP,2,"다이맥스 육성"))
        if(trainingEligible(DungeonTraining.GMAX,mon))add(DungeonProduct(DungeonTraining.GMAX,3,"거다이맥스 육성"))
    }
    fun validate(mon:Pokemon,item:Item){
        require(eligible(item,mon)&&trainingEligible(BuiltInRegistries.ITEM.getKey(item).toString(),mon)){"이 포켓몬에게 사용할 수 없는 특수 도구입니다."}
    }
    fun nativeHeld(mon:Pokemon,item:Item,apply:Boolean){
        if(held(item)){
            fun field(name:String)=item.javaClass.getDeclaredField(name).apply{isAccessible=true}.get(item)
            @Suppress("UNCHECKED_CAST")
            val callback=field(if(apply)"onApplyCallback" else "onRevertCallback") as? java.util.function.Consumer<Pokemon>
            try{callback?.accept(mon)}catch(e:NullPointerException){
                // This mod's Rusted Sword callback creates cosmetic lightning from getEntity().
                // Unsent draft Pokemon have no entity; all non-visual errors still propagate.
                if(mon.entity!=null || e.message?.contains("Pokemon.getEntity()")!=true)throw e
            }
            val effectClass=Class.forName(ROOT+"api.codec.Effect")
            val effect=effectClass.getMethod("getEffect",String::class.java).invoke(null,field("effectId"))
            val operation=if(apply)"applyEffects" else "revertEffects"
            val method=effectClass.methods.first{it.name==operation&&it.parameterCount==4}
            method.invoke(effect,mon,listOf(field(if(apply)"applyAspect" else "revertAspect")),java.util.Optional.empty<Any>(),null)
        }
        mon.tradeable=false
    }
    fun consumable(item:Item)=interaction(item)
    fun use(mon:Pokemon,stack:ItemStack){
        validate(mon,stack.item)
        val owner=requireNotNull(mon.getOwnerPlayer()){"포켓몬 소유자가 없습니다."}
        val method=stack.item.javaClass.methods.first{it.name=="applyToPokemon"&&it.parameterCount==3}
        val result=method.invoke(stack.item,owner,stack,mon) as net.minecraft.world.InteractionResultHolder<*>
        require(result.result!=net.minecraft.world.InteractionResult.FAIL){"폼 체인지 조건을 만족하지 않습니다."}
        mon.tradeable=false
    }
}
