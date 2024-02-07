# Java Message Board
A representation of TCP and UDP connections and client-server interactions built with Java 11. 

##How To Use
Server and Client functionality are in the serverside and clientside directories respectively.

###Run Server
In CLI
```console
% cd serverside
/serverside % java Server PORT_NUMBER NUM_MAX_ATTEMPTS
```
For example 
```console
% java Server 4000 3
```
This will run the server on PORT_NUMBER and will allow clients to attempt to authorise a maximum of NUM_MAX_ATTEMPTS
before they are blocked for 10 seconds.

###Run Client
In CLI

```console
% cd clientside
/clientside % java Client SERVER_HOST SERVER_PORT_NUMBER UDP_PORT
```

For example (assuming server running locally):

```console
% java Client localhost 4000 5
```

This will connect the client to the server at SERVER_HOST and on SERVER_PORT. UDP_PORT will be used for UDP peer-to-peer communication

Here the client will be prompted to enter credentials. All sets of valid credentials are held in serverside/credentials.txt
For example:
```console
Enter Username:
abc
Enter Password:
123
```
If a client attempts to enter invalid credentials they will be prompted to re-attempt. If the  number of incorrect attempts reaches
NUM_MAX_ATTEMPTS the user will be blocked for 10 seconds, even if they attempt with correct credentials.
After the blocked period has ended, the number of attempts will reset to 0.

###Client Commands
Once successfully logged in, users can send a variety of commands.
####/msgto
```console
/msgto USERNAME MESSAGE_CONTENT
```
This will send a private message to another logged-in USERNAME via the server. If the USERNAME is invalid or logged off, the 
sender will be informed.
If the message is successfully sent, the user will be notified as well. The receiving user will receive 
the message with the timestamp, sender and message content displayed.

####/activeuser
```console
/activeuser 
```
The server will check if there are any users active on the server other than the querying user. If so, the server will send the
active users, time they logged in and their IP addresses and UDP numbers.
If there are no other active users, the  user will be notified.

####/creategroup
```console
/creategroup GROUP_NAME USER_NAME1 USERNAME2 ...
```

This will request the server to create a group chat with the given name and containing the given users. There is no limit
to the number of users that can be added to the group chat.
If the group chat name already exists, the user will be notified by the server. 
The server will create a log file that stores the messages sent in the chat.

####/joingroup
```console
/joingroup GROUP_NAME
```
If the user was added to the group chat by the chat creator, they will need to join the group with this command to be able to send
messages. If the user has not been added to the chat, they will not be able to join the group and will be notified. 

####/groupmsg 
```console
/groupmsg GROUP_NAME MESSAGE
```
If the user has joined the group chat and the group chat exists, the user will be able to send a message in the chat. 
The server will check if the user has joined - if not, the user will be notified. 
Once the message is sent, the server will add the sender's name, timestamp and message to the group chat log. All members of the group
chat will also have the message displayed in their terminal. If there are no active users, ensure that the message is logged. 

####/p2pvideo
```console
/p2pvideo USER_NAME FILE_NAME
```
This will allow users to send binary files to each other via UDP. The only connection this will make to the server
is checking whether the destination USER_NAME is active. If not, the sender user will receive a notification.

####/logout
```console
/logout
```
This will close the TCP connection between the user and the server. The user will be deleted from the server and will be removed 
from all active user logs.