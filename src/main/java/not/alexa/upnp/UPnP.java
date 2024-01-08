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
package not.alexa.upnp;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import not.alexa.upnp.net.NetworkHelper;
import not.alexa.upnp.net.NetworkHelper.InterfaceInfo;
import not.alexa.upnp.net.Server;

/**
 * The base instance of this universal plug and play discovery and description implementation. For a description of UPnP see the <a href="https://en.wikipedia.org/wiki/Universal_Plug_and_Play">Wikipedia
 * Page</a>. More formal descriptions can be found at the <a href="https://openconnectivity.org/">Open Connectivity Foundation</a>. This implementation supports parts of
 * <a href="http://upnp.org/specs/arch/UPnP-arch-DeviceArchitecture-v1.0.pdf">Version 1.0</a> and is currently restricted to IPv4 addresses.
 * <p>While running, the server listens at a multicast address (239.255.255.250 by default) and port (1900 by default) on any network interface supporting multicast and having a IPv4 address.
 * <br>If the server publishes devices or services, it may also listen on a specific port to publish the descriptions of the devices using the HTTP protocol.
 * <br>Acting as a client, the server sends search messages asking for specific devices or services and listens for responses, routing them to suitable callback classes.
 * <p>The following parameters are configurable:
 * <ul>
 * <li>The multicast address via the constructor (default is 239.255.255.250). 
 * <li>The multicast port via the constructor (default is 1900). 
 * <li>The TTL value of an UPnP message via {@link #setTTL(int)} (in seconds). Default value is 300 (5 minutes).
 * <li>The expected response delay time interval (MX) via {@link #setMX(int)} (in seconds). Default value is 5.
 * <li>The port the HTTP server should listen at via {@link #setHttpPort(int)} (no default value).
 * <li>Should the server send a bye bye message on close? This behaviour can be configured via {@link #sayByeByeOnClose(boolean)} (default value is <code>false</code>).
 * <ul>
 * If running in publishing mode, UPnP devices can be published via the {@link #publish(UPnPMessage...)} method and withdrawn via the {@link #withdraw(UPnPMessage...)} method. 
 * Messages are checked if they are valid to be published. If the http port is configured and the location of the message doesn't refer to an external URL, the description is resolved to
 * the internal http server (referring to the correct ip address in the network) and the http server delivers the description of the device. Otherwise, the location should refer to an external
 * URL.
 * <br>If the server is running in client mode, requests for devices can be registerd using the {@link #startScan(UPnPMessage, UPnPCallback)} method. First argument is the search method,
 * second argument the callback. Search requests can be formulated using the returned {@link UPnPScanner}.
 * <br>Note that the modes are not exclusive. A running instance can be used both in publishing and client mode.
 * 
 * 
 * @author notalexa
 *
 */
public class UPnP implements Closeable {
    public static String getRootDevice() {
        return "upnp:rootdevice";
    }
    public static String getDefaultDeviceURN(String name,int version) {
        return "urn:schemas-upnp-org:device:"+name+":"+version;
    }
    
    private static final Random RANDOM=new Random(System.currentTimeMillis());
    
    private InetAddress multicastAddress;
    private int port=1900;
    private int httpPort=-1;
    private List<UPnPMessage> published=new ArrayList<>();
    private List<InterfaceInfo> interfaceAddresses=new ArrayList<InterfaceInfo>();
    private SocketWorker[] sendSockets=new SocketWorker[0];
    private ScheduledExecutorService executor=Executors.newScheduledThreadPool(2);
    private ScheduledFuture<?> notificationThread;
    private boolean sayByeByeOnClose;
    private int ttl=300;
    private int mx=5;
    private List<Scanner> scanners=new ArrayList<UPnP.Scanner>();
    private Server httpServer;
    private Map<String,byte[]> contentMap=new HashMap<String, byte[]>() {
        /**
         * 
         */
        private static final long serialVersionUID = 1L;

        @Override
        public synchronized byte[] get(Object key) {
            byte[] bytes=super.get(key);
            if(bytes==null) try {
                put((String)key,bytes=resolveContent((String)key));
            } catch(Throwable t) {
                put((String)key,bytes=new byte[0]);
            }
            return bytes.length==0?null:bytes;
        }
        
    };
    private static InetAddress resolveHost(String host) {
        try {
            return InetAddress.getByName(host);
        } catch(Throwable t) {
            throw new IllegalArgumentException("Not a valid host address: "+host);
        }
    }
    
    /**
     * Create an instance with the default multicast address (239.255.255.250) (and default port 1900).
     */
    public UPnP() {
        this("239.255.255.250");
    }
    
