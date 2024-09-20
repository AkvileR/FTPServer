package org.main;

import org.apache.ftpserver.*;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.*;

import javax.mail.*;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.mail.MessagingException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Properties;

public class Server {
    private static int port = 2121;

    public static void main(String[] args) throws MessagingException,IOException{
        String stringKey;
        if (args.length < 1) {
            stringKey = keyFromEmail();
        } else {
            stringKey = args[0];
        }
        System.out.println("Current key: "+stringKey);
        byte[] adjustedKey = adjustKey(stringKey);
        SecretKey secretKey = new SecretKeySpec(adjustedKey, "AES");

        FtpServerFactory serverFactory = new FtpServerFactory();
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        listenerFactory.setServerAddress("127.0.0.1");
        serverFactory.addListener("default", listenerFactory.createListener());

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        userManagerFactory.setFile(new File("users.properties"));
        UserManager userManager = userManagerFactory.createUserManager();
        serverFactory.setUserManager(userManager);

        BaseUser adminUser = new BaseUser();
        adminUser.setName("admin");
        adminUser.setPassword("admin-psw");
        adminUser.setHomeDirectory("/");
        adminUser.setEnabled(true);

        try {
            userManager.save(adminUser);
            serverFactory.setCommandFactory(new CustomCommandFactory(secretKey));
            FtpServer ftpServer = serverFactory.createServer();
            ftpServer.start();
        } catch (FtpException e) {
            System.err.println("Error starting FTP Server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static byte[] adjustKey(String keyString) {
        byte[] keyBytes = keyString.getBytes(StandardCharsets.UTF_8);
        byte[] adjustedKey = new byte[16];
        if(keyBytes.length < 16){
            System.arraycopy(keyBytes, 0, adjustedKey, 0, keyBytes.length);
            Arrays.fill(adjustedKey, keyBytes.length, adjustedKey.length, (byte) 0);
        } else {
            System.arraycopy(keyBytes, 0, adjustedKey, 0, adjustedKey.length);
        }
        return adjustedKey;
    }

    private static String keyFromEmail() throws MessagingException {
        Properties props = new Properties();
        props.setProperty("mail.store.protocol", "imap");
        props.setProperty("mail.imap.host", "imap.zoho.eu");
        props.setProperty("mail.imap.port", "993");
        props.setProperty("mail.imap.ssl.enable", "true");

        Session session = Session.getInstance(props);
        Store store = session.getStore();
        store.connect("imap.zoho.eu",993, "for_ftp_email@zohomail.eu","FtpServer_1");
        Folder inbox = store.getFolder("INBOX");
        inbox.open(Folder.READ_ONLY);

        Message[] messages = inbox.getMessages();
        if (messages.length == 0) {
            throw new RuntimeException("No emails found in the inbox.");
        }

        Message message = messages[messages.length-1];
        String key = message.getSubject();
        inbox.close(false);
        store.close();

        return key;
    }
}
