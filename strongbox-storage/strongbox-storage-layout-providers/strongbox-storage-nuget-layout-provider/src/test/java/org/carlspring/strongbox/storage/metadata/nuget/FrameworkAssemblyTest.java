/*
 * Copyright 2019 Carlspring Consulting & Development Ltd.
 * Copyright 2014 Dmitry Sviridov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.carlspring.strongbox.storage.metadata.nuget;

import org.carlspring.strongbox.storage.metadata.nuget.AssemblyTargetFrameworkAdapter;
import org.carlspring.strongbox.storage.metadata.nuget.Framework;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests collection of assemblies included in the delivery of Frameworks
 * 
 * @author sviridov
 */
public class FrameworkAssemblyTest
{

    /**
     * Check for string conversion
     *
     * @throws Exception
     *             conversion error
     */
    @Test
    public void testUnmarshalEmptyValue()
        throws Exception
    {
        // GIVEN
        AssemblyTargetFrameworkAdapter adapter = new AssemblyTargetFrameworkAdapter();
        // WHEN
        EnumSet<Framework> result = adapter.unmarshal("");
        // THEN
        assertNull(result);
    }

    /**
     * Check for null value conversion
     *
     * @throws Exception
     *             conversion error
     */
    @Test
    public void testMarshalNullValue()
        throws Exception
    {
        // GIVEN
        AssemblyTargetFrameworkAdapter adapter = new AssemblyTargetFrameworkAdapter();
        // WHEN
        String result = adapter.marshal(null);
        // THEN
        assertNull(result);
    }
}