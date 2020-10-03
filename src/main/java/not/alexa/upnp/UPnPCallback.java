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

import java.net.InetAddress;

/**
 * Callback for an {@link UPnPScanner}. Methods of this callback are invoked on receiving messages or search request timeouts.
 * 
 * @author notalexa
 *
 */
public interface UPnPCallback {
    
    /**
     * Called whenever a message satisfying the search criterion is received.
     * 
     * @param scanner the scanner for this message
     * @param from the ip address of the sender
     * @param reply
     * @param searchId the currently active search id of this scanner or <code>-1</code> if
     * no pending requests are present.
     * @param msg the received message
     */
    public default void onMessageReceived(UPnPScanner scanner,InetAddress from,boolean reply,int searchId,UPnPMessage msg) {
    }
    
    /**
     * Called whenever a sender matching the search message of this scanner says byebye.
     * 
     * @param scanner the scanner for this message
     * @param from the ip address of the sender
     * @param msg the received message
     */
    public default void onMessageByeBye(UPnPScanner scanner,InetAddress from,UPnPMessage msg) {
    }
    
    /**
     * Called whenever the search request timed out.
     * <br>Note that this method is always called no matter if messages were received. A call
     * to this method indicates that all UPnP devices or services matching the search message should have answered the request by now.
     * 
     * @param scanner the scanner
     * @param searchId the id of the search request which timed out
     */
    public default void onSearchTimedOut(UPnPScanner scanner,int searchId) {
    }
}
