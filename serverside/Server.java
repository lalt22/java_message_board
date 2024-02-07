import java.net.*;
import java.io.*;
import java.util.*;
import java.time.*;



public class Server {
    //Server Info
    private static ServerSocket serverSocket;
    private static Integer serverPort;
    private static Integer maxTries;

    //Maps detailing attempted logins, blocked users, active users
    private static Map<String, FailedLogin> failedLoginMap = new HashMap<>();
    private static Map<String, Date> blockedUsersWithTimeMap = new HashMap<>();
    private static Map<String, ActiveUser> activeUserMap = new HashMap<>();

    //Map detailing existing groupchats
    private static Map<String, GroupChat> existingGroupChats = new HashMap<>();


    //Private enums to indicate login success or failure
    private static Integer FAILURE = 0;
    private static Integer SUCCESS = 1;

    //To indicate whether a message is being sent
    private static Integer MESSAGE = 2;

    //To indicate if a user is online for P2P
    private static Integer ISONLINE = 3;

    private static class ClientThread extends Thread {
        private final Socket clientSocket;
        private final Socket messageSocket;
        private boolean clientAlive = false;

        ClientThread(Socket clientSocket, Socket messageSocket) {
            this.clientSocket = clientSocket;
            this.messageSocket = messageSocket;
        }

