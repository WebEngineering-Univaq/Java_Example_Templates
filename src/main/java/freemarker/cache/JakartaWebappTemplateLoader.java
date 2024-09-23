package freemarker.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;

import freemarker.log.Logger;
import freemarker.template.utility.CollectionUtils;
import freemarker.template.utility.NullArgumentException;
import freemarker.template.utility.StringUtil;
import jakarta.servlet.ServletContext;
//import jakarta.servlet.ServletContext;


/**
 *
 * @author Giuseppe Della Penna
 *
 * patch per l'uso della classe jakarta.servlet.ServletContect con Freemarker
 * 2.3.26
 * 
 * patch to use class jakarta.servlet.ServletContect with Freemarker 2.3.26
 * 
 * Nel caso si usi Freemarker con JakartaEE, sostituire l'import qui sopra
 * e configurare Freemarker per usare questo TemplateLoader invece di quello
 * di default (WebappTemplateLoader)
 * 
 * If you are using Freemarker with JakartaEE, replace the import above 
 * and configure Freemarker to use this TemplateLoader instead of the default 
 * TemplateLoader (WebappTemplateLoader)
 * 
 */
public class JakartaWebappTemplateLoader implements TemplateLoader {

    private static final Logger LOG = Logger.getLogger("freemarker.cache");

    private final ServletContext servletContext;
    private final String subdirPath;

    private Boolean urlConnectionUsesCaches;

    private boolean attemptFileAccess = true;

    public JakartaWebappTemplateLoader(ServletContext servletContext) {
        this(servletContext, "/");
    }

    public JakartaWebappTemplateLoader(ServletContext servletContext, String subdirPath) {
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
