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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Helper for common network operations.
 * 
 * @author notalexa
 *
 */
public class NetworkHelper {
    // Do not allow to instantiate this class
    private NetworkHelper() {}
    
    private static final byte[] MASK= {0x0,(byte)0x80,(byte)0xc0,(byte)0xe0,(byte)0xf0,(byte)0xf8,(byte)0xfc,(byte)0xfe,(byte)0xff};

    /**
     * Create a multicast socket for the given address and port. As a side effect, fill the <code>interfaceAddresses</code>
     * array with information about all bounded interfaces.
     * <p><b>Currently, the method supports IPv4 addresses only.</b>
     * 
     * @param group the address of the multicast
     * @param port the requested port
     * @param interfaceAddresses a list which can be filled with information about the bounded network interfaces.<br>
     *   If <code>null</code>, no information will be filled in.
     * @return a multicast socket ready for use
     * @throws SocketException if an socket exception occurs
     * @throws IOException if an error occurs
     */
    public static MulticastSocket createMulticastSocket(InetAddress group,int port,List<InterfaceInfo> interfaceAddresses) throws SocketException, IOException{
        MulticastSocket socket=new MulticastSocket(port);
        SocketAddress sa=new InetSocketAddress(group,port);
        for(Enumeration<NetworkInterface> ifaces=NetworkInterface.getNetworkInterfaces();ifaces.hasMoreElements();) try {
            NetworkInterface iface=ifaces.nextElement();
            if(!iface.isLoopback()&&iface.supportsMulticast()&&iface.getInetAddresses().hasMoreElements()) {
                socket.joinGroup(sa,iface);
                if(interfaceAddresses!=null) for(InterfaceAddress address:iface.getInterfaceAddresses()) {
                    InetAddress addr=address.getAddress();
                    if(addr.getAddress().length==4) {
                        InterfaceInfo info=new InterfaceInfo(addr,address.getNetworkPrefixLength());
                        interfaceAddresses.add(info);
                    }
                }
            } 
        } catch(Exception e2) {
        }
        return socket;
    }

    /**
     * Class representing a network interface. The user can request the IP address of this interface (IPv4)
     * and ask if a given address belongs to the same local subnet ({@link #matches(InetAddress)}.
     * 
     * @author notalexa
     *
     */
    public static class InterfaceInfo {
        private InetAddress address;
        private int prefixLength;
        byte[] addressBytes;
        public InterfaceInfo(InetAddress address,int prefixLength) {
            this.address=address;
            this.addressBytes=address.getAddress();
            this.prefixLength=prefixLength;
        }
       
        /**
         * @return the address of the network interface
         */
        public InetAddress getAddress() {
            return address;
        }
        
        /**
         * 
         * @param address the address to check
         * @return <code>true</code> matches this network interface (that is belongs to the same local network).
         */
        public boolean matches(InetAddress address) {
            byte[] other=address.getAddress();
            if(other.length!=addressBytes.length) {
                return false;
            }
            int firstNonMatch=0;
            for(;firstNonMatch<addressBytes.length&&other[firstNonMatch]==addressBytes[firstNonMatch];firstNonMatch++);
            if(firstNonMatch==addressBytes.length) {
                // IP are the same
                return true;
            } else if(8*firstNonMatch+8<prefixLength) {
                return false;
            } else if(8*firstNonMatch>=prefixLength) {
                return true;
            } else {
                // 8*firstNonMatch>length>8*firstNonMatch+8
                int mask=MASK[prefixLength-8*firstNonMatch];
                return (mask&other[firstNonMatch])==(mask&addressBytes[firstNonMatch]);
            }
        }
    }
    
    /**
     * Create a simple HTTP server. This method returns the {@link HttpMiniServer}.
     * 
     * @param port the port the server should listen on.
     * @param workers a workers pool to handle requests.
     * @param contentMap the content this server can deliver
     * @return a (http) server
     * @see HttpMiniServer
     */
    public static Server createHttpServer(int port,ExecutorService workers,Map<String,byte[]> contentMap) {
        return new HttpMiniServer(port, contentMap, workers);
    }
    
    /**
     * Convenience method to copy all bytes from an input to an output stream. The output
     * stream is returned (handy for <code>ByteArrayOutputStream.getByteArray()</code> for
     * example to obtain a byte array with all bytes of the input stream.
     * 
     * @param <O> the type of the output stream
     * @param in the input stream
     * @param out the output stream
     * @return the output stream
     * @throws IOException if an error occurs
     */
    public static <O extends OutputStream> O copy(InputStream in, O out) throws IOException {
        int n=0;
        byte[] buffer=new byte[8192];
        while((n=in.read(buffer))>=0 ) {
            out.write(buffer,0,n);
        }
        return out;
    }
}