        @Override
        public void run() {
            super.run();
            String clientAddress = clientSocket.getInetAddress().getHostAddress();
            int clientPort = clientSocket.getPort();
            String clientID = "(" + clientAddress + ", " + clientPort + ")";
            LogFunctions lf = new LogFunctions();

            //Validate Credentials
            boolean loggedIn = false;
            String userName = null;
            boolean alreadyBlocked = false;
            Date loginTime = null;
            LoginResult loginResult = null;

            //Keep attempting authentication while not logged in
            while (!loggedIn) {
                loginResult = login(clientSocket);
                loggedIn = loginResult.success;
                alreadyBlocked = loginResult.blocked;
                userName = loginResult.userName;
                loginTime = loginResult.loginTime;
            }

            //Once logged in - continue
            failedLoginMap.remove(userName);
            ActiveUser newUser = activeUserMap.get(userName);
            newUser.userAddress = clientAddress;
            newUser.userUDPPort = getUserUDPPort(clientSocket);
            newUser.userSocket = clientSocket;
            newUser.userMessageSocket = messageSocket;
            lf.logNewUser(newUser);

            System.out.println("===== New connection created for user: " + clientID);
            clientAlive = true;

            //Make data input/output streams to read info from client
            DataInputStream dis = null;
            DataOutputStream dos = null;

            //Data output stream to send messages to other clients in separate thread
            DataOutputStream messageDos = null;

            try {
                dis = new DataInputStream(this.clientSocket.getInputStream());
                dos = new DataOutputStream(this.clientSocket.getOutputStream());
            } catch(IOException e) {e.printStackTrace();}

            while (clientAlive) {
                try {
                    //Get command from the client
                    assert dis != null;
                    assert dos != null;

                    String data = "";
                    BufferedReader inStreamReader = new BufferedReader(new InputStreamReader(dis));
                    data = inStreamReader.readLine();
                    System.out.println("RECEIVED: " + data + " from: " + newUser.userName);

                    //Get command from data string
                    String[] commandDetails = data.split(" ");
                    String commandBase = commandDetails[0];

                    /*
                        SENDING PRIVATE MESSAGE
                     */
                    if (commandBase.equals("/msgto")) {
                        if (commandDetails.length < 3) {
                            dos.writeUTF("Please Include A User and A Message: /msgto USER MESSAGE");
                            dos.flush();
                            continue;
                        }
                        String rcvUser = commandDetails[1];
                        if (!validOnlineUser(rcvUser)) {
                            dos.writeUTF("User Invalid Or Offline: " + rcvUser);
                            dos.flush();
                            continue;
                        }
                        String message = "";
                        for (int i = 2; i < commandDetails.length; i++) {
                            message = message + " " + commandDetails[i];
                        }
                        lf.logMessage(rcvUser, message, "messagelog.txt");
                        dos.writeUTF("Message Sent: " + Date.from(Instant.now()) + ": " + message);
                        dos.flush();

                        //TODO: FLUSH MESSAGE TO RECEIVER
                        ActiveUser receivingUser = activeUserMap.get(rcvUser);
                        Socket rcvSocket = receivingUser.userMessageSocket;
                        DataOutputStream rcvDos = null;
                        try {
                            rcvDos = new DataOutputStream(rcvSocket.getOutputStream());
                        }catch(IOException e) {e.printStackTrace();}

                        String messageToRcv = Date.from(Instant.now()) + ", " + newUser.userName + ": " + message;
                        System.out.println("Sending message:" + messageToRcv +" to: "+ rcvUser + " from: " + newUser.userName);
                        rcvDos.writeUTF(messageToRcv);
                        rcvDos.flush();
                        continue;
                    }

                    /*
                        CHECKING ACTIVE USERS
                     */
                    else if (commandBase.equals("/activeuser")){
                        String activeUsers = lf.displayActiveUsers(newUser, clientSocket);
                        dos.writeUTF(activeUsers);
                        dos.flush();
//                        sendAlertInt(clientSocket, -1);
                        continue;
                    }

                    /*
                        CREATING GROUP CHAT
                     */
                    else if (commandBase.equals("/creategroup")){
                        if (commandDetails.length < 3) {
                            dos.writeUTF("Please Provide Group Name And Members: /creategroup GROUP_NAME USER1 USER2 ... ");
                            dos.flush();
                            continue;
                        }
                        String groupName = commandDetails[1];
                        if(existingGroupChats.containsKey(groupName)) {
                            dos.writeUTF("Group Chat Already Exists: " + groupName);
                            dos.flush();
                            continue;
                        }
                        List<String> groupMembers = new ArrayList<>();
                        groupMembers.add(newUser.userName);
                        //Check all users listed are valid and online
                        for (int i = 2; i <commandDetails.length; i++) {
                            if(!validOnlineUser(commandDetails[i])) {
                                dos.writeUTF("Invalid Group Member: " + commandDetails[i]);
                                dos.flush();
                                continue;
                            }
                            groupMembers.add(commandDetails[i]);
                        }
                        //Create log file and GroupChat instance to add to map
                        String fileName = groupName +"_messagelog.txt";
                        File groupChatLog = new File(fileName);
                        groupChatLog.createNewFile();

                        GroupChat newGroupChat = new GroupChat(groupName, groupChatLog, groupMembers);
                        existingGroupChats.put(groupName, newGroupChat);
                        //Join creator to group
                        newGroupChat.joinedMembers.add(newUser.userName);

                        //Alert user of successful creation
                        String memberString = String.join(", ", groupMembers);
                        dos.writeUTF("Group " + groupName + " Created With Members: " + groupMembers);
                        dos.flush();
//                        sendAlertInt(clientSocket, -1);
                        continue;

                    }
                    /*
                        JOINING GROUP CHAT
                     */
                    else if (commandBase.equals("/joingroup")){
                        if (commandDetails.length < 2) {
                            dos.writeUTF("Please Provide Group Name");
                            dos.flush();
                            continue;
                        }
                        String groupName = commandDetails[1];
                        if (!existingGroupChats.containsKey(groupName)) {
                            dos.writeUTF("Group Chat Does Not Exist");
                            dos.flush();
                            continue;
                        }
                        GroupChat groupChat = existingGroupChats.get(groupName);
                        if (!groupChat.members.contains(newUser.userName)) {
                            dos.writeUTF("You Are Not A Member Of Chat: " + groupName);
                            dos.flush();
                            continue;
                        }
                        groupChat.joinedMembers.add(newUser.userName);
                        dos.writeUTF("You Have Joined The Group");
                        dos.flush();
                        continue;

                    }
                    /*
                        SEND GROUP MESSAGE IN CHAT
                     */
                    else if (commandBase.equals("/groupmsg")){
                        //ERROR CHECKING
                        if (commandDetails.length < 3) {
                            dos.writeUTF("Please Include A Group and A Message: /msgto GROUPNAME MESSAGE");
                            dos.flush();
                            continue;
                        }
                        String groupName = commandDetails[1];
                        if (!existingGroupChats.containsKey(groupName)) {
                            dos.writeUTF("Group Chat Does Not Exist");
                            dos.flush();
                            continue;
                        }
                        GroupChat groupChat = existingGroupChats.get(groupName);
                        if (!groupChat.members.contains(newUser.userName)) {
                            dos.writeUTF("You Are Not A Member Of Chat: " + groupName);
                            dos.flush();
                            continue;
                        }
                        if (!groupChat.joinedMembers.contains(newUser.userName)) {
                            dos.writeUTF("You Have Not Joined Chat: " + groupName);
                            dos.flush();
                            continue;
                        }

                        //All good to continue
                        String message = "";
                        for (int i = 2; i < commandDetails.length; i++) {
                            message = message + " " + commandDetails[i];
                        }

                        dos.writeUTF("Sent message: " + message + " to group: " + groupName);
                        dos.flush();

                        //log message in group chat log
                        lf.logMessage(newUser.userName, message, groupChat.logFile.getName());

                        //SEND MESSAGE TO ALL ONLINE USERS
                        boolean aMemberIsActive = false;
                        for (String user : groupChat.members) {
                            //If a user is active, send message to them
                            if (activeUserMap.containsKey(user) && !user.equals(newUser.userName)) {
                                aMemberIsActive = true;
                                ActiveUser onlineMember = activeUserMap.get(user);
                                Socket memberMessageSocket = onlineMember.userMessageSocket;
                                DataOutputStream messageStream = null;

                                try {
                                    messageStream = new DataOutputStream(memberMessageSocket.getOutputStream());
                                }catch (IOException e) {e.printStackTrace();}

                                String messageToSend = Date.from(Instant.now()) + ", " + groupName + ", " + newUser.userName + ": " + message;
                                messageStream.writeUTF(messageToSend);
                                messageStream.flush();
                                continue;

                            }
                        }




                    }
                    /*
                        LOGOUT FROM SERVER
                     */
                    else if (commandBase.equals("/logout")){
                        dos.writeUTF("You Are Logged Out: " + userName);
                        dos.flush();
                        logUserOut(newUser);
                        updateSequenceNums();
                        System.out.println("===== User Disconnected. User: " + clientID);
                        clientAlive = false;
                    }
                    else if (commandBase.equals("/p2pvideo")) {
                        if (commandDetails.length < 3) {
                            dos.writeUTF("Please Include A User and A File: /p2pvideo USER FILENAME");
                            dos.flush();
                            continue;
                        }
                        String audienceUser = commandDetails[1];
                        //If audience user offline, alert presenter user
                        if (!activeUserMap.containsKey(audienceUser)) {
                            dos.writeUTF("User Is Offline");
                            dos.flush();
                            sendAlertInt(clientSocket, 0);
                            continue;
                        }
                        //If audience user online, send details to presenter user to start P2P
                        //Also send presenter username as that is not stored by presenter
                        else {
                            ActiveUser activeUser = activeUserMap.get(audienceUser);
                            String audienceIPAddress = activeUser.userAddress;
                            Integer audienceUDPPort = activeUser.userUDPPort;

                            dos.writeUTF(audienceIPAddress+ " " + audienceUDPPort + " " + newUser.userName);
                            dos.flush();
                            sendAlertInt(clientSocket, 3);
                        }


                    }

                    else {
                        dos.writeUTF("Error: Invalid Command!: " + commandBase);
                        dos.flush();
                        continue;
                    }
                }catch (EOFException e) {
                    System.out.println("===== User Disconnected. User: " + clientID);
                    clientAlive = false;
                }
                catch (IOException e) {e.printStackTrace();}
            }
        }
    }

