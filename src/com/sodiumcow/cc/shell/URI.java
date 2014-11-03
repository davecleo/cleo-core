package com.sodiumcow.cc.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sodiumcow.util.S;

public class URI {
    public String             id           = null;
    public String             file         = null;
    public String             inputStream  = null;
    public String             outputStream = null;
    public String[]           classPath    = null;
    public Map<String,String> properties   = new HashMap<String,String>();

    private static final Pattern URI_PROPERTY = Pattern.compile("cleo\\.uri\\.(\\w+)\\.file");
    private static final String  COLON        = String.valueOf(File.pathSeparatorChar);

    public static Properties removeScheme(Properties props, URI scheme) {
        return removeScheme(props, scheme.id);
    }
    public static Properties removeScheme(Properties props, String id) {
        String prefix = "cleo.uri."+id+".";
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                props.remove(key);
            }
        }
        return props;
    }

    public static Properties setScheme(Properties props, URI scheme) {
        removeScheme(props, scheme);
        props.setProperty("cleo.uri."+scheme.id+".file",         scheme.file);
        props.setProperty("cleo.uri."+scheme.id+".inputstream",  scheme.inputStream);
        props.setProperty("cleo.uri."+scheme.id+".outputstream", scheme.outputStream);
        props.setProperty("cleo.uri."+scheme.id+".classpath",    S.join(COLON, scheme.classPath));
        for (Map.Entry<String,String> e : scheme.properties.entrySet()) {
            props.setProperty("cleo.uri."+scheme.id+"."+e.getKey(), e.getValue());
        }
        return props;
    }

    public static URI getScheme(Properties props, String id) {
        String             file         = null;
        String             inputstream  = null;
        String             outputstream = null;
        String             classpath    = null;
        Map<String,String> properties   = new HashMap<String,String>();

        String prefix = "cleo.uri."+id+".";
        if (props.containsKey(prefix+"file")) {
            for (String key : props.stringPropertyNames()) {
                if (key.startsWith(prefix)) {
                    String prop = key.substring(prefix.length());
                    if (prop.equals("file")) {
                        file = props.getProperty(key);
                    } else if (prop.equals("inputstream")) {
                        inputstream = props.getProperty(key);
                    } else if (prop.equals("outputstream")) {
                        outputstream = props.getProperty(key);
                    } else if (prop.equals("classpath")) {
                        classpath = props.getProperty(key);
                    } else {
                        properties.put(prop, props.getProperty(key));
                    }
                }
            }
            return new URI(id, file, inputstream, outputstream, classpath, properties);
        } else {
            return null;
        }
    }

    public static String[] getSchemeIds(Properties props) {
        List<String> ids = new ArrayList<String>();
        for (String key : props.stringPropertyNames()) {
            Matcher m = URI_PROPERTY.matcher(key);
            if (m.matches()) {
                ids.add(m.group(1));
            }
        }
        return ids.toArray(new String[ids.size()]);
    }

    public static URI[] getSchemes(Properties props) {
        String[] ids  = getSchemeIds(props);
        URI[]    uris = new URI[ids.length];
        for (int i=0; i<ids.length; i++) {
            uris[i] = getScheme(props, ids[i]);
        }
        return uris;
    }

    public static Properties load(File home) throws IOException {
        Properties props = new Properties();
        FileInputStream in = null;
        try {
            in = new FileInputStream(new File(home, "conf/system.properties"));
            props.load(in);
        } catch (FileNotFoundException e) {
            // that's ok -- otherwise let it bubble up
        } finally {
            if (in!=null) in.close();
        }
        return props;
    }

    public static void store(File home, Properties props) throws Exception {
        FileOutputStream out = new FileOutputStream(new File(home, "conf/system.properties"));
        props.store(out, "Properties updated by "+URI.class.getCanonicalName());
        out.close();
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String schemeClassPath, Map<String,String> properties) {
        if (properties==null) {
            properties = new HashMap<String,String>();
        }
        this.id           = scheme;
        this.file         = schemeFile;
        this.inputStream  = schemeInputStream;
        this.outputStream = schemeOutputStream;
        this.classPath    = schemeClassPath==null ? null : schemeClassPath.split(COLON);
        this.properties   = properties;
    }

    private static String relativize (File home, File jar) {
        if (home==null) {
            try {
                return jar.getCanonicalPath();
            } catch (Exception e) {
                return jar.getAbsolutePath();
            }
        } else {
            return home.toURI().relativize(jar.toURI()).getPath();
        }
    }

    private File homeFile(File home, String name) {
        File f = new File(name);
        if (home!=null && !f.isAbsolute()) {
            return new File(home, name);
        }
        return f;
    }

    private synchronized void inspectJars (File home, final String...jars) throws Exception {
        List<String> schemeFiles  = new ArrayList<String>();
        for (String file : jars) {
            // check if it's a property, not a file
            if (file.contains("=")) {
                String[] kv = file.split("=", 2);
                this.properties.put(kv[0], kv[1]);
                continue;
            }
            // ok, it's a file
            File jarfile = homeFile(home, file);
            if (jarfile.canRead()) {
                schemeFiles.add(relativize(home, jarfile));
            } else {
                throw new Exception("can not read file: "+jarfile);
            }
        }
        if (!schemeFiles.isEmpty()) {
            final ClassLoader    classLoader = Util.class.getClassLoader();
            final java.net.URL[] urls = new java.net.URL[schemeFiles.size()];
            for (int i=0; i<schemeFiles.size(); i++) {
                urls[i] = new java.net.URL("jar:file:"+homeFile(home, schemeFiles.get(i)).getCanonicalPath()+"!/");
            }
            final URLClassLoader ucl         = new URLClassLoader(urls, classLoader);
            for (String f : schemeFiles) {
                final File           jarfile = homeFile(home, f);
                final JarInputStream jar     = new JarInputStream(new FileInputStream(jarfile));
                JarEntry entry;
                while ((entry = jar.getNextJarEntry()) != null) {
                    if (entry.getName().endsWith(".class")) {
                        String classname = entry.getName().replaceAll("/", "\\.");
                        classname = classname.substring(0, classname.length()-".class".length());
                        if (!classname.contains("$")) {
                            try {
                                final Class<?> test = Class.forName(classname, true, ucl);
                                if (com.cleo.lexicom.beans.LexURIFile.class.isAssignableFrom(test)) {
                                    this.file = classname;
                                    this.id = classname.toLowerCase();
                                    if (this.id.endsWith("file")) {
                                        this.id = this.id.substring(this.id.lastIndexOf(".")+1, this.id.length()-"file".length());
                                    }
                                }
                                try {
                                    test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class);
                                    if (java.io.InputStream.class.isAssignableFrom(test)) {
                                        this.inputStream = classname;
                                    }
                                } catch (Exception e) {}
                                try {
                                    test.getConstructor(com.cleo.lexicom.beans.LexURIFile.class, boolean.class);
                                    if (java.io.OutputStream.class.isAssignableFrom(test)) {
                                        this.outputStream = classname;
                                    }
                                } catch (Exception e) {}
                            } catch (final ClassNotFoundException e) {
                                // so what
                            } catch (final NoClassDefFoundError e) {
                                // so what
                            } 
                        }
                    }
                }
                jar.close();
            }
        }
        if (this.file==null || this.inputStream==null || this.outputStream==null) {
            throw new Exception("classes missing from classpath: invalid URI");
        }
        this.classPath = schemeFiles.toArray(new String[schemeFiles.size()]);
    }

    public URI (String scheme, String schemeFile, String schemeInputStream, String schemeOutputStream, String[] schemeClassPath, Map<String,String> properties) {
        this(scheme, schemeFile, schemeInputStream, schemeOutputStream, S.join(COLON, schemeClassPath), properties);
    }

    public URI(String...jars) throws Exception {
        this(null, jars);
    }

    public URI(File home, final String...jars) throws Exception {
        inspectJars(home, jars);
    }

    public String[] toStrings() {
        List<String> list = new ArrayList<String>();
        list.add("cleo.uri."+this.id+".file="+this.file);
        list.add("cleo.uri."+this.id+".inputstream="+this.inputStream);
        list.add("cleo.uri."+this.id+".outputStream="+this.outputStream);
        list.add("cleo.uri."+this.id+".classpath="+S.join(File.pathSeparator, this.classPath));
        for (Map.Entry<String,String> e : this.properties.entrySet()) {
            list.add("cleo.uri."+this.id+"."+e.getKey()+"="+e.getValue());
        }
        return list.toArray(new String[list.size()]);
    }
    public String toString() {
        return S.join("\n", this.toStrings());
    }
}