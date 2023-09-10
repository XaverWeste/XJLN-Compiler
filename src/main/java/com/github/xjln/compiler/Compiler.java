package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

import javassist.*;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.ClassFile;
import javassist.bytecode.MethodInfo;

import java.io.File;
import java.io.IOException;
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
        for(String folder:srcFolders)
            parseFolder(new File(folder));
        compile();
        System.out.println("XJLN: finished Compilation successful");
    }

    private void validateFolders(){
        Path compiled = Paths.get("compiled");
        if(!Files.exists(compiled) && !new File("compiled").mkdirs())
            throw new RuntimeException("unable to validate compiled folder");
        else
            clearFolder(compiled.toFile(), false);
        for (String srcFolder : srcFolders){
            if (!Files.exists(Paths.get(srcFolder)))
                throw new RuntimeException("unable to find source folder " + srcFolder);
            try {
                ClassPool.getDefault().appendClassPath("compiled/" + srcFolder);
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
            delete = folder.delete();
    }

    private void parseFolder(File folder){
        for(File file: Objects.requireNonNull(folder.listFiles())){
            if(file.isDirectory())
                parseFolder(file);
            else if(file.getName().endsWith(".xjln"))
                classes.putAll(parser.parseFile(file));
        }
    }

    private void compile(){
        for(String name: classes.keySet()){
            Compilable compilable = classes.get(name);
            if(compilable instanceof XJLNInterface)
                compileInterface((XJLNInterface) compilable);
        }
    }

    private void writeFile(ClassFile cf){
        ClassPool cp = ClassPool.getDefault();
        CtClass ct = cp.makeClass(cf);
        try {
            ct.writeFile("compiled");
        }catch(CannotCompileException | IOException e){
            throw new RuntimeException("Failed to write output File", e);
        }
    }

    private void compileInterface(XJLNInterface xjlnInterface){
        ClassFile cf = new ClassFile(true, xjlnInterface.name(), null);

        for(XJLNMethodAbstract method:xjlnInterface.methods().values()){
            if(method instanceof XJLNMethod){
                throw new RuntimeException("Interface " + xjlnInterface.name() + " should not contain non abstract Method " + method.name);
            }else{
                MethodInfo mInfo = new MethodInfo(cf.getConstPool(), method.name, toDesc(method));
                mInfo.setAccessFlags(AccessFlag.setPublic(AccessFlag.ABSTRACT));
                cf.addMethod2(mInfo);
            }
        }

        writeFile(cf);
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

    public static String toDesc(XJLNMethodAbstract method){
        StringBuilder desc = new StringBuilder();

        desc.append("(");

        for(XJLNParameter parameter:method.parameterTypes.getValueList())
            desc.append(toDesc(parameter.type()));

        desc.append(")").append(toDesc(method.returnType));

        return desc.toString();
    }

    public static String toCompilerDesc(XJLNMethodAbstract method){
        StringBuilder desc = new StringBuilder();
        if(method.statik)
            desc.append("static_");
        desc.append("(");
        if(method.parameterTypes != null)
            for(XJLNParameter p:method.parameterTypes.getValueList())
                desc.append(p.type()).append(",");
        if(!desc.toString().endsWith("("))
            desc.deleteCharAt(desc.length() - 2);
        desc.append(") ").append(method.name);
        return desc.toString();
    }

    public static String validateName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    public static String toIdentifier(String operators){
        StringBuilder sb = new StringBuilder();
        for(char c:operators.toCharArray())
            sb.append(OPERATOR_LIST.getValue(String.valueOf(c))).append("_");
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