    public static void main(String[] args) throws IOException {
        //Ensure correct input when starting server
        if (args.length != 2) {
            System.out.println("===== Error usage: java Server SERVER_PORT MAX_FAILED_CONSECUTIVE_ATTEMPTS =====");
            return;
        }

        serverPort = Integer.parseInt(args[0]);
        maxTries = Integer.parseInt(args[1]);

        //Ensure valid amount of max tries
        if (maxTries < 1 || maxTries > 5) {
            System.out.println("===== Error usage: Invalid number of MAX_FAILED_CONSECUTIVE_ATTEMPTS: " + maxTries + " Please enter an amount between 1 and 5 =====");
            return;
        }

        //Define server socket and listen for client connection requests
        serverSocket = new ServerSocket(serverPort);

        System.out.println("===== Server Running. Awaiting client connection requests... =====");

        //Keep server alive and listening
        while (true) {
            //When new request received, accept and start a new thread for each user
            Socket clientSocket = serverSocket.accept();
            Socket messageSocket = serverSocket.accept();
            ClientThread clientThread = new ClientThread(clientSocket, messageSocket);
            clientThread.start();
            System.out.println("Made connection");
        }
    }


    /*
        AUTHENTICATION HELPER FUNCTIONS
     */

    //Collects credentials provided by client in giveCredentials()
    protected static String[] getCredentials(Socket s) {
        System.out.println("Received credentials");
        String[] credentials = {};

        try {
            DataInputStream dis = new DataInputStream(s.getInputStream());
            String streamData = dis.readUTF();
            credentials = streamData.split(" ");
        } catch (IOException e) {
            e.printStackTrace();
        }

        return credentials;
    }

