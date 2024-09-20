# FTP Server

A server made to work with the Filezilla client for the most basic operations based on the Apache FTP Server example. 

When storing files are encrypted and when retrieving decrypted. Currently they are stored in the project folder. 
*The full path is on lines 39 and 72 in CustomCommandFactory, you need to change it to work on your device. As this is more of a sandbox for myself, I did not add a more generalised solution.* 

## Launching

**Server.java** is the main file to launch the program.
The command line argument is the key that the files are encrypted with. If no key is provided the program then tries to connect to an email inbox and takes the first letter topic as the key.
*Currently launching without an argument **will not work** as the email in the code was a temporary one which has since been deleted.*
