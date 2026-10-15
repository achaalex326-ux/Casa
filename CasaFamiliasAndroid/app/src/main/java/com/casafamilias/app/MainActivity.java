package com.casafamilias.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.*;
import android.view.View;
import android.widget.Toast;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.view.Gravity;
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
        openApp();
    }
    private void openApp() {
        if(web!=null){setContentView(web);web.loadUrl(HOME+authFragment(getIntent()));return;}
        try { createWebView(); } catch(RuntimeException error) {
            showLoadError("Actualiza Android System WebView y Google Chrome desde Play Store y vuelve a abrir Casa Familias.");
        }
    }
    private void createWebView() {
        web=new WebView(this); setContentView(web);
        WebSettings s=web.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(true); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        // Assets belong to this APK. Reusing an older version can mix incompatible scripts.
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFFF5F7FC);
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest req) {
                Uri u=req.getUrl(); if(!"appassets.androidplatform.net".equals(u.getHost())) return null;
                try {
                    String path=u.getPath(); if(path==null||!path.startsWith("/web/")||path.contains("..")) throw new IOException();
                    String mime=path.endsWith(".html")?"text/html":path.endsWith(".js")?"application/javascript":path.endsWith(".css")?"text/css":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".png")?"image/png":"application/octet-stream";
                    HashMap<String,String> headers=new HashMap<>();
                    headers.put("Cache-Control","no-store, max-age=0");
                    return new WebResourceResponse(mime,"UTF-8",200,"OK",headers,getAssets().open(path.substring(1)));
                } catch(IOException ex) { return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",new HashMap<>(),new ByteArrayInputStream(new byte[0])); }
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error) {
                if(request.isForMainFrame())showLoadError("No se pudo abrir Casa Familias. Pulsa Reintentar.");
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest request,WebResourceResponse response) {
                if(request.isForMainFrame())showLoadError("No se pudieron cargar los archivos de la aplicación. Pulsa Reintentar.");
            }
            @Override public boolean onRenderProcessGone(WebView v,RenderProcessGoneDetail detail) {
                // A crashed renderer cannot be reused; local session storage is preserved.
                if(picker!=null){picker.onReceiveValue(null);picker=null;}
                if(v==web)web=null;
                showLoadError("Android cerró la pantalla de la aplicación. Pulsa Reintentar para volver a abrirla.");
                v.destroy();return true;
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
    private void showLoadError(String message) {
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);
        int space=(int)(28*getResources().getDisplayMetrics().density);panel.setPadding(space,space,space,space);panel.setBackgroundColor(0xFFF5F7FC);
        TextView title=new TextView(this);title.setText("Casa Familias");title.setTextSize(26);title.setTextColor(0xFF182A69);title.setGravity(Gravity.CENTER);panel.addView(title);
        TextView text=new TextView(this);text.setText(message);text.setTextSize(17);text.setTextColor(0xFF182A69);text.setPadding(0,space,0,space);text.setGravity(Gravity.CENTER);panel.addView(text);
        Button retry=new Button(this);retry.setText("Reintentar");retry.setOnClickListener(v->openApp());panel.addView(retry);setContentView(panel);
    }
    private String authFragment(Intent intent) { Uri u=intent.getData(); return u!=null&&"casafamilias".equals(u.getScheme())&&u.getEncodedFragment()!=null?"#"+u.getEncodedFragment():""; }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i);setIntent(i);openApp(); }
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
    @Override public void onBackPressed(){if(web==null||web.getParent()==null){super.onBackPressed();return;}web.evaluateJavascript("!!(window.Mobile && Mobile.back())",value->{if(!"true".equals(value))super.onBackPressed();});}
    @Override protected void onDestroy(){if(picker!=null)picker.onReceiveValue(null);if(web!=null)web.destroy();super.onDestroy();}
}
