package dev.kent.windbook.core.data.learning

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local key, excluded from backup and never embedded in an APK. */
class GeminiKeyStore(context: Context) {
    private val file=AtomicFile(File(context.noBackupFilesDir,"gemini-key.enc"))
    private fun secret(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(value: String) {
        val key=value.trim();require(key.isNotBlank() && key.length<=512 && key.none {it.isWhitespace() || it.code<32})
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.ENCRYPT_MODE,secret())}
        val encrypted=cipher.doFinal(key.toByteArray())
        val out=file.startWrite()
        try {out.write(cipher.iv.size);out.write(cipher.iv);out.write(encrypted);file.finishWrite(out)}
        catch(e: Exception){file.failWrite(out);throw e}
    }
    @Synchronized fun read(): String? = try {
        if(!file.baseFile.exists()) null else {
            val bytes=file.openRead().use {it.readBytes()};require(bytes.size in 30..1024)
            val n=bytes[0].toInt();require(n==12)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {init(Cipher.DECRYPT_MODE,secret(),GCMParameterSpec(128,bytes.copyOfRange(1,1+n)))}
            cipher.doFinal(bytes.copyOfRange(1+n,bytes.size)).toString(Charsets.UTF_8)
        }
    } catch(_: Exception){null}
    @Synchronized fun remove() {file.delete()}
    companion object {private const val ALIAS="windbook-gemini-v1"}
}
