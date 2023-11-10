package com.github.xjln.compiler;

import com.github.xjln.lang.Compilable;
import com.github.xjln.utility.MatchedList;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.ClassFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
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

    private final HashMap<String, Compilable> classes = new HashMap<>();
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

        System.out.println("\nFinished compilation successfully\n");
    }

    /**
     * compiles all .xjln Files in the given Folders
     * @param enableDebugInformation if information of the compilation process should be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(boolean enableDebugInformation, String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = enableDebugInformation;
            compile(srcFolders);
        }

        System.out.println("\nFinished compilation successfully\n");
    }

    /**
     * compiles all .xjln Files in the given Folders
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = false;
            compile(srcFolders);
        }

        System.out.println("\nFinished compilation successfully\n");
    }

    private void compile(String[] srcFolders){
        validateFolders(srcFolders);

        for(String folder:srcFolders)
            compileFolder(new File(folder));
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
        for(File file:folder.listFiles()){
            if(file.isDirectory()) {
                clearFolder(file, true);

                if(delete && file.listFiles().length == 0)
                    if(!file.delete())
                        throw new RuntimeException("failed to delete Folder " + file.getPath());
            }else if(!file.delete())
                throw new RuntimeException("failed to delete " + file.getPath());
        }
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile("compiled");
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException("failed to write ClassFile for " + cf.getName());
        }
    }

    private void compileFolder(File folder){
        for(File file:folder.listFiles()) {
            if (file.isDirectory())
                compileFolder(file);
            else
                compileFile(file);
        }
    }

    private void compileFile(File file){
        if(file.getName().endsWith(".xjln")) {
            try {
                classes.putAll(parser.parseFile(file));
            } catch (FileNotFoundException ignored) {
                throw new RuntimeException("Unable to access " + file.getPath());
            }
        }
    }

    private static void printDebug(String message){
        if(debug)
            System.out.println(message);
    }
}
