/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.module.launcher.domain;

import org.mule.module.classloader.FilteringModuleClassLoader;
import org.mule.module.classloader.ModuleClassLoaderFilter;
import org.mule.module.descriptor.LoaderExport;
import org.mule.module.descriptor.LoaderExportParser;
import org.mule.module.descriptor.ModuleDescriptor;

import java.util.LinkedList;
import java.util.List;

public class MuleClassLoaderFactory
{

    public static ClassLoader createMuleClassLoader()
    {
        //TODO(pablo.kraan): CCL - Move all this code to a different class
        final ClassLoader muleClassLoader = MuleClassLoaderFactory.class.getClassLoader();
        //TODO(pablo.kraan): CCL - need a descriptor for Mule
        final ModuleDescriptor muleModuleDescriptor = new ModuleDescriptor("MuleCore");
        final LoaderExport loaderExport = createMuleLoaderExport();
        muleModuleDescriptor.setLoaderExport(loaderExport);
        ModuleClassLoaderFilter filter = new ModuleClassLoaderFilter(muleModuleDescriptor);
        FilteringModuleClassLoader filteredMuleClassLoader = new FilteringModuleClassLoader("MuleCore", muleClassLoader, filter);

        return filteredMuleClassLoader;
    }

    public static LoaderExport createMuleLoaderExport()
    {
        //TODO(pablo.kraan): CCL - loader export should ignore empty strings
        //TODO(pablo.kraan): CCL - loader export should differentiate between classes, packages and folders
        //TODO(pablo.kraan): CCL - filter definition must be extracted into a separated file
        List<String> loaderExports = new LinkedList<>();
        loaderExports.add("java.");
        loaderExports.add("javax.");
        loaderExports.add("org.slf4j.");
        loaderExports.add("org.apache.logging.log4j.");
        loaderExports.add("META-INF/");
        loaderExports.add("org.springframework.");
        loaderExports.add("org/springframework/");
        loaderExports.add("org.mule.");
        loaderExports.add("com.mulesoft.");

        //TODO(pablo.kraan): CCL - seems like some classes are not required when running from tests. Apparently is because bootstrap properties included on al lthe other dependencies not included on launcher moduel
        loaderExports.add("org.apache.xerces.");
        loaderExports.add("org.apache.commons.");
        loaderExports.add("org.dom4j.");
        loaderExports.add("org.w3c.");
        loaderExports.add("com.");

        //loaderExports.add("org.mule.config.");
        //loaderExports.add("org.mule.module.launcher.artifact.");
        //loaderExports.add("org.mule.extension.");
        //loaderExports.add("org.mule.module.extension.");
        //loaderExports.add("org.mule.api.config.");
        //loaderExports.add("org.mule.module.http.");
        //loaderExports.add("org.mule.expression.");
        //loaderExports.add("org.mule.registry.");
        //TODO(pablo.kraan): CCL - see how test can have different Isolation rules in order to include mule test code and third party libraries used on a test
        //loaderExports.add("org.mule.functional.");
        //loaderExports.add("org.mule.util.");
        //loaderExports.add("org.mule.retry.");
        //loaderExports.add("org.mule.el.mvel.");
        //loaderExports.add("org.mule.time.");
        //loaderExports.add("org.mule.internal.connection.");
        //loaderExports.add("org.mule.security.");
        //loaderExports.add("org.mule.execution.");
        //loaderExports.add("org.mule.endpoint.");
        //TODO(pablo.kraan): CCL - why do we have fucking classes on the root package?
        //TODO(pablo.kraan): CCL - as with OSGi, we would also like to avoid duplicating package names as you can't exported it or blocked it dependngin on the used jar.
        //loaderExports.add("org.mule.DynamicDataTypeConversionResolver");
        //loaderExports.add("org.mule.management.stats.");
        //loaderExports.add("org.mule.connector.");
        //loaderExports.add("org.mule.exception.");
        //loaderExports.add("org.mule.context.notification.");
        //loaderExports.add("org.mule.construct.");
        //TODO(pablo.kraan): CCL - added as the test needs to access test resources defined there
        //loaderExports.add("org.mule.module.launcher.DeploymentServiceTestCase");

        return new LoaderExportParser().parse(loaderExports.toArray(new String[0]));
    }

}