package ServerSide;

import SQL.SQLManager;
import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.sql.*;
import java.util.List;

/**
 * Server backend for the KMes server<br/>
 * Manages all inputs from user clients
 *
 * @version 27.06.2022
 * @author Joshua H. | KaitoKunTatsu#3656
 * */
class InputHandler extends Thread {

    private final SocketAcceptor socketAcceptor;
    private final SQLManager sqlmanager;

    List<List<Object>> clients_in_out = SocketAcceptor.getSockets();

    private boolean running;

    protected InputHandler() throws IOException, SQLException, ClassNotFoundException {
        socketAcceptor = new SocketAcceptor();
        socketAcceptor.start();
        sqlmanager = new SQLManager("src/main/java/SQL/kmes.db");
        running = true;
    }


    private void handleSendRequest(int author_socket_index, String[] request) throws IOException
    {
        String receiver = request[2];
        for (List<Object> client : clients_in_out)
        {
            if (client.get(3).equals(receiver))
            {
                if (!clients_in_out.get(author_socket_index).get(3).equals(receiver))
                {
                    writeToSocket(clients_in_out.indexOf(client), "KMES;message;"+clients_in_out.get(author_socket_index).get(3)+";"+request[3]);
                }
                else
                {
                    writeToSocket(author_socket_index, "KMES;error;You can't message yourself;Couldn't send message");
                }
                return;
            }
        }
        writeToSocket(author_socket_index, "KMES;error;User not found;Couldn't send message");
    }

    private void handleRegistrationRequest(int socket_index, String[] request) throws IOException
    {
        if (request.length<4)
        {
            writeToSocket(socket_index, "KMES;error;Please fill out every text field;Registration failed");
            System.out.println("Password invalid");
        }
        else if (clients_in_out.get(socket_index).get(3).equals(""))
        {
            String username = request[2];
            if (!sqlmanager.userExists(username))
            {
                PasswordHasher hasher = new PasswordHasher();
                sqlmanager.insertNewUser(username,  hasher.getHash(request[3]));
                writeToSocket(socket_index, "KMES;loggedIn;"+username);
            }
            else
            {
                writeToSocket(socket_index, "KMES;error;This username is already taken, please choose another one;Registration failed");
            }
        }
        else
        {
            writeToSocket(socket_index, "KMES;error;Log out before registration;Registration failed");
        }
    }

    private void handleLoginRequest(int socket_index, String[] request) throws IOException {
        if (request.length<4)
        {
            writeToSocket(socket_index, "KMES;error;Please fill out every text field;Login failed");
            System.out.println("Password invalid");
            return;
        }

        PasswordHasher hasher = new PasswordHasher();
        String username = request[2];
        String password = hasher.getHash(request[3]);

        System.out.println("Password validation..");
        if (sqlmanager.check_login(username, password))
        {
            clients_in_out.get(socket_index).set(3, username);
            writeToSocket(socket_index, "KMES;loggedIn;"+username);
            System.out.println("Password valid");
        }
        else
        {
            writeToSocket(socket_index, "KMES;error;Password or username incorrect;Login failed");
            System.out.println("Password invalid");
        }
    }

    private void writeToSocket(int socket_index, String str) throws IOException {
        try {
            ((DataOutputStream)clients_in_out.get(socket_index).get(1)).writeUTF(str);
        } catch (IOException e) {
            SocketAcceptor.closeSocket(socket_index);
        }
    }

    /**
     *
     * */
    public void run() {
        while (running)
        {
            for (int i = 0; i < clients_in_out.toArray().length; i++)
            {
                Socket current_socket = ((Socket)clients_in_out.get(i).get(0));
                DataInputStream reader = ((DataInputStream)clients_in_out.get(i).get(2));

                System.out.printf("Checking socket[%d]..\n", i+1);
                try {
                    String input = reader.readUTF();
                    if (current_socket.isClosed() || !current_socket.isConnected())
                    {
                        SocketAcceptor.closeSocket(i);
                        System.out.printf("[%d]Socket closed\n", i+1);
                        i--;
                    }
                    else
                    {
                        System.out.printf("[%d]Socket still connected\n", i+1);
                        String[] request = input.split(";");
                        if (!request[0].equals("KMES"))
                        {
                            current_socket.close();
                            clients_in_out.remove(i);
                        }
                        else
                        {
                            switch (request[1])
                            {
                                case "login":
                                    handleLoginRequest(i, request);
                                    break;
                                case "register":
                                    handleRegistrationRequest(i, request);
                                    break;
                                case "send":
                                    handleSendRequest(i, request);
                                    break;
                                case "logout":
                                    clients_in_out.get(i).set(3,"");
                                    break;
                                case "doesUserExist":
                                    String username = request[2];
                                    if (sqlmanager.userExists(username))
                                    {
                                        writeToSocket(i, "KMES;userExists;"+username);
                                    }
                                    else
                                    {
                                        writeToSocket(i, "KMES;error;User does not exist: "+username+";Adding contact failed");
                                    }
                                default:
                            }
                        }
                    }

                }
                catch (SocketTimeoutException ex) {}
                catch (IOException e)
                {
                    clients_in_out.remove(i);
                    System.out.printf("[%d]Socket closed due to IOExcception\n", i+1);
                    i--;
                }

            }
        }
    }


    public static void main(String[] args) throws IOException, SQLException, ClassNotFoundException {
        InputHandler inputHandler = new InputHandler();
        inputHandler.run();
    }
}
