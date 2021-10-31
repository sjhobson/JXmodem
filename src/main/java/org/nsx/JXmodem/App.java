package org.nsx.JXmodem;

import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.Arrays;

/**
 * quick and dirty test framework
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        int port = 63722;

        try {
            // Test JXmodem against itself
            testLocalStringTransfer();
            testLocalFileTransfer();

            // Test JXmodem against another Xmodem program (I used sx and rx)
            // testReceiveFromExternal(InetAddress.getLocalHost(), port);
            // testSendToExternal(InetAddress.getLocalHost(), port);            
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        
    }

    public static void testLocalStringTransfer() throws Exception
    {
        var inStreamA = new PipedInputStream();
        var outStreamA = new PipedOutputStream();
        var inStreamB = new PipedInputStream(outStreamA);
        var outStreamB = new PipedOutputStream(inStreamA);

        Thread tA;
        Thread tB;

        // TEST SENDING STRING
        String testString =
            "All human beings are born free and equal in dignity and rights. They are endowed with reason and conscience and should act towards one another in a spirit of brotherhood.";

        tB = new Thread(new Runnable() {
            @Override
            public void run()
            {
                String result = "";
                try {
                    var xmB = new JXmodem(inStreamB, outStreamB);
                    result = xmB.receiveString();
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("Thread B result: " + result);
            }
        });

        tA = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Boolean result = null;
                try {
                    var xmA = new JXmodem(inStreamA, outStreamA);
                    result = xmA.sendString(testString);
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Thread A result: " + result);
            }
        });

        System.out.println("TESTING SEND STRING");
        tA.start();
        tB.start();
        tA.join();
        tB.join();
    }

    public static void testLocalFileTransfer() throws Exception
    {
        var inStreamA = new PipedInputStream();
        var outStreamA = new PipedOutputStream();
        var inStreamB = new PipedInputStream(outStreamA);
        var outStreamB = new PipedOutputStream(inStreamA);

        Thread tA;
        Thread tB;

        // TEST SENDING FILE
        
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] inFileHash = null;
        byte[] outFileHash = null;
        try(var fileIn = new FileInputStream("cat.png")) {
            inFileHash = sha256.digest(fileIn.readAllBytes());
        }
        tB = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Boolean result = null;
                try {
                    var xmB = new JXmodem(inStreamB, outStreamB);
                    result = xmB.receiveFile("catII.png");
                }
                catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                System.out.println("Thread B result: " + result);
            }
        });

        tA = new Thread(new Runnable() {
            @Override
            public void run()
            {
                Boolean result = null;
                try {
                    var xmA = new JXmodem(inStreamA, outStreamA);
                    result = xmA.sendFile("cat.png");
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                System.out.println("Thread A result: " + result);
            }
        });

        System.out.println("\n\nTESTING SEND FILE");
        tA.start();
        tB.start();
        tA.join();
        tB.join();
        

        try (var fileIn = new FileInputStream("catII.png")) {
            outFileHash = sha256.digest(fileIn.readAllBytes());
        }

        boolean hashMatch = Arrays.compare(inFileHash, outFileHash) == 0;
        System.out.println("Hashes match: " + hashMatch);
    }

    // Tested using sx on unix. sx should be waiting for a connection already
    public static void testReceiveFromExternal(InetAddress ipaddr, int port) 
            throws Exception 
    {
        System.out.println("\n\nTESTING RECEIVE FROM EXTERNAL SOURCE");
        Socket clientSocket = new Socket(ipaddr, port);
        var inStream = clientSocket.getInputStream();
        var outStream = clientSocket.getOutputStream();

        boolean result = false;
        var xm = new JXmodem(inStream, outStream);
        while (!result)
            result = xm.receiveFile("catFromEx.png");
        System.out.println("RESULT: " + result);
        inStream.close();
        outStream.close();
        clientSocket.close();
    } 

    // Tested using rx on unix. rx should be awaiting a connection already
    public static void testSendToExternal(InetAddress ipaddr, int port) 
            throws Exception
    {
        System.out.println("\n\nTESTING SEND TO EXTERNAL SOURCE");
        Socket clientSocket = new Socket(ipaddr, port);
        var inStream = clientSocket.getInputStream();
        var outStream = clientSocket.getOutputStream();
        
        boolean result = false;
        var xm = new JXmodem(inStream, outStream);
        while (!result)
            result = xm.sendFile("cat.png");
        System.out.println("RESULT: " + result);
        inStream.close();
        outStream.close();
        clientSocket.close();
    }
}