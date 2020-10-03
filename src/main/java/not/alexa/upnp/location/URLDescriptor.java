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
package not.alexa.upnp.location;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import not.alexa.upnp.LocationDescriptor;
import not.alexa.upnp.UPnP;
import not.alexa.upnp.UPnPMessage;
import not.alexa.upnp.net.NetworkHelper;
import not.alexa.upnp.net.NetworkHelper.InterfaceInfo;

/**
 * A descriptor resolving the content via an URL. This class is used whenever a client receives a {@link UPnPMessage} with a location field.
 * <br>Note that this class resolves the content always via using the explicitly given URL. If a name is given, the <b>location</b> of this descriptor is resolved 
 * using the normal mechanism defined in {@link UPnP}. Therefore, if used in a server context, using a file URL and using the file name as an explicit name results
 * in the generation of an http URL for the location and resolving the content via the file URL. Omitting the explicit name would result in a file URL for the location
 * which is not intended in general.
 *  
 * @author notalexa
 *
 */
public class URLDescriptor implements LocationDescriptor {
    protected String name;
    protected String url;
    public URLDescriptor(String name,String url) {
        this(url);
        this.name=name;
    }
    
    public URLDescriptor(String url) {
        this.url=url;
    }
    
    public String getName() {
        String name=this.name;
        if(name==null) {
            name=url.substring(url.lastIndexOf('/')+1);
        }
        return name;
    }

    /**
     * 
     */
    @Override
    public String getLocation(UPnP upnp, InterfaceInfo info) {
        return name==null?url:upnp.resolveLocalURL(info,name);
    }

    /**
     * Resolve the content using the URL. The content is not cached.
     * 
     */
    @Override
    public byte[] getContent() throws IOException {
        try(InputStream stream=new URL(url).openStream()) {
            return NetworkHelper.copy(stream,new ByteArrayOutputStream()).toByteArray();
        }
    }
    
    public String toString() {
        return url;
    }
}
