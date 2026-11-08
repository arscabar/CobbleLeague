package dev.cobbleleague
fun releaseIsNewer(candidate:String,current:String):Boolean {
    fun parts(s:String)=s.substringBefore('+').substringBefore('-').split('.').map{it.toIntOrNull()?:-1}
    val a=parts(candidate);val b=parts(current)
    if(a.any{it<0}||b.any{it<0})return false
    for(i in 0 until maxOf(a.size,b.size)){
        val x=a.getOrElse(i){0};val y=b.getOrElse(i){0};if(x!=y)return x>y
    }
    return '-' in current.substringBefore('+')&&'-' !in candidate.substringBefore('+')
}
