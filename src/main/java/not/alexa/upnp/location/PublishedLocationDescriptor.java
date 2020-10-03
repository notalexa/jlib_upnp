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
 * A universal location descriptor delegating to {@link URLDescriptor}, {@link ConstantLocationDescriptor} or {@link ClassLoaderLocationDescriptor} depending of
 * the content.
 * 
 * @author notalexa
 * 
 * @see URLDescriptor
 * @see ConstantLocationDescriptor
 * @see ClassLoaderLocationDescriptor
 */
public class PublishedLocationDescriptor implements LocationDescriptor {
    protected LocationDescriptor delegate;
    
    public PublishedLocationDescriptor(String name) {
        delegate=new ClassLoaderLocationDescriptor(name);
    }
    
    public PublishedLocationDescriptor(String name,String content) {
        if(content.indexOf("://")>0&&content.indexOf('\n')<0) {
            delegate=new URLDescriptor(name,content);
        } else if(content.startsWith("<?xml")||content.indexOf('\n')>=0) {
            delegate=new ConstantLocationDescriptor(name, content);
        } else {
            delegate=new ClassLoaderLocationDescriptor(name,content);
        }
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public byte[] getContent() throws IOException {
        return delegate.getContent();
    }
}
