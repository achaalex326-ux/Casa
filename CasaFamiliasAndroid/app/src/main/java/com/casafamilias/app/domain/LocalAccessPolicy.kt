package com.casafamilias.app.domain

/** Local screen lock; Supabase authentication and permissions still apply. */
class LocalAccessPolicy(var owner: String? = null) {
    private var authenticated = owner == null
    private var backgroundAt: Long? = null
    fun enabledFor(user: String?) = user != null && user == owner
    fun requiresUnlock(user: String?) = enabledFor(user) && !authenticated
    fun enable(user: String) { require(user.isNotBlank()); owner=user; authenticated=true }
    fun disable() { owner=null; authenticated=true; backgroundAt=null }
    fun authenticate() { authenticated=true; backgroundAt=null }
    fun passwordAuthenticated(user: String) { if(owner!=user)disable(); authenticate() }
    fun background(now: Long) { backgroundAt=now }
    fun foreground(now: Long) {
        val elapsed=backgroundAt?.let {now-it} ?: return
        backgroundAt=null
        if(owner!=null && elapsed>=30_000)authenticated=false
    }
}
