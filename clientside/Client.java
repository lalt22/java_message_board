import java.net.*;
import java.io.*;
import java.util.Scanner;

public class Client {
    //Store server and UDP details given by user
    private static String serverHost;
    private static Integer serverPort;
    private static Integer UDPPort;

    private static class ReceiverThread extends Thread {
        private final Socket messageSocket;
        ReceiverThread(Socket s){
            this.messageSocket = s;
        };
        @Override
        public void run() {
            super.run();
            MessageReceiver messageReceiver = new MessageReceiver(serverHost, serverPort);
            messageReceiver.receiveMessage(messageSocket);
        }
    }

    private static class UDPThread extends Thread {
        private final DatagramSocket UDPSocket;
        UDPThread(DatagramSocket UDPSocket) {
            this.UDPSocket = UDPSocket;
        }

        @Override
        public void run() {
            super.run();
            //Each user's audience capability is held in the UDP Thread, listening for P2P conn. requests
            Audience audience = new Audience(serverHost, serverPort);
            audience.receiveAndCreateUDPFile(this.UDPSocket);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) {
            System.out.println("===== Error usage: java Client SERVER_IP SERVER_PORT UDP_PORT =====");
            return;
        }

        serverHost = args[0];
        serverPort = Integer.parseInt(args[1]);
        UDPPort = Integer.parseInt(args[2]);

        //Create TCP and UDP sockets
        Socket clientSocket = new Socket(serverHost, serverPort);
        //Create socket for thread to always be listening for server messages
        Socket clientMessageSocket = new Socket(serverHost, serverPort);
        DatagramSocket UDPSocket = new DatagramSocket(UDPPort);

        //Get input and output streams for data sending/receiving with server
        DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
        DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());

        //Get input stream to receive messages
        DataInputStream messageDis = new DataInputStream(clientMessageSocket.getInputStream());


        //Read from command line
        BufferedReader commandLineReader = new BufferedReader(new InputStreamReader(System.in));
        while(true) {
            giveCredentials(clientSocket, dos);
            if (isAuthenticated(clientSocket)) {
                sendUDPPort(clientSocket);

                //Create UDP Thread to run concurrently for P2P
                UDPThread uDPThread = new UDPThread(UDPSocket);
                uDPThread.start();

                //Create message receiving thread to always be listening for messages
                ReceiverThread receiverThread = new ReceiverThread(clientMessageSocket);
                receiverThread.start();

                while (true) {
                    //Take input commands
                    System.out.println("===== Enter one of the following commands (/msgto, /activeuser, /creategroup, /joingroup, /groupmsg, /logout, /p2pvideo): ");

                    String command = commandLineReader.readLine();
                    //Flush command to server
                    PrintWriter osWriter = new PrintWriter(new OutputStreamWriter(dos));
                    osWriter.println(command);
                    osWriter.flush();

                    //Get response to command from server - cast as string
                    String serverResponse = (String) dis.readUTF();

                    if (command.contains("/msgto")) {
                        System.out.println(serverResponse);
                        continue;
                    }

                    else if (command.contains("/creategroup")) {
                        System.out.println(serverResponse);
                        continue;
                    }

                    else if (command.contains("/joingroup")) {
                        System.out.println(serverResponse);
                        continue;
                    }

                    else if (command.equals("/logout")) {
                        System.out.println(serverResponse);
                        clientSocket.close();
                        dos.close();
                        dis.close();
                        return;
                    }

                    else if (command.contains("/p2pvideo")) {
                        String[] commandDetails = command.split(" ");
//                        System.out.println(serverResponse);
                        if (commandDetails.length < 3) {
                            System.out.println(serverResponse);
                            continue;
                        }
                        boolean audienceAlive = audienceIsOnline(dis);
                        if(audienceAlive) {
                            String[] UDPInfo = serverResponse.split(" ");
                            String audienceIPAddress = UDPInfo[0];
                            Integer audienceUDPPort = Integer.parseInt(UDPInfo[1]);
                            String presenterName = UDPInfo[2];
                            String fileName = commandDetails[2];
                            File fileToSend = new File(fileName);
                            if(!fileToSend.exists()) {
                                System.out.println("File Does Not Exist At Presenter");
                                continue;
                            }
                            System.out.println("Sending file: "+ fileName +" to " + audienceIPAddress + " " + audienceUDPPort + " from "+ presenterName);

                            Presenter presenter = new Presenter(audienceIPAddress, audienceUDPPort, presenterName);
                            presenter.sendUDPFile(fileToSend, UDPSocket);
                            continue;
                        }
                        else {
                            System.out.println(serverResponse);
                            continue;
                        }

                    }

                    else {
                        System.out.println(serverResponse);
                        continue;
                    }
                }
            }
        }
    }

    /*
        AUTHENTICATION FUNCTIONS AND DETAILS
     */

    //Gets client credentials in String format and outputs them to the server for checking
    public static void giveCredentials(Socket s, DataOutputStream dos) {
        System.out.println("Enter Username: ");
        Scanner scan = new Scanner(System.in);
        String userName = scan.next();
        System.out.println("Enter Password: ");
        String password = scan.next();

        try {
            assert dos != null;
            dos.writeUTF(userName + " " + password);
            dos.flush();
        } catch(IOException e) {e.printStackTrace();}
    }

    public static boolean isAuthenticated(Socket s) {
        DataInputStream dis;
        Integer alertInt = 0;
        String alertResponse = "";

        try {
            dis = new DataInputStream(s.getInputStream());
            alertResponse = dis.readUTF();
            alertInt = dis.readInt();
            System.out.println(alertResponse);
        } catch (IOException e) {e.printStackTrace();}

        if (alertInt == 0) {
            return false;
        } else return true;
    }

    /*
        GENERAL COMMUNICATION FUNCTIONS
     */
    public static void sendUDPPort(Socket s) {
        DataOutputStream dos;
        try {
            dos = new DataOutputStream(s.getOutputStream());
            dos.writeInt(UDPPort);
            dos.flush();
        }catch (IOException e) {e.printStackTrace();}
    }

    public static boolean messageInStream(DataInputStream dis) {
        Integer alert = -1;
        try {
            if (dis.available() > 0) {
                alert = dis.readInt();
            }
        }catch(IOException e) {e.printStackTrace();}
        if (alert == 2) {
            return true;
        }
        else return false;
    }

    public static boolean audienceIsOnline(DataInputStream dis) {
        System.out.println("Checking audience is online");
        Integer online = 0;
        try {
            online = dis.readInt();
        }catch (IOException e) {e.printStackTrace();}
        if (online == 3) {
            System.out.println("Audience online");
            return true;
        }
        return false;
    }
}
