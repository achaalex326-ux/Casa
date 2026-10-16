package com.casafamilias.app

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.casafamilias.app.data.*
import com.casafamilias.app.domain.Role
import com.casafamilias.app.ui.CasaApp
import com.casafamilias.app.ui.CasaViewModel
import kotlinx.coroutines.*
import org.json.JSONTokener
import org.json.JSONObject

class MainActivity: ComponentActivity() {
    private lateinit var vm: CasaViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle=SystemBarStyle.auto(0x00F3F7FF,0x000D1728),navigationBarStyle=SystemBarStyle.auto(0xFFF3F7FF.toInt(),0xFF0D1728.toInt()))
        val preview=(applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)!=0 && intent.getBooleanExtra("design_preview",false)
        val source: CasaDataSource by lazy {if(preview)PreviewRepository(Role.from(intent.getStringExtra("preview_role") ?: "sysadmin")) else CasaRepository(applicationContext,ApiConfig.load(applicationContext),SecureSessionStore(applicationContext))}
        vm=ViewModelProvider(this,object: ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T: ViewModel> create(modelClass: Class<T>): T=CasaViewModel(application,source) as T
        })[CasaViewModel::class.java]
        setContent {CasaApp(vm)}
        if(!vm.hasStarted) {
            val fragment=recovery(intent)
            if(preview || fragment!=null || source.session!=null)vm.start(fragment=fragment)
            else migrateLegacySession {vm.start(it)}
        }
    }
    override fun onNewIntent(intent: Intent) {super.onNewIntent(intent);setIntent(intent);recovery(intent)?.let {vm.start(fragment=it)}}
    private fun recovery(intent: Intent): String? = intent.data?.takeIf {it.scheme=="casafamilias" && it.host=="auth"}?.encodedFragment
    private fun migrateLegacySession(done: (String?)->Unit) {
        val prefs=getSharedPreferences("casa_native_migration",0)
        if(prefs.getBoolean("done",false)){done(null);return}
        // Only this one-time, invisible migration uses WebView. Every app screen is Compose.
        var finished=false;var web: WebView?=null;var timeout: Job?=null
        fun finish(value: String?) {if(finished)return;finished=true;timeout?.cancel();prefs.edit().putBoolean("done",true).apply();web?.destroy();web=null;done(value)}
        try {
            val config=ApiConfig.load(this);val ref=android.net.Uri.parse(config.url).host!!.substringBefore('.')
            val storageKey="sb-$ref-auth-token"
            web=WebView(this).apply {
                settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
                webViewClient=object: WebViewClient(){override fun onPageFinished(view: WebView,url: String){view.evaluateJavascript("localStorage.getItem(${JSONObject.quote(storageKey)})"){raw->val value=runCatching {JSONTokener(raw).nextValue() as? String}.getOrNull();finish(value)}}}
                loadDataWithBaseURL("https://appassets.androidplatform.net/web/","<html><body></body></html>","text/html","UTF-8",null)
            }
            timeout=lifecycleScope.launch {delay(4000);finish(null)}
        }catch(e: Exception){finish(null)}
    }
}
