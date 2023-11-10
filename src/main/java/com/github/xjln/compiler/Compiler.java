package com.github.xjln.compiler;

import com.github.xjln.utility.MatchedList;

import java.io.File;
import java.util.*;

public final class Compiler {

    private static final MatchedList<String, String> OPERATOR_LIST = MatchedList.of(
            new String[]{"+"  , "-"       , "*"       , "/"     , "="     , "<"       , ">"          , "!"  , "%"     , "&"  , "|" },
            new String[]{"add", "subtract", "multiply", "divide", "equals", "lessThan", "greaterThan", "not", "modulo", "and", "or"});

    private static final MatchedList<String, String> WRAPPER_CLASSES = MatchedList.of(
            new String[]{"var"                        , "int"              , "double"          , "long"          , "float"          , "boolean"          , "char"               , "byte"          , "short"},
            new String[]{"com.github.xjln.utility.Var", "java.lang.Integer", "java.lang.Double", "java.lang.Long", "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short"});

    private  static final Set<String> PRIMITIVES                  = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");

    private static boolean debug;

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

        System.out.println("\nFinished compilation successfully");
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

        System.out.println("\nFinished compilation successfully");
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

        System.out.println("\nFinished compilation successfully");
    }

    private void compile(String[] srcFolders){
        validateFolders(srcFolders);
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

            printDebug("output Folder cleared successfully");
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

    private static void printDebug(String message){
        if(debug)
            System.out.println(message);
    }
}