    //Attempts login using retrieved credentials. Returns a LoginResult obj with details of success/failure
    protected static LoginResult login(Socket s) {
        System.out.println("Attempting login");
        String[] credentials = getCredentials(s);
        String userName = credentials[0];
        String password = credentials[1];

        /*
            First check if UserName has already attempted before + if they're already blocked.
            Block if they have attempted too many times
         */

        if (failedLoginMap.containsKey(userName)) {
            FailedLogin fl = failedLoginMap.get(userName);
            //Block if num times attempted > max
            if (fl.failedAuthCount > maxTries - 2) {
                Date now = Date.from(Instant.now());
                alertUserBlocked(s);
                //Reset attempt count so user can start fresh after block period
                fl.failedAuthCount = 0;
                blockedUsersWithTimeMap.put(userName, now);

                //Return a failed loginresult, with blocked user
                return new LoginResult(userName, false, true, now);
            }
        }

        if (blockedUsersWithTimeMap.containsKey(userName)) {
            Date loginTime = blockedUsersWithTimeMap.get(userName);
            //If less than 10s passed, notify user
            if (((Date.from(Instant.now()).getTime() - loginTime.getTime()) / 1000) <= 10) {
                alertUserBlocked(s);
                //Return a failed loginresult, with blocked user
                return new LoginResult(userName, false, true, loginTime);
            } else {
                blockedUsersWithTimeMap.remove(userName);
            }
        }
        /*
            If not attempted before or not blocked, attempt to validate
         */

        BufferedReader credentialsReader = null;
        try {
            credentialsReader = new BufferedReader(new FileReader("credentials.txt"));
            String lineOfCreds = credentialsReader.readLine();
            while (lineOfCreds != null) {
                String[] cmpCreds = lineOfCreds.split(" ");

                //Successfully logged in
                if (cmpCreds[0].equals(userName) && cmpCreds[1].equals(password)) {
                    Date now = Date.from(Instant.now());
                    ActiveUser newUser = new ActiveUser(userName, activeUserMap.size() + 1, now);
                    activeUserMap.put(userName, newUser);
                    failedLoginMap.remove(userName);
                    alertLoginSuccess(s);
                    return new LoginResult(userName, true, false, now);
                }

                //Credential mismatch - failed login
                else if (cmpCreds[0].equals(userName) && !cmpCreds[1].equals(password)) {
                    alertLoginFailIncorrectPassword(s);
                    FailedLogin fl = failedLoginMap.get(userName);
                    if (fl == null) {
                        failedLoginMap.put(userName, new FailedLogin(userName, 1, Date.from(Instant.now())));
                    } else {
                        fl.failedAuthCount = fl.failedAuthCount + 1;
                    }
                    return new LoginResult(userName, false, false, null);
                }
                lineOfCreds = credentialsReader.readLine();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (credentialsReader != null) {
                try {
                    credentialsReader.close();
                } catch (IOException e) {
                }
                ;
            }
        }
        alertLoginFailInvalidUser(s, userName);
        return new LoginResult(null, false, false, null);
    }

    protected static void alertLoginSuccess(Socket s) {
        String response = "Login Success - Welcome!";
        sendAlertResponse(s, response);
        sendAlertInt(s, SUCCESS);
    }

    protected static void alertLoginFailIncorrectPassword(Socket s) {
        String response = "Incorrect Password - Please Try Again!";
        sendAlertResponse(s, response);
        sendAlertInt(s, FAILURE);
    }

    protected static void alertLoginFailInvalidUser(Socket s, String incorrectName) {
        String response = "Login Failed - Could Not Find User " + incorrectName;
        sendAlertResponse(s, response);
        sendAlertInt(s, FAILURE);
    }

    protected static void alertUserBlocked(Socket s) {
        String response = "This User is Blocked - Please Try Again Later";
        sendAlertResponse(s, response);
        sendAlertInt(s, FAILURE);
    }
    /*
        CLIENT COMMUNICATION FUNCTIONS
     */

    //Sends integer alerts to client
    protected static void sendAlertInt(Socket s, Integer i) {
        DataOutputStream dos;
        try {
            dos = new DataOutputStream(s.getOutputStream());
            dos.writeInt(i);
            dos.flush();
        } catch(IOException e){e.printStackTrace();}
    }

    //Sends string responses to client
    protected static void sendAlertResponse(Socket s, String response) {
        DataOutputStream dos;
        try {
            dos = new DataOutputStream(s.getOutputStream());
            dos.writeUTF(response);
            dos.flush();
        } catch(IOException e){e.printStackTrace();}
    }

    //Receives UDP Port from client
    public static Integer getUserUDPPort(Socket s) {
        DataInputStream dis;
        Integer UDPPort = 0;
        try {
            dis = new DataInputStream(s.getInputStream());
            UDPPort = dis.readInt();
        } catch(IOException e) {e.printStackTrace();}
        return UDPPort;
    }

    /*
        USER LOG MANAGEMENT FUNCTIONS
     */

    //Removes user from active users and removes them from the user log
    protected static void logUserOut(ActiveUser user) {
        activeUserMap.remove(user.userName);
        LogFunctions lf = new LogFunctions();
        lf.removeUserFromLog(user);
    }

    protected static void updateSequenceNums() {
        int numDevicesCount = 1;
        LogFunctions lf = new LogFunctions();
        for (ActiveUser user : activeUserMap.values()) {
            user.sequenceNum = numDevicesCount;
            lf.updateSeqNumbersInLog(user);
        }
    }

      /*
        VALIDATING USER PRESENCE
     */

    protected static boolean validOnlineUser(String userName) {
        if(activeUserMap.containsKey(userName)) {
            return true;
        }
        else {return false;}
    }
}


/*
    AUTHENTICATION HELPER CLASSES
 */

//Class holds essential login details
class LoginResult {
    String userName;
    boolean success;
    boolean blocked;
    Date loginTime;

