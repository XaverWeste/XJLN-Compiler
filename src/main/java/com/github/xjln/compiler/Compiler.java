package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNClass;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;

public class Compiler {

    private final Parser parser;
    private static String srcFolder = "";

    public Compiler(String srcFolder){
        parser = new Parser();
        Compiler.srcFolder = srcFolder;
        validateFolders();
        compileFolder(new File(srcFolder));
    }

    private void validateFolders() throws RuntimeException{
        Path compiled = Paths.get("compiled");
        if(!Files.exists(compiled)) throw new RuntimeException("compiled folder didn't exist");
        else clearFolder(compiled.toFile(), false);
        if(!Files.exists(Paths.get(srcFolder))) throw new RuntimeException("unable to find source folder");
        srcFolder = srcFolder.replace("/", ".").replace("\\", ".");
    }

    private void clearFolder(File folder, boolean delete) throws RuntimeException{
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())){
            if(fileEntry.isDirectory()){
                clearFolder(fileEntry, true);
                if(delete && Objects.requireNonNull(folder.listFiles()).length == 0) if(!fileEntry.delete()) throw new RuntimeException("unable to clear out folders");
            }else if(fileEntry.getName().endsWith(".class")) if(!fileEntry.delete()) throw new RuntimeException("unable to clear out folders");
        }
    }

    private void compileFolder(File folder){
        HashMap<String, XJLNClass> classes = new HashMap<>();
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())){
            if(fileEntry.isDirectory()) compileFolder(fileEntry);
            else if(fileEntry.getName().endsWith(".xjln")) classes.putAll(parser.parseFile(fileEntry));
        }

    }

    public static String toDesc(String type){
        return switch (type){
            case "int" -> "I";
            case "double" -> "D";
            case "char" -> "C";
            case "short" -> "S";
            case "float" -> "F";
            case "byte" -> "B";
            case "boolean" -> "Z";
            case "long" -> "J";
            case "void" -> "V";
            default -> "L" + type + ";";
        };
    }

    public static String validateName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        //if(name.startsWith(srcFolder)) name = name.substring(srcFolder.length() + 1);
        return name;
    }
}
