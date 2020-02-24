package com.company;

/*
 * This is a simple server application
 * This server receive a file from a Android client and save it in a given place.
 * Author by Jack
 */

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

import java.text.SimpleDateFormat;
import java.util.Date;

public class Server {

    private static ServerSocket serverSocket;
    private static Socket clientSocket;
    private static InputStream inputStream;
    private static FileOutputStream fileOutputStream;
    private static BufferedOutputStream bufferedOutputStream;
    private static int filesize = 10000000; // filesize temporary hardcoded
    private static int bytesRead;
    private static int current = 0;

    public static void main(String[] args) throws IOException {


        serverSocket = new ServerSocket(8888);  //Server socket

        System.out.println("Server started. Listening to the port 8888");

        int index = 0;
        while (true) {
            clientSocket = serverSocket.accept();
            Date date = new Date();
            SimpleDateFormat sdf2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String dateNowStr = sdf2.format(date);

            byte[] mybytearray = new byte[filesize];    //create byte array to buffer the file

            inputStream = clientSocket.getInputStream();
            String fileName = "Airbeam_data_" + dateNowStr + ".zip";
            fileOutputStream = new FileOutputStream(fileName);
            bufferedOutputStream = new BufferedOutputStream(fileOutputStream);

            System.out.println("Receiving...");

            //following lines read the input slide file byte by byte
            bytesRead = inputStream.read(mybytearray, 0, mybytearray.length);
            current = bytesRead;

            do {
                bytesRead = inputStream.read(mybytearray, current, (mybytearray.length - current));
                if (bytesRead >= 0) {
                    current += bytesRead;
                }
            } while (bytesRead > -1);

            bufferedOutputStream.write(mybytearray, 0, current);
            bufferedOutputStream.flush();
            bufferedOutputStream.close();
            inputStream.close();
            clientSocket.close();

            System.out.println("Sever recieved the file: " + fileName);
            index ++;
        }
    }
}