    LoginResult(String userName, boolean success, boolean blocked, Date loginTime) {
        this.userName = userName;
        this.success = success;
        this.blocked = blocked;
        this.loginTime = loginTime;
    }
}
//Class used to store whether a user has failed to log-in previously
class FailedLogin {
    String userName;
    Integer failedAuthCount;
    Date lastFailedAuthTime;

    FailedLogin(String userName, Integer failedAuthCount, Date lastFailedAuthTime) {
        this.userName = userName;
        this.failedAuthCount = failedAuthCount;
        this.lastFailedAuthTime = lastFailedAuthTime;
    }
}

//Class used to store details of users active on server
class ActiveUser {
    String userName;
    String userAddress;
    Integer userUDPPort;
    Integer sequenceNum;
    Date loginTime;
    Socket userSocket;
    Socket userMessageSocket;

    ActiveUser(String userName, Integer sequenceNum, Date loginTime) {
        this.userName = userName;
        this.sequenceNum = sequenceNum;
        this.loginTime = loginTime;
    }
}

/*
    GROUP CHAT HELPER CLASS
 */
class GroupChat {
    File logFile;
    String groupChatName;
    List<String> members;
    List<String> joinedMembers;

    GroupChat(String groupChatName, File logFile, List<String> members) {
        this.groupChatName = groupChatName;
        this.logFile = logFile;
        this.members = members;
        this.joinedMembers = new ArrayList<>();
    }
}