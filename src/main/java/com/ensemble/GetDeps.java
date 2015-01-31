package com.ensemble;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class GetDeps {
    private static final List<Dependency> deps = new ArrayList<Dependency>();

    /*
     * Get all bundle.jar files.
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Please specify the felix directory. Usually this is crx-quickstart/launchpad/felix.");
        }
        File rootFolder = new File(args[0]);
        if (rootFolder.exists() && rootFolder.isDirectory() && rootFolder.canRead()) {
            Collection<File> bundles = FileUtils.listFiles(rootFolder, FileFilterUtils.nameFileFilter("bundle.jar"), FileFilterUtils.directoryFileFilter());
            for (File bundle : bundles) {
                processBundle(bundle);
            }
            Collections.sort(deps);
            System.out.println("**********************************************************************");
            for (Dependency dep : deps) {
                System.out.println(dep);
            }
        } else {
            throw new IllegalArgumentException("The provided argument either does not exist or is not a folder or cannot be accessed.");
        }
    }

    /*
     * Extract the pom.xml from each bundle
     */
    private static void processBundle(File bundle) throws Exception {
        ZipFile zip = new ZipFile(bundle);
        Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.getName().startsWith("META-INF/maven") && entry.getName().endsWith("pom.xml")) {
                InputStream pom = zip.getInputStream(entry);
                parsePom(pom, bundle);
            }
        }
    }

    /*
     * Parse the pom.xml for groupId, artifactId, version
     */
    private static void parsePom(InputStream stream, File bundle) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(stream);
        Element root = doc.getDocumentElement();
        NodeList nodes = root.getChildNodes();
        Node parent = null;
        Node groupId = null;
        Node artifactId = null;
        Node version = null;
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeName().equals("groupId")) {
                groupId = node;
            } else if (node.getNodeName().equals("artifactId")) {
                artifactId = node;
            } else if (node.getNodeName().equals("version")) {
                version = node;
            } else if (node.getNodeName().equals("parent")) {
                parent = node;
            }
        }
        if ((groupId == null || version == null) && parent != null) {
            nodes = parent.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeName().equals("groupId") && groupId == null) {
                    groupId = node;
                } else if (node.getNodeName().equals("version") && version == null) {
                    version = node;
                }
            }
        }
        deps.add(new Dependency(groupId, artifactId, version, bundle.getAbsolutePath()));
    }

}

class Dependency implements Comparable<Dependency> {
    private final Node group;
    private final Node artifact;
    private final Node version;
    private final String bundlePath;

    public Dependency(Node _group, Node _artifact, Node _version, String _bundlePath) {
        group = _group;
        artifact = _artifact;
        version = _version;
        bundlePath = _bundlePath;
    }

    public Node getGroup() {
        return group;
    }

    public Node getArtifact() {
        return artifact;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("<dependency>\n");
        sb.append("    <groupId>").append(group.getTextContent()).append("</groupId>\n");
        sb.append("    <artifactId>").append(artifact.getTextContent()).append("</artifactId>");
        if (artifact.getTextContent().contains("${")) {
            sb.append("     ************************************************************** ").append(bundlePath);
        }
        sb.append("\n");
        sb.append("    <version>").append(version.getTextContent()).append("</version>");
        if (version.getTextContent().contains("${")) {
            sb.append("     ************************************************************** ").append(bundlePath);
        }
        sb.append("\n");
        sb.append("    <scope>provided</scope>\n");
        return sb.append("</dependency>\n").toString();
    }

    public int compareTo(Dependency dep) {
        int result = group.getTextContent().compareTo(dep.getGroup().getTextContent());
        if (result == 0) {
            return artifact.getTextContent().compareTo(dep.getArtifact().getTextContent());
        } else {
            return result;
        }
    }
}