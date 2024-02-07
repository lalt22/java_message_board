import java.io.*;
import java.net.*;
import java.util.*;
import java.time.*;


public class LogFunctions {
    public LogFunctions() {}

    //Log new user
    public void logNewUser(ActiveUser user) {
        System.out.println("Made new file");
        PrintWriter writer;
        try {
            File userLogFile = new File("userlog.txt");
            userLogFile.createNewFile();
            writer = new PrintWriter(new FileWriter(userLogFile, true));
            writer.write(user.sequenceNum + "; " + user.loginTime + "; " + user.userName + "; " + user.userAddress + "; " + user.userUDPPort + System.lineSeparator());
            writer.close();
        } catch (IOException e) {e.printStackTrace();}
    }

    public void logMessage(String user, String message, String fileName) {
        System.out.println("Logging message: "+ message);
        PrintWriter msgLogWriter;
        try{
            File msgLogFile = new File(fileName);
            msgLogFile.createNewFile();
            Integer msgCount = getNumMsgsInLog(fileName);
            msgLogWriter = new PrintWriter(new FileWriter(msgLogFile, true));
            msgLogWriter.write(msgCount + "; " + Date.from(Instant.now()) + "; " + user + "; " + message + System.lineSeparator());
            msgLogWriter.close();
        }catch (IOException e) {e.printStackTrace();}
    }

    public int getNumMsgsInLog(String fileName) {
        int messages = 1;
        try {
            BufferedReader logReader = new BufferedReader(new FileReader(fileName));
            while(logReader.readLine() != null) messages++;
            logReader.close();} catch (IOException e) {e.printStackTrace();}
        return messages;
    }

    public String displayActiveUsers(ActiveUser user, Socket s) {
        String result = "";
        BufferedReader userLogReader;
        File userLog = new File("userlog.txt");
        Integer activeUsersCount = 0;

        try {
            userLogReader = new BufferedReader(new FileReader(userLog));
            String userLogEntry;
            while ((userLogEntry = userLogReader.readLine()) != null) {
                String[] logDetails = userLogEntry.split("; ");
                if (!logDetails[2].equals(user.userName)) {
                    userLogEntry = "Time Joined: " + logDetails[1] + " Name: " + logDetails[2] + " Host: " + logDetails[3] + " Port:" + logDetails[4];
                    result = result + userLogEntry;
                    activeUsersCount = activeUsersCount + 1;
                }
            }
            userLogReader.close();
            if (activeUsersCount == 0) {
                result = "No Active Users";
            }
        }catch(IOException e) {e.printStackTrace();}
        return result;
    }

    public void removeUserFromLog(ActiveUser user) {
        File userLog = new File("userlog.txt");
        File tempUserLog = new File("templog.txt");

        //Rewrite log file with every log except the user being removed
        try {
            BufferedReader logReader = new BufferedReader(new FileReader(userLog));
            BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempUserLog));

            String currentLog;
            while((currentLog = logReader.readLine()) != null) {
                String[] logDetails = currentLog.split("; ");
                if (logDetails[2].equals(user.userName)) {
                    continue;
                }
                tempWriter.write(currentLog);
                currentLog = logReader.readLine();
            }
            tempWriter.close();
            logReader.close();
            userLog.delete();
            if ( tempUserLog.renameTo(userLog)) {
                System.out.println("Successfully renamed");
            }
            tempUserLog.delete();
        } catch(IOException e) {e.printStackTrace();}
    }

    public void updateSeqNumbersInLog(ActiveUser user) {
        File userLog = new File("userlog.txt");
        File tempUserLog = new File("templog.txt");

        //Rewrite log file with every log except the user being removed
        try {
            BufferedReader logReader = new BufferedReader(new FileReader(userLog));
            BufferedWriter tempWriter = new BufferedWriter(new FileWriter(tempUserLog));

            String currentLog;
            int deletionCount = 0;
            while((currentLog = logReader.readLine()) != null) {
                String[] logDetails = currentLog.split("; ");
                if (logDetails[2].equals(user.userName) && deletionCount == 0) {
                    deletionCount = deletionCount + 1;
                }
                tempWriter.write(user.sequenceNum + "; " + user.loginTime + "; " + user.userName + "; " + user.userAddress + "; " + user.userUDPPort + System.lineSeparator());
                currentLog = logReader.readLine();
            }
            tempWriter.close();
            logReader.close();
            userLog.delete();
            if(tempUserLog.renameTo(userLog)) {
                System.out.println("Successfully renamed during updating");
            }
        } catch(IOException e) {e.printStackTrace();}
    }
}
