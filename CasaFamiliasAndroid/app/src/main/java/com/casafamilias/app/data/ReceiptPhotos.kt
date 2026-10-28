package com.casafamilias.app.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Full-size camera output stays in app-private storage until the form is submitted. */
object ReceiptPhotos {
    private fun directory(context: Context)=File(context.filesDir,"receipt_photos").apply {mkdirs()}
    fun create(context: Context): Uri {
        val file=File.createTempFile("receipt-", ".jpg",directory(context))
        return FileProvider.getUriForFile(context,context.packageName+".receipts",file)
    }
    private fun owned(context: Context,uri: Uri): File? {
        if(uri.scheme!="content" || uri.authority!=context.packageName+".receipts" || uri.pathSegments.size!=2 || uri.pathSegments[0]!="receipts")return null
        val folder=directory(context).canonicalFile;val file=File(folder,uri.lastPathSegment ?: return null).canonicalFile
        return file.takeIf {it.parentFile==folder}
    }
    fun deleteIfOwned(context: Context,uri: Uri){owned(context,uri)?.delete()}
    /** Bound memory and upload size; apply EXIF orientation before removing camera metadata. */
    fun prepare(context: Context,uri: Uri): Uri {
        val file=owned(context,uri) ?: error("La foto no pertenece a esta aplicación.")
        require(file.length()>0){"La cámara no guardó la foto. Vuelve a intentarlo."}
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0){"No se pudo leer la foto. Vuelve a tomarla."}
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>5120 || bounds.outWidth.toLong()*bounds.outHeight/(sample.toLong()*sample)>4_000_000)sample*=2
        val decoded=BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply {inSampleSize=sample}) ?: error("No se pudo abrir la foto.")
        var bitmap=decoded
        try {
            val longest=maxOf(bitmap.width,bitmap.height)
            if(longest>2560){val scale=2560f/longest;val resized=Bitmap.createScaledBitmap(bitmap,(bitmap.width*scale).toInt().coerceAtLeast(1),(bitmap.height*scale).toInt().coerceAtLeast(1),true);if(resized!==bitmap)bitmap.recycle();bitmap=resized}
            val orientation=ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)
            val matrix=Matrix().apply {when(orientation){
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL->setScale(-1f,1f)
                ExifInterface.ORIENTATION_ROTATE_180->setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL->{setRotate(180f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_TRANSPOSE->{setRotate(90f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_90->setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE->{setRotate(-90f);postScale(-1f,1f)}
                ExifInterface.ORIENTATION_ROTATE_270->setRotate(-90f)
            }}
            if(!matrix.isIdentity){val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true);if(rotated!==bitmap)bitmap.recycle();bitmap=rotated}
            var quality=88;var bytes: ByteArray
            do {bytes=ByteArrayOutputStream().use {out->check(bitmap.compress(Bitmap.CompressFormat.JPEG,quality,out));out.toByteArray()};quality-=10}while(bytes.size>5*1024*1024 && quality>=48)
            require(bytes.size<=5*1024*1024){"La foto pesa demasiado. Acércate a la factura y vuelve a tomarla."}
            val temporary=File(file.parentFile,"prepared-${UUID.randomUUID()}.jpg")
            try {temporary.writeBytes(bytes);check(temporary.renameTo(file)){"No se pudo guardar la foto."}}finally{temporary.delete()}
            return uri
        } finally {bitmap.recycle()}
    }
}
class ReceiptCaptureContract: ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context,input: Uri): Intent = super.createIntent(context,input).apply {
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        clipData=ClipData.newRawUri("Foto de la factura",input)
    }
}
