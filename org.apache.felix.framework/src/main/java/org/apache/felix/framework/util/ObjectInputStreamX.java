/*
 *   Copyright 2006 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.felix.framework.util;

import java.io.*;

import org.apache.felix.moduleloader.IContentLoader;

/**
 * The ObjectInputStreamX class is a simple extension to the ObjectInputStream
 * class.  The purpose of ObjectInputStreamX is to allow objects to be deserialized
 * when their classes are not in the CLASSPATH (e.g., in a JAR file).
 */
public class ObjectInputStreamX extends ObjectInputStream
{
    private IContentLoader m_contentLoader = null;

    /**
     * Construct an ObjectInputStreamX for the specified InputStream and the specified
     * ClassLoader.
     * @param in the input stream to read.
     * @param loader the class loader used to resolve classes.
     */
    public ObjectInputStreamX(InputStream in, IContentLoader contentLoader)
        throws IOException, StreamCorruptedException
    {
        super(in);
        m_contentLoader = contentLoader;
    }

    /**
     * By overloading this method, the ObjectInputStreamX can resolve a class
     * from the class loader that was passed into it at construction time.
     */
    protected Class resolveClass(ObjectStreamClass v)
        throws IOException, ClassNotFoundException
    {
        return m_contentLoader.getClass(v.getName());
    }
}