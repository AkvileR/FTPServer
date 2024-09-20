package org.main;

import org.apache.ftpserver.command.Command;
import org.apache.ftpserver.command.CommandFactory;
import org.apache.ftpserver.command.impl.*;
import org.apache.ftpserver.ftplet.*;
import org.apache.ftpserver.impl.*;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.mina.filter.logging.MdcInjectionFilter;

import javax.crypto.SecretKey;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class CustomCommandFactory implements CommandFactory {
    private final SecretKey secretKey;

    public CustomCommandFactory(SecretKey key) {
        super();
        this.secretKey = key;
    }

    @Override
    public Command getCommand(String commandName) {
        if ("EPSV".equals(commandName)) { //RFC 2428
            return new EPSV();
        } else if ("EPRT".equals(commandName)) { //RFC 2428
            return new EPRT();
        } else if ("LIST".equals(commandName)) {
            return new LIST();
        } else if ("PWD".equals(commandName)) {
            return new PWD() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    session.resetState();
                    String currDir = "C:/Users/Akvile/IdeaProjects/FTPServer";
                    //Code 257 - "PATHNAME" created.
                    session.write(LocalizedFtpReply.translate(session, request, context, 257, "PWD", currDir));
                }
            };
        } else if ("USER".equals(commandName)) {
            return new USER() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    session.resetState();
                    String userName = request.getArgument();
                    MdcInjectionFilter.setProperty(session, "userName", userName);
                    session.setUserArgument(userName);
                    if (userName.equals("admin")) {
                        //Code 331 - User name okay, need password.
                        session.write(LocalizedFtpReply.translate(session, request, context, 331, "USER", (String) null));
                    } else {
                        //Code 530 - Not logged in.
                        session.write(LocalizedFtpReply.translate(session, request, context, 530, "USER.invalid", (String) null));
                    }
                }
            };
        } else if ("PASS".equals(commandName)) {
            return new PASS() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws FtpException {
                    session.resetState();
                    String password = request.getArgument();
                    String userName = session.getUserArgument();

                    BaseUser adminUser = new BaseUser();
                    adminUser.setName("admin");
                    adminUser.setPassword("admin-psw");
                    adminUser.setHomeDirectory("/Users/Akvile/IdeaProjects/FTPServer");
                    adminUser.setEnabled(true);
                    session.setUser(adminUser);

                    if (userName.equals("admin") && password.equals("admin-psw")) {
                        FileSystemFactory fmanager = context.getFileSystemManager();
                        FileSystemView fsview = fmanager.createFileSystemView(session.getUser());
                        session.setLogin(fsview);
                        //Code 230 - User logged in, proceed.
                        session.write(LocalizedFtpReply.translate(session, request, context, 230, "PASS", userName));
                    }
                }
            };
        } else if ("STOR".equals(commandName)) {
            return new STOR() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException {
                    IODataConnection dataConnection;
                    try {
                        dataConnection = (IODataConnection) session.getDataConnection().openConnection();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    String fileName = request.getArgument();
                    Path filePath = Paths.get(fileName);
                    long transferredSize = dataConnection.transferFromClient(new DefaultFtpSession(session), new FileOutputStream(fileName));
                    try {
                        FileEncryptor.encryptFile(secretKey, filePath, filePath);
                    } catch (Exception e) {
                        System.out.println(e);
                    }

                    if (transferredSize <= 0) {
                        //Code 451 -  Requested action aborted: local error in processing. (?)
                        session.write(LocalizedFtpReply.translate(session, request, context, 451, "STOR", fileName));
                    } else {
                        //Code 226 - Closing data connection. Requested file action successful (for example, transfer or file abort).
                        session.write(LocalizedFtpReply.translate(session, request, context, 226, "STOR", fileName));
                    }
                }
            };
        } else if ("RETR".equals(commandName)) {
            return new RETR() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) throws IOException {
                    IODataConnection dataConnection;
                    try {
                        dataConnection = (IODataConnection) session.getDataConnection().openConnection();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    String fileName = request.getArgument();
                    Path filePath = Paths.get(fileName);
                    String fileNameWithoutExtension = filePath.getFileName().toString().replaceFirst("[.][^.]+$", "");
                    String newFileName = "E" + fileNameWithoutExtension;
                    Path copyPath = filePath.resolveSibling(newFileName + ".txt");
                    Files.copy(filePath, copyPath, StandardCopyOption.REPLACE_EXISTING);

                    try {
                        FileEncryptor.decryptFile(secretKey, filePath, filePath);
                    } catch (Exception e) {
                        System.out.println(e);
                    }

                    FileInputStream fis = new FileInputStream(fileName);
                    long transferredSize = dataConnection.transferToClient(new DefaultFtpSession(session), fis);
                    fis.close();
                    Files.delete(copyPath);

                    if (transferredSize <= 0) {
                        //Code 425 - Can't open data connection.
                        session.write(LocalizedFtpReply.translate(session, request, context, 425, "STOR", fileName));
                    } else {
                        //Code 226 - Closing data connection. Requested file action successful (for example, transfer or file abort).
                        session.write(LocalizedFtpReply.translate(session, request, context, 226, "STOR", fileName));
                    }
                }
            };
        } else if ("QUIT".equals(commandName)) {
            return new QUIT() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    //Code 221 - Service closing control connection. Logged out if appropriate.
                    session.write(LocalizedFtpReply.translate(session, request, context, 221, "QUIT", (String) null));
                    session.getDataConnection().closeDataConnection();
                    session.close();
                }
            };
        } else if ("TYPE".equals(commandName)) {
            return new TYPE() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    session.setDataType(DataType.ASCII);
                    session.write(LocalizedFtpReply.translate(session, request, context, 200, "TYPE", (String) null));
                }
            };
        } else if ("MODE".equals(commandName)) {
            return new MODE() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    session.getDataConnection().setZipMode(false); // Default (Stream) mode
                    session.write(LocalizedFtpReply.translate(session, request, context, 200, "MODE", "S"));
                }
            };
        } else if ("STRU".equals(commandName)) {
            return new STRU() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    session.setStructure(Structure.FILE);
                    session.write(LocalizedFtpReply.translate(session, request, context, 200, "STRU", (String) null));
                }
            };
        } else if ("NOOP".equals(commandName)) {
            return new NOOP() {
                @Override
                public void execute(FtpIoSession session, FtpServerContext context, FtpRequest request) {
                    //Code 200 - Command okay.
                    session.write(LocalizedFtpReply.translate(session, request, context, 200, "NOOP", (String) null));
                }
            };
        } else if ("PORT".equals(commandName)) {
            return new PORT();
        }
        return null;
    }
}