    /**
     * Create an instance with the default port (1900).
     * 
     * @param address the multicast address this instance listens at.
     */
    public UPnP(String address) {
        this(address,1900);
    }
    
    /**
     * Create an instance with the given multicast address and port.
     * 
     * @param address the (multicast) address
     * @param port the port
     */
    public UPnP(String address,int port) {
        this(resolveHost(address),port);
    }
    
    /**
     * Create an instance with the given multicast address and port.
     * 
     * @param address the (multicast) address
     * @param port the port
     */
    public UPnP(InetAddress address,int port) {
        this.multicastAddress=address;
        this.port=port;
        sayByeByeOnClose=true;
    }

    /**
     * Helper method to schedule the given command. This method delegates to an underlying executor.
     * 
     * @param command the command to execute
     * @param delay the time offset
     * @param unit the time unit
     * @return a scheduled future representing the task.
     */
    public ScheduledFuture<?> schedule(Runnable command,long delay,TimeUnit unit) {
        return executor.schedule(command, delay, unit);
    }
    
    protected byte[] resolveContent(String page) {
        for(UPnPMessage pub:published) try {
            if(page.equals(pub.getLocation().getName())) {
                return pub.getLocation().getContent();
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
        return new byte[0];
    }
    
    String getHost() {
        return multicastAddress.getHostAddress()+":"+port;
    }
    
    String getServerName() {
        return "java/1.8 UPnP/1.0 jlibupnp/1.0";
    }
    
    /**
     * Configuration method: Configure the bye bye behaviour.
     * 
     * @param sayByeByeOnClose should we say bye bye on close?
     * @return this instance for further configuration or start
     */
    public UPnP sayByeByeOnClose(boolean sayByeByeOnClose) {
        this.sayByeByeOnClose=sayByeByeOnClose;
        return this;
    }
    
    /**
     * 
     * @return <code>true</code> if the server says bye bye on close
     */
    public boolean doSayByeByeOnClose() {
        return sayByeByeOnClose;
    }
    
    /**
     * Configuration method: Set the MX value (controlling the time interval a server should respond for a given search method)
     * 
     * @param mx the interval in seconds
     * @return this instance for further configuration or start
     */
    public UPnP setMX(int mx) {
        this.mx=mx;
        return this;
    }
    
    /**
     * Configuration method: Set the HTTP port of this instance for delivering device descriptions
     * 
     * @param port the http port
     * @return this instance for futhre configuration or start
     */
    public UPnP setHttpPort(int port) {
        this.httpPort=port;
        return this;
    }
    
    /**
     * 
     * @return the http port of this instance
     */
    public int getHttpPort() {
        return httpPort;
    }
    
    /**
     * 
     * @return the MX parameter of this instance
     */
    int getMX() {
        return mx;
    }
    
    /**
     * Configuration method: Set the Time To Live value of a UPnP Message send by this server.
     * 
     * @param ttl the Time To Live value of this server (in seconds)
     * @return this instance for further configuration or start
     */
    public UPnP setTTL(int ttl) {
        this.ttl=ttl;
        return this;
    }
    
    /**
     * 
     * @return the TTL value of this instance
     */
    int getTTL() {
        return ttl;
    }
    
    /**
     * Basic method to start this server.
     * 
     * @return this instance for stopping
     * @throws IOException if an error occurs
     * @see #close()
     */
    public UPnP start() throws IOException {
        if(sendSockets.length==0) {
            if(httpPort>0) {
                httpServer=NetworkHelper.createHttpServer(getHttpPort(), executor, contentMap);
                httpServer.start();
            }
            interfaceAddresses.clear();
            MulticastSocket socket=NetworkHelper.createMulticastSocket(multicastAddress, port,interfaceAddresses);
            SocketWorker[] sendSocks=new SocketWorker[interfaceAddresses.size()+1];
            sendSocks[0]=new SocketWorker(socket);
            sendSocks[0].start();
            for(int i=1;i<sendSocks.length;i++) {
                sendSocks[i]=new SocketWorker(new DatagramSocket(0,interfaceAddresses.get(i-1).getAddress()));
                sendSocks[i].start();
            }
            sendSockets=sendSocks;
            if(notificationThread==null) {
                executor.scheduleAtFixedRate(() -> {
                    notify(published);
                }, 1000,ttl*333, TimeUnit.MILLISECONDS);
            }
        } else {
            throw new IOException("Server already started");
        }
        return this;
    }
    
    /**
     * Publish all messages. In contrast to the configuration methods, this method can be called after the server was started.
     * 
     * @param msgs the messages to publish
     * @return this instance for further configuration or startup.
     * 
     */
    public UPnP publish(UPnPMessage...msgs) {
        for(UPnPMessage msg:msgs) {
            if(msg!=null&&msg.isPublishable()) {
                publishInternal(msg);
            }
        }
        return this;
    }
    
    protected void publishInternal(UPnPMessage msg) {
        int n=published.size();
        boolean replaced=false;
        for(int i=0;i<n;i++) {
            if(published.get(i).matches(msg)) {
                published.set(i,msg);
                replaced=true;
                break;
            }
        }
        if(!replaced) {
            published.add(msg);
        }
        notify(Collections.singletonList(msg));
    }
    
    /**
     * Withdraw the messages in the list. This message can be called after the server was started.
     * 
     * @param msgs the messages to withdraw
     */
    public void withdraw(UPnPMessage...msgs) {
        for(UPnPMessage msg:msgs) {
            if(msg!=null) {
                removeInternal(msg);
            }
        }
    }
    
    protected void removeInternal(UPnPMessage msg) {
        for(Iterator<UPnPMessage> itr=published.iterator();itr.hasNext();) {
            UPnPMessage m=itr.next();
            if(m.matches(msg)) {
                itr.remove();
                byebye(m);
            }
        }
    }
    
    protected void byebye(UPnPMessage msg) {
        // Notify a bye bye
        send(multicastAddress,port,msg,SendMsgType.byebye);
    }
    
    protected void notify(List<UPnPMessage> msgs) {
        // Send a notification
        for(UPnPMessage msg:msgs) {
            send(multicastAddress,port,msg,SendMsgType.alive);
        }
    }
    
    protected boolean isAdmissable(InetAddress address,InterfaceInfo sockAddress) {
        if(address.isMulticastAddress()) {
            return true;
        } else {
            return sockAddress.matches(address);
        }
    }
    
    protected void send(InetAddress address,int port,UPnPMessage msg,SendMsgType type) {
        if(sendSockets.length!=0) try {
            for(int i=0;i<interfaceAddresses.size();i++) {
                if(isAdmissable(address,interfaceAddresses.get(i))) try {
                    String s=null;
                    switch(type) {
                    case byebye:s=msg.getByeByeMessage(this); break;
                    case alive:s=msg.getAliveMessage(this, interfaceAddresses.get(i)); break;
                    case search:s=msg.getSearchMessage(this); break;
                    case reply:s=msg.getResponseMessage(this,interfaceAddresses.get(i)); break;
                    }
                    s+="\r\n";
                    byte[] bytes=s.getBytes();
                    DatagramPacket packet=new DatagramPacket(bytes,bytes.length,address,port);
                    sendSockets[i+1].send(packet);
                } catch(Throwable t) {
                    System.out.println("Failed to send from "+sendSockets[i+1].getLocalSocketAddress()+" to "+address+":"+port+": "+t.getClass().getSimpleName()+" ("+t.getMessage()+")");
                } else {
                    //System.out.println("OMIT "+sendSockets[i+1].getLocalAddress()+"->"+address+":"+port);
                }
            }
        } catch(Throwable t) {
            t.printStackTrace();
        }
    }
    
    /**
     * Start scanning. 
     * @param matcher the search message. The scanner checks incoming messages against this search message and ignores any message not matching
     * @param callback the callback invoked on matching messages.
     * @return a scanner for the given parameters.
     */
    public UPnPScanner startScan(UPnPMessage matcher,UPnPCallback callback) {
        Scanner scanner=new Scanner(matcher, callback);
        scanners.add(scanner);
        return scanner;
    }
    
    protected void remove(Scanner scanner) {
        scanners.remove(scanner);
    }
    
    /**
     * Shutdown this running instance.
     * 
     * @see #start()
     */
    public void close() {
        if(httpServer!=null) try {
            httpServer.close();
        } catch(IOException e) {
            
        }
        if(notificationThread!=null) try {
            notificationThread.cancel(false);
        } catch(Throwable t) {
        } finally {
            notificationThread=null;
        }
        if(httpServer!=null) try {
            httpServer.close();
        } catch(IOException e) {
        }
        executor.shutdownNow();
        if(sayByeByeOnClose) try {
            for(UPnPMessage msg:published) {
                byebye(msg);
            }
            Thread.sleep(100);
        } catch(Throwable t) {
        }
        SocketWorker[] sendSocks=sendSockets;
        sendSockets=new SocketWorker[0];
        for(SocketWorker sock:sendSocks) {
            sock.close();
        }
    }
    
    protected List<String> split(String s) {
        List<String> lines=new ArrayList<String>(10);
        int lf=-1;
        int o=0;
        while((lf=s.indexOf('\n',o))>0) {
            lines.add(s.substring(o,lf>o&&s.charAt(lf-1)=='\r'?lf-1:lf));
            o=lf+1;
        }
        return lines;
    }
    
    private void handlePacket(InetAddress address,int port, byte[] data, int offset, int length) {
        String s=new String(data,offset,length);
        if(s.startsWith("M-SEARCH")) {
            if(published.size()>0) {
                UPnPMessage searchMsg=UPnPMessage.fromHttp(split(s));
                if(searchMsg!=null) for(UPnPMessage msg:published) {
                    if(msg.matches(searchMsg)) {
                        int wait=Math.max(100,Math.min(4500,searchMsg.getTTL()*1000-500));
                        executor.schedule(()->{
                            send(address,port,msg,SendMsgType.reply);
                        },RANDOM.nextInt(wait),TimeUnit.MILLISECONDS);
                    }
                }
            }
        } else if(s.startsWith("NOTIFY")) {
            if(scanners.size()>0) {
                List<String> lines=split(s);
                UPnPMessage msg=UPnPMessage.fromHttp(lines);
                if(msg!=null) for(Scanner scanner:scanners) {
                    if(msg.matches(scanner.matcher)) {
                        if(msg.descriptionURL==null) {
                            scanner.callback.onMessageByeBye(scanner,address, msg);
                        } else {
                            scanner.callback.onMessageReceived(scanner,address,false,scanner.currentSearchId, msg);
                        }
                    }
                }
            }
        } else if(s.startsWith("HTTP/1.1")) {
            if(scanners.size()>0) {
                List<String> lines=split(s);
                UPnPMessage msg=UPnPMessage.fromHttp(lines);
                if(msg!=null) for(Scanner scanner:scanners) {
                    if(msg.matches(scanner.matcher)) {
                        scanner.callback.onMessageReceived(scanner,address,true,scanner.currentSearchId, msg);
                    }
                }
            }
        }
    }

    protected class SocketWorker extends Thread implements AutoCloseable {
        boolean closed;
        DatagramSocket socket;
        SocketWorker(DatagramSocket socket) {
            super("UPnP listener at "+multicastAddress.getHostAddress());
            setDaemon(true);
            this.socket=socket;
        }
        
        public InetAddress getLocalSocketAddress() {
            return socket.getLocalAddress();
        }

        public void send(DatagramPacket packet) throws IOException {
            socket.send(packet);
        }

        public void run() {
           byte[] buf=new byte[2048];
           DatagramPacket receivePacket=new DatagramPacket(buf,buf.length);
           while(socket!=null&&!closed) try {
               socket.receive(receivePacket);
               handlePacket(receivePacket.getAddress(),receivePacket.getPort(), receivePacket.getData(),receivePacket.getOffset(), receivePacket.getLength());
           }  catch(Exception e) {
               if(!closed) {
                   e.printStackTrace();
               }
           }
        }
        
        public void close() {
            closed=true;
            socket.close();
        }
    }
    
    class Scanner implements UPnPScanner {
        UPnPMessage matcher;
        int currentSearchId;
        UPnPCallback callback;
        
        protected Scanner(UPnPMessage matcher,UPnPCallback callback) {
            this.matcher=matcher;
            this.callback=callback;
            currentSearchId=-1;
        }
        
        @Override
        public void close() throws IOException {
            remove(this);
        }

        @Override
        public boolean search(int searchId) {
            if(currentSearchId<0) {
                send(multicastAddress,port,matcher,SendMsgType.search);
                currentSearchId=searchId;
                executor.schedule(this::reset,getMX(),TimeUnit.SECONDS);
            }
            return currentSearchId==searchId;
        }
        
        public void reset() {
            int id=currentSearchId;
            currentSearchId=-1;
            callback.onSearchTimedOut(this,id);
        }

        @Override
        public UPnP getServer() {
            return UPnP.this;
        }

        @Override
        public UPnPMessage getSearchMessage() {
            return matcher;
        }
    }
    
    private enum SendMsgType {
        alive,search,byebye,reply;
    }

    /**
     * Create an URL for the given interface such that the http server can deliver the description associated with the name.
     * 
     * @param info the interface info
     * @param name the name of the device (or the description)
     * @return an URL representing the description
     */
    public String resolveLocalURL(InterfaceInfo info, String name) {
        if(getHttpPort()>0) {
            return "http:/"+info.getAddress()+":"+getHttpPort()+"/"+name;
        } else {
            throw new IllegalArgumentException("Cannot obtain location for "+name+" (http not configured)");
        }
    }
    
    /**
     * Reset the descriptor content of published messages
     */
    public void reset() {
    	contentMap.clear();
    }
}
