package com.yesmin.ai

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

/** Extra instructions for the text chat only (not for the live voice call). */
object StyleGuide {
    const val TEXT = " NAME RULE: you are Payal. Never mention Gemini, Google or any other AI company's name. " +
        "CHAT STYLE: you can decorate text with {{styles|text}} where styles is a comma list taken from: serif, mono, cursive, bold, italic, underline, strike, big, huge, small, glow, red, green, blue, gold, pink, purple, cyan, orange. " +
        "Example: {{cursive,gold,huge|Shubh Ratri}}. Use it for headings, greetings, wishes, poems and whenever the user asks for a font, style or design; keep normal answers plain. " +
        "You may write in any language or script the user asks for. " +
        "IMAGES: if the user asks you to make, draw, generate or design a picture, photo, image, logo, wallpaper or poster, reply with ONE line [[IMAGE: detailed English description for an image generator, with style, colours and any exact text to show]] and then, after it, at most one short friendly sentence in the user's language. Never say you cannot make images."
}

object ImageStore {
    private val saved = HashMap<String, Uri>()

    fun saveCache(ctx: Context, bytes: ByteArray): String {
        val f = java.io.File(ctx.filesDir, "payal_" + System.currentTimeMillis() + ".png")
        f.writeBytes(bytes)
        return f.absolutePath
    }

    fun toGallery(ctx: Context, path: String): Uri? {
        saved[path]?.let { return it }
        return try {
            val bytes = java.io.File(path).readBytes()
            val name = "Payal_" + System.currentTimeMillis() + ".png"
            val uri: Uri?
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues()
                v.put(MediaStore.Images.Media.DISPLAY_NAME, name)
                v.put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Payal")
                uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
                if (uri != null) ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
            } else {
                val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                @Suppress("DEPRECATION")
                val s = MediaStore.Images.Media.insertImage(ctx.contentResolver, bmp, name, null)
                uri = if (s != null) Uri.parse(s) else null
            }
            if (uri != null) saved[path] = uri
            uri
        } catch (e: Exception) {
            null
        }
    }

    fun download(ctx: Context, path: String) {
        val u = toGallery(ctx, path)
        android.widget.Toast.makeText(ctx, if (u != null) "Photo gallery mein save ho gayi" else "Photo save nahi ho payi", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun share(ctx: Context, path: String) {
        val u = toGallery(ctx, path)
        if (u == null) {
            android.widget.Toast.makeText(ctx, "Photo share nahi ho payi", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(Intent.ACTION_SEND)
        i.type = "image/png"
        i.putExtra(Intent.EXTRA_STREAM, u)
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
