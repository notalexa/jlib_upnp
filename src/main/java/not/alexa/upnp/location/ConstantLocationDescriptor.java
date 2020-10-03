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

import java.io.IOException;

import not.alexa.upnp.LocationDescriptor;

/**
 * A constant location descriptor. The content is passed in the constructor.
 * 
 * @author notalexa
 *
 */
public class ConstantLocationDescriptor implements LocationDescriptor {
    protected String name;
    protected byte[] content;
   
    /**
     * 
     * @param name the name of this location
     * @param content the content of the descriptor
     */
    public ConstantLocationDescriptor(String name,String content) {
        this.name=name;
        this.content=content.getBytes();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] getContent() throws IOException {
        return content;
    }
}
