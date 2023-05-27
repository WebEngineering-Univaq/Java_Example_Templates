/*
 * Patched version of the Freemarker WebappTemplateLoader accepting
 * JakartaEE ServletContext instances.
 *
 * Original license header follows....
 */

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package freemarker.cache;

import freemarker.log.Logger;
import freemarker.template.utility.CollectionUtils;
import freemarker.template.utility.NullArgumentException;
import freemarker.template.utility.StringUtil;
import jakarta.servlet.ServletContext;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;



public class JavartaWebappTemplateLoader implements TemplateLoader {

    private static final Logger LOG = Logger.getLogger("freemarker.cache");

    private final ServletContext servletContext;
    private final String subdirPath;

    private Boolean urlConnectionUsesCaches;

    private boolean attemptFileAccess = true;

    /**
     * Creates a template loader that will use the specified servlet context to
     * load the resources. It will use the base path of <code>"/"</code> meaning
     * templates will be resolved relative to the servlet context root location.
     *
     * @param servletContext the servlet context whose
     * {@link ServletContext#getResource(String)} will be used to load the
     * templates.
     */
    public JavartaWebappTemplateLoader(ServletContext servletContext) {
        this(servletContext, "/");
    }

    public JavartaWebappTemplateLoader(ServletContext servletContext, String subdirPath) {
        NullArgumentException.check("servletContext", servletContext);
        NullArgumentException.check("subdirPath", subdirPath);

        subdirPath = subdirPath.replace('\\', '/');
        if (!subdirPath.endsWith("/")) {
            subdirPath += "/";
        }
        if (!subdirPath.startsWith("/")) {
            subdirPath = "/" + subdirPath;
        }
        this.subdirPath = subdirPath;
        this.servletContext = servletContext;
    }

    @Override
    public Object findTemplateSource(String name) throws IOException {
        String fullPath = subdirPath + name;

        if (attemptFileAccess) {
            // First try to open as plain file (to bypass servlet container resource caches).
            try {
                String realPath = servletContext.getRealPath(fullPath);
                if (realPath != null) {
                    File file = new File(realPath);
                    if (file.canRead() && file.isFile()) {
                        return file;
                    }
                }
            } catch (SecurityException e) {
                ;// ignore
            }
        }

        // If it fails, try to open it with servletContext.getResource.
        URL url = null;
        try {
            url = servletContext.getResource(fullPath);
        } catch (MalformedURLException e) {
            LOG.warn("Could not retrieve resource " + StringUtil.jQuoteNoXSS(fullPath),
                    e);
            return null;
        }
        return url == null ? null : new URLTemplateSource(url, getURLConnectionUsesCaches());
    }

    @Override
    public long getLastModified(Object templateSource) {
        if (templateSource instanceof File) {
            return ((File) templateSource).lastModified();
        } else {
            return ((URLTemplateSource) templateSource).lastModified();
        }
    }

    @Override
    public Reader getReader(Object templateSource, String encoding)
            throws IOException {
        if (templateSource instanceof File) {
            return new InputStreamReader(
                    new FileInputStream((File) templateSource),
                    encoding);
        } else {
            return new InputStreamReader(
                    ((URLTemplateSource) templateSource).getInputStream(),
                    encoding);
        }
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        if (templateSource instanceof File) {
            // Do nothing.
        } else {
            ((URLTemplateSource) templateSource).close();
        }
    }

    public Boolean getURLConnectionUsesCaches() {
        return urlConnectionUsesCaches;
    }

    public void setURLConnectionUsesCaches(Boolean urlConnectionUsesCaches) {
        this.urlConnectionUsesCaches = urlConnectionUsesCaches;
    }

    @Override
    public String toString() {
        return TemplateLoaderUtils.getClassNameForToString(this)
                + "(subdirPath=" + StringUtil.jQuote(subdirPath)
                + ", servletContext={contextPath=" + StringUtil.jQuote(getContextPath())
                + ", displayName=" + StringUtil.jQuote(servletContext.getServletContextName()) + "})";
    }
  
    private String getContextPath() {
        try {
            Method m = servletContext.getClass().getMethod("getContextPath", CollectionUtils.EMPTY_CLASS_ARRAY);
            return (String) m.invoke(servletContext, CollectionUtils.EMPTY_OBJECT_ARRAY);
        } catch (Throwable e) {
            return "[can't query before Serlvet 2.5]";
        }
    }

    public boolean getAttemptFileAccess() {
        return attemptFileAccess;
    }

    public void setAttemptFileAccess(boolean attemptLoadingFromFile) {
        this.attemptFileAccess = attemptLoadingFromFile;
    }
}