package com.sadqa.tracker;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypted storage. All records (and the PIN hash) live in ONE file, AES-256-GCM encrypted with a
 * key generated inside the Android Keystore (hardware-backed where the phone supports it) – the key
 * never leaves the secure hardware and is not readable by other apps, backups or file managers.
 */
final class Vault {
    private static final String ALIAS = "sadqa_vault_key_v1";
    private static final String FILE = "vault.bin";

    private Vault() { }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return kg.generateKey();
        }
        return (SecretKey) ks.getKey(ALIAS, null);
    }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    static boolean exists(Context c) { return file(c).exists(); }

    /** Returns null when nothing has been saved yet; throws when the file cannot be decrypted. */
    static JSONObject read(Context c) throws Exception {
        File f = file(c);
        if (!f.exists()) return null;
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        FileInputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        } finally { in.close(); }
        byte[] all = bo.toByteArray();
        int ivLen = all[0] & 0xff;
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, all, 1, ivLen));
        byte[] plain = ci.doFinal(all, 1 + ivLen, all.length - 1 - ivLen);
        return new JSONObject(new String(plain, "UTF-8"));
    }

    /** Atomic write: encrypt -> temp file -> rename, so a crash never leaves a half-written vault. */
    static void write(Context c, JSONObject o) throws Exception {
        Cipher ci = Cipher.getInstance("AES/GCM/NoPadding");
        ci.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = ci.getIV();
        byte[] ct = ci.doFinal(o.toString().getBytes("UTF-8"));
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(iv.length);
            out.write(iv);
            out.write(ct);
            out.getFD().sync();
        } finally { out.close(); }
        if (!tmp.renameTo(file(c))) {
            file(c).delete();
            if (!tmp.renameTo(file(c))) throw new java.io.IOException("could not save");
        }
    }

    /** Deletes the data file and the Keystore key (used by "Reset"). */
    static void destroy(Context c) {
        file(c).delete();
        new File(c.getFilesDir(), FILE + ".tmp").delete();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS);
        } catch (Exception ignored) { }
    }
}
