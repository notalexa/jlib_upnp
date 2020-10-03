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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import not.alexa.upnp.LocationDescriptor;
import not.alexa.upnp.net.NetworkHelper;

/**
 * A descriptor using the class loader for resolving the context.
 * <br>This class resolves both a <code>&lt;path&gt;</code> and <code>META-INF/&lt;path&gt</code> entries in that order. On
 * failure, the path is considered to be a path in the filesystem.
 * 
 * @author notalexa
 *
 */
public class ClassLoaderLocationDescriptor implements LocationDescriptor {
    protected String name;
    protected String path;

    /**
     * Use the given <code>name</code> for both the name and path in {@link #ClassLoaderLocationDescriptor(String, String)}.
     * 
     * @param name the (file)name to use for location and content resolution
     */
    public ClassLoaderLocationDescriptor(String name) {
        this(name,name);
    }

    /**
     * 
     * @param name the (file)name for the location
     * @param path the path to use for content resolution (including the filename)
     */
    public ClassLoaderLocationDescriptor(String name,String path) {
        this.name=name;
        this.path=path;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public byte[] getContent() throws IOException {
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream(path)) {
            if(stream!=null) {
                return NetworkHelper.copy(stream,new ByteArrayOutputStream()).toByteArray();
            }
        }
        try(InputStream stream=getClass().getClassLoader().getResourceAsStream("META-INF/"+path)) {
            if(stream!=null) {
                return NetworkHelper.copy(stream,new ByteArrayOutputStream()).toByteArray();
            }
        }
        try(InputStream stream=new FileInputStream(path)) {
            return NetworkHelper.copy(stream,new ByteArrayOutputStream()).toByteArray();
        }
    }
}
