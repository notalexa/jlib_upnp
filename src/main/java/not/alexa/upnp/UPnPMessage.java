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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import not.alexa.upnp.location.URLDescriptor;
import not.alexa.upnp.net.NetworkHelper.InterfaceInfo;

/**
 * Message class in the UPnP library. There are different "types" of messages:
 * <ul>
 * <li>A search message is "incomplete" and asks servers in the network for a specific device
 * <li>A bye bye message indicating a device is not longer available
 * <li>An alive message indicating that a device is running
 * <li>A response message answering a search message
 * </ul>
 * 
 * @author notalexa
 *
 */
public class UPnPMessage {
    public static final UPnPMessage ALL=new UPnPMessage(null,null,null);
    SimpleDateFormat rfc1123dateFormat=new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US);
    {
        rfc1123dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    protected String urn;
    protected String uuid;
    protected LocationDescriptor descriptionURL;
    protected Map<String,String> headers;
    private int ttl;
    
    /**
     * Create a message from a set of (HTTP) header lines.
     * 
     * @param lines the header of an http request
     * @return the message or <code>null</code> if the header is illegal
     */
    static UPnPMessage fromHttp(List<String> lines) {
        String uuid=null;
        String urn=null;
        String descriptionURL=null;
        int ttlMX=-1;
        int ttlCache=-1;
        String st=null;
        for(String line:lines) {
            int p=line.indexOf(':');
            if(p>0) {
                String tag=line.substring(0,p).toLowerCase();
                if(tag.equals("location")) {
                    descriptionURL=line.substring(p+1).trim();
                } else if(tag.contentEquals("usn")) {
                    String s=line.substring(p+1).trim();
                    if(s.startsWith("uuid")) {
                        s=s.substring(5);
                        p=s.indexOf("::");
                        if(p>0) {
                            uuid=s.substring(0,p);
                            urn=s.substring(p+2);
                        } else if(s.length()==36) {
                            uuid=s;
                        }
                    }
                } else if(tag.equals("mx")) try {
                    ttlMX=Integer.parseInt(line.substring(p+1).trim());
                } catch(Throwable t) {
                } else if(tag.equals("cache-control")) try {
                    ttlCache=Integer.parseInt(line.substring(p+1).trim().substring("max-age=".length()));
                } catch(Throwable t) {
                } else if(tag.equals("st")) {
                    st=line.substring(p+1).trim();
                }
            }
        }
        if(uuid!=null&&(descriptionURL!=null||ttlCache<0)) {
            return new UPnPMessage(uuid,urn,descriptionURL==null?null:new URLDescriptor(descriptionURL),ttlMX>0?ttlMX:ttlCache);
        } else if("ssdp:all".equals(st)) {
            return new UPnPMessage(uuid,urn,null,ttlMX);
        } else if(st!=null&&ttlMX>0) {
            return new UPnPMessage(null,st,null,ttlMX);
        } else {
//            System.out.println("urn="+urn+", uuid="+uuid+", location="+descriptionURL+" ("+lines+")");
            return null;
        }
    }
    
    /**
     * Construct a (publishable) message for a specific device.
     * 
     * @param uuid the device id
     * @param urn the device urn
     * @param descriptionURL the descriptor of the device
     */
    public UPnPMessage(String uuid,String urn,LocationDescriptor descriptionURL) {
        this(uuid,urn,descriptionURL,Integer.MAX_VALUE);
    }

    private UPnPMessage(String uuid,String urn,LocationDescriptor descriptionURL,int ttl) {
        this.urn=urn;
        this.uuid=uuid;
        this.descriptionURL=descriptionURL;
        this.ttl=ttl;
    }

    /**
     * 
     * @return the time to live value of this message
     */
    public int getTTL() {
        return ttl;
    }
    
    /**
     * Create a http search message for this message
     * 
     * @param upnp the UPnP instance
     * @return a string representing the HTTP header corresponding to this message
     */
    public String getSearchMessage(UPnP upnp) {
        StringBuilder builder=new StringBuilder();
        builder.append("M-SEARCH * HTTP/1.1\r\n");
        builder.append("HOST: "+upnp.getHost()+"\r\n");
        builder.append("MAN: \"ssdp:discover\"\r\n");
        builder.append("MX: "+upnp.getMX()+"\r\n");
        if(uuid!=null) {
            builder.append("ST: uuid:"+uuid+"\r\n");
        } else if(urn!=null) {
            builder.append("ST: "+urn+"\r\n");
        } else {
            builder.append("ST: ssdp:all\r\n");
        }
        return builder.toString();
    }
    
    /**
     * 
     * @return <code>true</code> if this message is publishable (that is all required fields are set).
     */
    public boolean isPublishable() {
        return urn!=null&&uuid!=null&&descriptionURL!=null;
    }
    
    /**
     * 
     * @return the location of this message
     */
    public LocationDescriptor getLocation() {
        return descriptionURL;
    }
        
    /**
     * 
     * @param upnp the UPnP instance
     * @param info the (network) interface info
     * @return a string representing the http header of this message considered as a "alive" message
     */
    public String getAliveMessage(UPnP upnp,InterfaceInfo info) {
        StringBuilder builder=new StringBuilder();
        builder.append("NOTIFY * HTTP/1.1\r\n");
        builder.append("HOST: "+upnp.getHost()+"\r\n");
        builder.append("SERVER: "+upnp.getServerName()+"\r\n");
        builder.append("CACHE-CONTROL: max-age="+upnp.getTTL()+"\r\n");
        builder.append("LOCATION: "+encode(upnp,info,descriptionURL)+"\r\n");
        builder.append("NT: "+urn+"\r\n");
        builder.append("NTS: ssdp:alive\r\n");
        builder.append("USN: uuid:"+uuid+"::"+urn+"\r\n");
        return builder.toString();
    }

    /**
     * 
     * @param upnp the UPnP instance
     * @param info the (network) interface info
     * @return a string representing the http header of this message considered as a "response" message
     */
    public String getResponseMessage(UPnP upnp,InterfaceInfo info) {
        StringBuilder builder=new StringBuilder();
        builder.append("HTTP/1.1 * OK\r\n");
        builder.append("EXT:\r\n");
        builder.append("SERVER: "+upnp.getServerName()+"\r\n");
        builder.append("CACHE-CONTROL: max-age="+upnp.getTTL()+"\r\n");
        builder.append("DATE: "+getDate(System.currentTimeMillis())+"\r\n");
        builder.append("LOCATION: "+encode(upnp,info,descriptionURL)+"\r\n");
        builder.append("NT: "+urn+"\r\n");
        builder.append("NTS: ssdp:alive\r\n");
        builder.append("USN: uuid:"+uuid+"::"+urn+"\r\n");
        return builder.toString();
    }

    /**
     * 
     * @param upnp the UPnP instance
     * @param info the (network) interface info
     * @return a string representing the http header of this message considered as a "byebye" message
     */
    public String getByeByeMessage(UPnP upnp) {
        StringBuilder builder=new StringBuilder();
        builder.append("NOTIFY * HTTP/1.1\r\n");
        builder.append("HOST: "+upnp.getHost()+"\r\n");
        builder.append("NT: "+urn+"\r\n");
        builder.append("NTS: ssdp:byebye\r\n");
        builder.append("USN: uuid:"+uuid+"::"+urn+"\r\n");
        return builder.toString();
    }
    
    protected String getDate(long ms) {
        return rfc1123dateFormat.format(new Date(ms));
    }
    
    protected String encode(UPnP upnp,InterfaceInfo info,LocationDescriptor url) {
        return url.getLocation(upnp, info);
    }
    
    /**
     * if this is a search message, the method test if the given message matches this message.
     * 
     * @param msg the message to test
     * @return <code>true</code> if the message matches, <code>false</code> otherwise
     */
    public boolean matches(UPnPMessage msg) {
        if(msg.uuid!=null) {
            if(uuid==null||!uuid.equals(msg.uuid)) {
                return false;
            }
        }
        if(msg.urn!=null) {
            if(urn==null||!urn.equals(msg.urn)) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        return "UPnP[urn="+urn+", uuid="+uuid+", location="+descriptionURL+"]";
    }
}
