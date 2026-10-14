package com.casafamilias.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import android.widget.Toast;
import java.io.*;
import java.util.HashMap;

public class MainActivity extends Activity {
    private WebView web;
    private ValueCallback<Uri[]> picker;
    private byte[] pendingExport;
    private static final int PICK=41, SAVE=42;
    private static final String HOME="https://appassets.androidplatform.net/web/index.html";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        web=new WebView(this); setContentView(web);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setBackgroundColor(0xFFFBF6EE);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u=req.getUrl(); if(!"appassets.androidplatform.net".equals(u.getHost())) return null;
                try {
                    String path=u.getPath(); if(path==null||!path.startsWith("/web/")||path.contains("..")) throw new IOException();
                    String mime=path.endsWith(".html")?"text/html":path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".png")?"image/png":"application/octet-stream";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open(path.substring(1)));
                } catch(IOException ex) { return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",new HashMap<>(),new ByteArrayInputStream(new byte[0])); }
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if("appassets.androidplatform.net".equals(r.getUrl().getHost()))return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW,r.getUrl())); } catch(Exception e) { Toast.makeText(MainActivity.this,"No se pudo abrir el enlace",Toast.LENGTH_SHORT).show(); }
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView v,ValueCallback<Uri[]> cb,FileChooserParams p) {
                if(picker!=null)picker.onReceiveValue(null); picker=cb;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*");
                try { startActivityForResult(i,PICK); return true; } catch(Exception e) { picker=null;return false; }
            }
            @Override public boolean onJsAlert(WebView v,String u,String msg,JsResult result) { new android.app.AlertDialog.Builder(MainActivity.this).setMessage(msg).setPositiveButton("Aceptar",(d,w)->result.confirm()).setOnCancelListener(d->result.cancel()).show();return true; }
        });
        web.setDownloadListener((url,ua,cd,mime,len)-> {
            if(url.startsWith("https://"))try { startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(url))); }catch(Exception ignored){}
        });
        // Bundled scripts and app content are the only pages loaded in this WebView.
        web.addJavascriptInterface(new ExportBridge(),"AndroidFiles");
        web.loadUrl(HOME+authFragment(getIntent()));
    }
    private String authFragment(Intent intent) { Uri u=intent.getData(); return u!=null&&"casafamilias".equals(u.getScheme())&&u.getEncodedFragment()!=null?"#"+u.getEncodedFragment():""; }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i);setIntent(i);web.loadUrl(HOME+authFragment(i)); }
    public class ExportBridge {
        @JavascriptInterface public void save(String name,String mime,String base64) {
            if(base64==null||base64.length()>28000000)return;
            try {
                byte[] bytes=android.util.Base64.decode(base64,android.util.Base64.DEFAULT);
                runOnUiThread(()-> {
                    if(pendingExport!=null){Toast.makeText(MainActivity.this,"Termina la descarga anterior",Toast.LENGTH_SHORT).show();return;}
                    pendingExport=bytes; Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name.replaceAll("[/\\\\]","_"));
                    try{startActivityForResult(i,SAVE);}catch(Exception e){pendingExport=null;Toast.makeText(MainActivity.this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
                });
            }catch(Exception ignored){}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==PICK&&picker!=null){picker.onReceiveValue(result==RESULT_OK&&data!=null?new Uri[]{data.getData()}:null);picker=null;}
        if(request==SAVE){
            if(result==RESULT_OK&&data!=null&&pendingExport!=null)try(OutputStream out=getContentResolver().openOutputStream(data.getData())){out.write(pendingExport);Toast.makeText(this,"Archivo guardado",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"No se pudo guardar",Toast.LENGTH_LONG).show();}
            pendingExport=null;
        }
    }
    @Override public void onBackPressed(){web.evaluateJavascript("Mobile.back()",value->{if("false".equals(value))super.onBackPressed();});}
    @Override protected void onDestroy(){if(picker!=null)picker.onReceiveValue(null);web.destroy();super.onDestroy();}
}
