/*
 * Copyright (C) 2020 Not Alexa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package not.alexa.upnp.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Very simple HTTP server. This server delivers outer content and closes any connection
 * after delivery. Therefore, the server is acceptable for
 * <ul>
 * <li>short content
 * <li>deliverying "singular" content (like UPnP description files with no "inner" references to
 * HTML content).
 * </ul>
 * 
 * @author notalexa
 *
 */
public class HttpMiniServer extends Thread implements Server {
    protected ServerSocket serverSocket;
    protected int port;
    protected ExecutorService executors;
    protected Map<String,byte[]> content;
    protected boolean closed;
    
    /**
     * Create a server for the given port and the given content
     * 
     * @param port the port the server should listen on
     * @param content the content map
     * @param executors the workers for handling a request
     */
    public HttpMiniServer(int port, Map<String,byte[]> content,ExecutorService executors) {
        super("http");
        setDaemon(true);
        this.port=port;
        this.content=content;
        this.executors=executors;
    }
    
    public void run() {   
        try {
            serverSocket=new ServerSocket(port);
            while(!closed) {
                Socket clientSock=serverSocket.accept();
                clientSock.setSoTimeout(1000);
                handleRequest(clientSock);
            }
        } catch(Throwable t) {
            if(!closed) {
                t.printStackTrace();
            }
        }
    }
    
    protected void handleRequest(Socket socket) {
        try {
            BufferedReader reader=new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String line=null;
            String page=null;
            while((line=reader.readLine())!=null) {
                line=line.trim();
                if(line.length()==0) {
                    break;
                } else if(line.startsWith("GET ")&&line.endsWith("HTTP/1.1")) {
                    page=line.substring(3,line.length()-8).trim();
                }
            }
            if(page!=null&&page.length()>0) {
                while(page.charAt(0)=='/') {
                    page=page.substring(1);
                }
                byte[] body=content.get(page);
                if(body!=null) {
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\n"
                        +"connection: close\r\n"
                        +"content-type: text/xml\r\n"
                        +"content-length: "+body.length+"\r\n\r\n").getBytes());
                    socket.getOutputStream().write(body);
                    return;
                }
            }
            socket.getOutputStream().write(("HTTP/1.1 404 NOT FOUND\r\n"
                    +"connection: close\r\n"
                    +"content-length: 0\r\n\r\n").getBytes());
        } catch(Throwable t) {
            if(!closed) {
                t.printStackTrace();
            }
        } finally {
            try {
                socket.close();
            } catch(Throwable t) {
            }
        }
    }

    @Override
    public void close() throws IOException {
        closed=true;
        serverSocket.close();
    }
}
