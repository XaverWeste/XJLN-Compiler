package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

import javassist.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public class Compiler {

    public static final MatchedList<String, String> OPERATOR_LIST = MatchedList.of(
            new String[]{"+"  , "-"       , "*"       , "/"     , "="     , "<"       , ">"          , "!"  , "%"     , "&"  , "|" },
            new String[]{"add", "subtract", "multiply", "divide", "equals", "lessThan", "greaterThan", "not", "modulo", "and", "or"});

    public static final MatchedList<String, String> WRAPPER_CLASSES = MatchedList.of(
            new String[]{"var"                        , "int"              , "double"          , "long"          , "float"          , "boolean"          , "char"               , "byte"          , "short"},
            new String[]{"com.github.xjln.utility.Var", "java.lang.Integer", "java.lang.Double", "java.lang.Long", "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short"});

    public  static final Set<String> PRIMITIVES                  = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
    private static final Set<String> PRIMITIVE_NUMBER_OPERATORS  = Set.of("+", "-", "*", "/", "!=", "==", ">=", "<=", "<", ">", "%", "=");
    private static final Set<String> PRIMITIVE_BOOLEAN_OPERATORS = Set.of("==", "!=", "=", "&", "|");

    private static String[] srcFolders = new String[0];
    private static HashMap<String, Compilable> classes;

    private final Parser parser;

    /**
     * creates a new instance of the XJLN-Compiler and starts compiling
     * @param main the path to the .xjln file with the main method (if there is no main method or no main method should be executed the parameter should be null)
     * @param srcFolders the paths to the folders that should be compiled
     */
    public Compiler(String main, String...srcFolders){
        parser = new Parser();
        classes = new HashMap<>();
        Compiler.srcFolders = srcFolders;
        validateFolders();
    }

    private void validateFolders(){
        Path compiled = Paths.get("compiled");
        if(!Files.exists(compiled) && !new File("compiled").mkdirs()) throw new RuntimeException("unable to validate compiled folder");
        else clearFolder(compiled.toFile(), false);
        for (String srcFolder : srcFolders){
            if (!Files.exists(Paths.get(srcFolder))) throw new RuntimeException("unable to find source folder " + srcFolder);
            try {
                ClassPool.getDefault().appendClassPath("compiled" + srcFolder);
            }catch (NotFoundException ignored){}
        }
    }

    private void clearFolder(File folder, boolean delete){
        for(File file: Objects.requireNonNull(folder.listFiles()))
            if(file.isDirectory())
                clearFolder(file, true);
            else if(file.getName().endsWith(".class") && !file.delete())
                throw new RuntimeException("failed to delete " + file.getPath());
        if(delete && Objects.requireNonNull(folder.listFiles()).length == 0)
            if(!folder.delete())
                throw new RuntimeException("unable to clear folder " + folder.getPath());
    }

    public static String toDesc(String type){
        if(type.startsWith("["))
            return "[" + toDesc(type.substring(1));

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
        return name;
    }

    public static String toIdentifier(String operators){
        StringBuilder sb = new StringBuilder();
        for(char c:operators.toCharArray())
            sb.append(OPERATOR_LIST.getSecond(String.valueOf(c))).append("_");
        if(sb.length() > 0)
            sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public static String getPrimitiveType(String value){
        if(value.contains(".")){
            try{
                Float.parseFloat(value);
                return "float";
            }catch (NumberFormatException ignored){}
            try{
                Double.parseDouble(value);
                return "double";
            }catch (NumberFormatException ignored){}
        }else{
            try{
                Short.parseShort(value);
                return "short";
            }catch (NumberFormatException ignored){}
            try{
                Integer.parseInt(value);
                return "int";
            }catch (NumberFormatException ignored){}
            try{
                Long.parseLong(value);
                return "long";
            }catch (NumberFormatException ignored){}
        }
        throw new RuntimeException("internal compiler error");
    }
}
