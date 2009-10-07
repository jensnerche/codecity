package org.codecity.datasource.java;

import java.io.*;
import static java.lang.System.currentTimeMillis;
import java.util.*;

/**
 * @author Michael Hunger
 * @since 07.10.2009
 */
public class MooseWriter {
    private final Map<String, ClassInfo> classes;
    private Set<PackageInfo> namespaces;
    private Collection<PackageInfo> packages;
    private int index;

    public MooseWriter(final Map<String, ClassInfo> classes) {
        this.classes = classes;
        index = classes.size() + 2;
        System.out.println("max.class.idx = " + index);
        this.namespaces = extractNamespaces(classes, index);
        index += namespaces.size();
        System.out.println("max.ns.idx = " + index);
        this.packages = extractPackages(namespaces, index);
        index += packages.size();
        System.out.println("max.pkg.idx = " + index);
    }

    private Collection<PackageInfo> extractPackages(Collection<PackageInfo> namespaces, int startIdx) {
        Collection<PackageInfo> result=new HashSet<PackageInfo>(namespaces.size());
        for (PackageInfo namespace : namespaces) {
            PackageInfo packageInfo = new PackageInfoBean(namespace.getName(), startIdx++);
            result.add(packageInfo);
            while (!packageInfo.isRoot()) {
                final PackageInfo superPackage = new PackageInfoBean(packageInfo.getSuperPackage(), startIdx);
                if (!result.contains(superPackage)) {
                    result.add(superPackage);
                    startIdx++;
                }
                packageInfo=superPackage;
            }
        }
        return result;
    }

    private Set<PackageInfo> extractNamespaces(final Map<String, ClassInfo> classes, int startIdx) {
        final Set<PackageInfo> result = new HashSet<PackageInfo>();
        for (final ClassInfo info : classes.values()) {
            final PackageInfoBean packageInfo = new PackageInfoBean(info.getPackage(), startIdx);
            if (!result.contains(packageInfo)) {
                result.add(packageInfo);
                startIdx++;
            }
        }
        return result;
    }

    public static void main(final String[] args) throws Exception {

        final String file = args[0];
        final Class<?> type = args.length > 1 ? Class.forName(args[1]) : Object.class;
        long time= currentTimeMillis();
        final Map<String, ClassInfo> classes = new ClassParser().loadClasses(type);
        time= currentTimeMillis()-time;
        System.out.println("reading took " + time+" ms");
        time= currentTimeMillis();
        new MooseWriter(classes).writeFile(file);
        time= currentTimeMillis()-time;
        System.out.println("\nwriting took " + time+" ms");
    }

    private void writeFile(final String file) {
        try {
            final Writer os = new BufferedWriter(new FileWriter(file));
            writeHeader(os);
            writePackages(os);
            writeClasses(os,index);
            writeFooter(os);
            os.close();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    private void writeClasses(Writer os, int index) throws IOException {
        System.out.println("\nWriting classes");
        for (ClassInfo info : classes.values()) {
            os.write("\t\t(FAMIX.Class (id: "+info.getId()+")\n" +
                    "\t\t\t(name '"+info.getSimpleName()+"')\n" +
                    "\t\t\t(belongsTo (idref: "+getPackageId(namespaces, info.getPackage())+"))\n" +
                    "\t\t\t(isAbstract false)\n" +
                    "\t\t\t(NOM "+info.getMethods().size()+")\n" +
                    "\t\t\t(isInterface false)\n" +

                    "\t\t\t(packagedIn (idref: "+getPackageId(packages,info.getPackage())+")))\n");
            if (!info.isRoot()) {
                os.write("\t\t(FAMIX.InheritanceDefinition (id: "+(index++)+")\n" +
                    "\t\t\t(subclass (idref: "+info.getId()+"))\n" +
                    "\t\t\t(superclass (idref: "+ info.getSuperClass().getId() +")))\n");
            }
        }
    }

    private int getPackageId(Collection<PackageInfo> packages, String pkgName) {
        for (PackageInfo namespace : packages) {
            if (namespace.getName().equals(pkgName)) return namespace.getId();
        }
        throw new IllegalArgumentException("Unknown package "+pkgName);
    }

    private void writePackages(Writer os) throws IOException {
        System.out.println("\nWriting namespaces");
        for (PackageInfo info : namespaces) {
            os.write("\t\t(FAMIX.Namespace (id: "+info.getId()+")\n" +
                    "\t\t\t(name '"+toMoose(info.getName())+"'))\n");
        }
        System.out.println("\nWriting packages");
        for (PackageInfo aPackage : packages) {
            os.write("\t\t(FAMIX.Package (id: "+aPackage.getId()+")\n" +
                    "\t\t\t(name '"+toMoose(aPackage.getName())+"')\n" +
                    "\t\t\t(DIH "+dih(aPackage)+")\n" +
                    (aPackage.isRoot() ? "" : "\t\t\t(packagedIn (idref: "+getPackageId(packages,aPackage.getSuperPackage())+"))")
                    +")\n");
        }
    }

    private int dih(PackageInfo aPackage) {
        return aPackage.getName().split("\\.").length;
    }

    private String toMoose(String pkg) {
        return pkg.replaceAll("/","::");
    }

    private void writeFooter(Writer os) throws IOException {
        os.write(")\n" +
                "\t(LOC 1000)\n" +
                "\t(NOC "+classes.size()+")\n" +
                "\t(NOP "+ namespaces.size()+")\n" +
                "\t(sourceLanguage 'Java'))\n");
    }

    private void writeHeader(Writer os) throws IOException {
        os.write("(Moose.Model (id: 1)\n" +
                "\t(name 'test')\n" +
                "\t(entity");
    }

    static class ClassParser {
        public Map<String, ClassInfo> loadClasses(final Class<?> type) {
            final RecordingInspector inspector = new RecordingInspector();
            final ClassFileIterator fileIterator = new ClassFileIterator();
            final String jarFileLocation = fileIterator.getJarLocationByClass(type);
            for (final String classFileName : fileIterator.getClassFileNames(jarFileLocation)) {
                inspector.inspectClass(classFileName);
            }
            return inspector.getClasses();
        }
    }
}
