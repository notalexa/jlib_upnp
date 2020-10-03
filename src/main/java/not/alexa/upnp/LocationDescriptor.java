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

import java.io.IOException;

import not.alexa.upnp.location.URLDescriptor;
import not.alexa.upnp.net.NetworkHelper.InterfaceInfo;

/**
 * The interface describes the location url and content of the <code>LOCATION</code> field in an UPnP message. For a client, the information are typically taken
 * from this field and the content is resolved via a http call (via {@link URLDescriptor} (with no name). For a server, the URL is in general constructed with
 * the help of {@link #getName()} and the content is provided from various locations (like filesystem, classpath, ...)
 * 
 * @author notalexa
 *
 */
public interface LocationDescriptor {
    /**
     * 
     * @return the name of this descriptor (including an extension in general).
     */
    public String getName();
    
    /**
     * Resolve the URL of this location. The default implementation delegates to {@link UPnP#resolveLocalURL(InterfaceInfo, String)} with {@link #getName()} as
     * second argument.
     * 
     * @param upnp the UPnP instance
     * @param info the interface info the location should be generated for
     * @return a string representing the localion
     */
    public default String getLocation(UPnP upnp,InterfaceInfo info) {
        return upnp.resolveLocalURL(info,getName());
    }
    
    /**
     * 
     * @return the content of the descriptor of the UPnP service.
     * 
     * @throws IOException if the content cannot be resolved.
     */
    public byte[] getContent() throws IOException;
}
