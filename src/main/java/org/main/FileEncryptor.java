package org.main;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
public class FileEncryptor {
    private static final String TRANSFORMATION = "AES/CTR/NoPadding";
    private static final int IV_SIZE = 16;
    public static void encryptFile(SecretKey key, Path inputFile, Path outputFile) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        SecureRandom secureRandom = new SecureRandom();
        byte[] iv = new byte[IV_SIZE];
        secureRandom.nextBytes(iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] inputBytes = Files.readAllBytes(inputFile);
        byte[] encryptedBytes = cipher.doFinal(inputBytes);
        byte[] combined = new byte[iv.length + encryptedBytes.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedBytes, 0, combined, iv.length, encryptedBytes.length);

        Files.write(outputFile, combined);
    }

    public static void decryptFile(SecretKey key, Path inputFile, Path outputFile) throws Exception {
        byte[] fileContent = Files.readAllBytes(inputFile);
        byte[] iv = new byte[IV_SIZE];
        System.arraycopy(fileContent, 0, iv, 0, IV_SIZE);
        byte[] cipherText = new byte[fileContent.length - IV_SIZE];
        System.arraycopy(fileContent, IV_SIZE, cipherText, 0, cipherText.length);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

        byte[] decryptedBytes = cipher.doFinal(cipherText);
        Files.write(outputFile, decryptedBytes);
    }
}
