package dev.foco.bloqueio.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Senha mestra do admin + código de recuperação. Ver docs/funcionalidades/08-admin.md.
 */
class PasswordVault(context: Context) {

    sealed interface Result {
        data object Ok : Result
        data object Wrong : Result
        data class LockedOut(val seconds: Long) : Result
    }

    private val prefs = context.getSharedPreferences("vault", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    fun isSetUp() = prefs.contains(KEY_HASH)

    /** Cria a senha e retorna o código de recuperação (mostrar uma única vez). */
    fun setup(password: String): String {
        require(password.length >= MIN_LENGTH) { "A senha precisa de pelo menos $MIN_LENGTH caracteres" }
        storePassword(password)
        return newRecoveryCode()
    }

    fun verify(password: String): Result {
        lockoutRemaining()?.let { return Result.LockedOut(it) }
        val salt = decode(prefs.getString(KEY_SALT, null) ?: return Result.Wrong)
        val expected = decode(prefs.getString(KEY_HASH, null) ?: return Result.Wrong)
        return if (MessageDigest.isEqual(pbkdf2(password, salt), expected)) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
            Result.Ok
        } else {
            registerFailure()
            lockoutRemaining()?.let { Result.LockedOut(it) } ?: Result.Wrong
        }
    }

    fun change(current: String, newPassword: String): Result {
        val r = verify(current)
        if (r == Result.Ok) {
            require(newPassword.length >= MIN_LENGTH)
            storePassword(newPassword)
        }
        return r
    }

    /** Redefine a senha com o código de recuperação. Retorna o NOVO código, ou null se inválido. */
    fun resetWithRecovery(code: String, newPassword: String): String? {
        if (lockoutRemaining() != null) return null
        val salt = decode(prefs.getString(KEY_REC_SALT, null) ?: return null)
        val expected = decode(prefs.getString(KEY_REC_HASH, null) ?: return null)
        val normalized = code.uppercase().filter { it.isLetterOrDigit() }
        if (!MessageDigest.isEqual(pbkdf2(normalized, salt), expected)) {
            registerFailure()
            return null
        }
        require(newPassword.length >= MIN_LENGTH)
        storePassword(newPassword)
        prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCK_UNTIL, 0).apply()
        return newRecoveryCode()
    }

    private fun storePassword(password: String) {
        val salt = ByteArray(16).also(random::nextBytes)
        prefs.edit()
            .putString(KEY_SALT, encode(salt))
            .putString(KEY_HASH, encode(pbkdf2(password, salt)))
            .apply()
    }

    private fun newRecoveryCode(): String {
        val raw = (1..20).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        val salt = ByteArray(16).also(random::nextBytes)
        prefs.edit()
            .putString(KEY_REC_SALT, encode(salt))
            .putString(KEY_REC_HASH, encode(pbkdf2(raw, salt)))
            .apply()
        return raw.chunked(5).joinToString("-")
    }

    private fun registerFailure() {
        val fails = prefs.getInt(KEY_FAILS, 0) + 1
        val edit = prefs.edit().putInt(KEY_FAILS, fails)
        if (fails >= 5) {
            val seconds = 30L shl (fails - 5).coerceAtMost(8)
            edit.putLong(KEY_LOCK_UNTIL, System.currentTimeMillis() + seconds * 1000)
        }
        edit.apply()
    }

    private fun lockoutRemaining(): Long? {
        val until = prefs.getLong(KEY_LOCK_UNTIL, 0)
        val left = (until - System.currentTimeMillis()) / 1000
        return if (left > 0) left else null
    }

    private fun pbkdf2(secret: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    private fun encode(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun decode(s: String) = Base64.decode(s, Base64.NO_WRAP)

    companion object {
        const val MIN_LENGTH = 6
        private const val ITERATIONS = 120_000
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private const val KEY_HASH = "hash"
        private const val KEY_SALT = "salt"
        private const val KEY_REC_HASH = "rec_hash"
        private const val KEY_REC_SALT = "rec_salt"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCK_UNTIL = "lock_until"
    }
}
