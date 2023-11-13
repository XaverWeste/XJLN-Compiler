package com.github.xjln.compiler;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.lang.XJLNClass;
import com.github.xjln.lang.XJLNField;
import com.github.xjln.lang.XJLNFile;
import com.github.xjln.utility.MatchedList;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.FieldInfo;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public final class Compiler {

    private static final MatchedList<String, String> OPERATOR_LIST = MatchedList.of(
            new String[]{"+"  , "-"       , "*"       , "/"     , "="     , "<"       , ">"          , "!"  , "%"     , "&"  , "|" },
            new String[]{"add", "subtract", "multiply", "divide", "equals", "lessThan", "greaterThan", "not", "modulo", "and", "or"});

    private static final MatchedList<String, String> WRAPPER_CLASSES = MatchedList.of(
            new String[]{"var"                        , "int"              , "double"          , "long"          , "float"          , "boolean"          , "char"               , "byte"          , "short"},
            new String[]{"com.github.xjln.utility.Var", "java.lang.Integer", "java.lang.Double", "java.lang.Long", "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short"});

    public static final Set<String> PRIMITIVES = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");

    private static boolean debug;

    private final HashMap<String, XJLNFile> files = new HashMap<>();
    private final Parser parser = new Parser();

    /**
     * compiles all .xjln Files in the given Folders and runs the main method in the given Main class
     * @param mainClass the class that contains the main method
     * @param enableDebugInformation if information of the compilation process should be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(String mainClass, boolean enableDebugInformation, String... srcFolders) throws RuntimeException{
        debug = enableDebugInformation;

        if(srcFolders.length > 0) {
            compile(srcFolders);
            //TODO run main
        }
    }

    /**
     * compiles all .xjln Files in the given Folders. No Main Method will be executed
     * @param enableDebugInformation if information of the compilation process should be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(boolean enableDebugInformation, String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = enableDebugInformation;
            compile(srcFolders);
        }
    }

    /**
     * compiles all .xjln Files in the given Folders. No Main Method will be executed, no information of the compilation process will be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = false;
            compile(srcFolders);
        }
    }

    private void compile(String[] srcFolders){
        validateFolders(srcFolders);

        for(String folder:srcFolders)
            parseFolder(new File(folder));

        printDebug("parsing finished successfully");

        compileFiles();

        System.out.println("\nFinished compilation process successfully\n");
    }

    private void validateFolders(String[] srcFolders){
        File file;

        for(String path:srcFolders){
            file = new File(path);

            if(!file.exists())
                throw new RuntimeException("Folder " + path + " does not exist");

            if(!file.isDirectory())
                throw new RuntimeException("Expected Folder got File with " + path);
        }

        printDebug("src Folders have been validated");

        file = new File("compiled");

        if(!file.exists() || !file.isDirectory()){
            if(!file.mkdir())
                throw new RuntimeException("Failed to create output Folder");

            printDebug("output Folder has been created");
        }else{
            clearFolder(file, false);

            printDebug("output Folder has been cleared");
        }
    }

    private void clearFolder(File folder, boolean delete){
        for(File file: Objects.requireNonNull(folder.listFiles())){
            if(file.isDirectory())
                clearFolder(file, true);
            else if(!file.delete())
                throw new RuntimeException("failed to delete " + file.getPath());
        }

        if(delete){
            if(Objects.requireNonNull(folder.listFiles()).length != 0)
                throw new RuntimeException("failed to delete files in " + folder.getPath());

            if(!folder.delete())
                throw new RuntimeException("failed to delete " + folder.getPath());
        }
    }

    private void parseFolder(File folder){
        for(File file: Objects.requireNonNull(folder.listFiles())) {
            if (file.isDirectory())
                parseFolder(file);
            else
                parseFile(file);
        }
    }

    private void parseFile(File file){
        if(file.getName().endsWith(".xjln")) {
            try {
                XJLNFile xjlnFile = parser.parseFile(file);
                if(xjlnFile != null)
                    files.put(file.getPath().substring(0, file.getPath().length() - 5), xjlnFile);
            } catch (FileNotFoundException ignored) {
                throw new RuntimeException("Unable to access " + file.getPath());
            }
        }
    }

    private void compileFiles(){
        for(String path: files.keySet()){
            XJLNFile file = files.get(path);

            if(!file.main.isEmpty())
                compile(file.main, path + ".Main");
        }
    }

    private void compile(XJLNClass clazz, String name){
        ClassFile cf = new ClassFile(false, name, null);
        cf.setAccessFlags(AccessFlag.PUBLIC); //TODO accessflag

        for(String field:clazz.staticFields.keySet()){
            cf.addField2(compileField(field, clazz.getField(field), true, cf.getConstPool()));
        }
    }

    private FieldInfo compileField(String name, XJLNField field, boolean statik, ConstPool cp){
        FieldInfo fInfo = new FieldInfo(cp, name, "I"); //TODO desc
        fInfo.setAccessFlags(AccessFlag.PUBLIC + (statik ? AccessFlag.STATIC : 0)); //TODO Accessflag
        return fInfo;
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile("compiled");
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException("failed to write ClassFile for " + cf.getName());
        }
    }

    public static String validateName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    private static void printDebug(String message){
        if(debug)
            System.out.println(message);
    }
}
