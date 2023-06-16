package com.github.xjln.compiler;

import com.github.xjln.lang.Compilable;
import com.github.xjln.lang.XJLNClass;
import com.github.xjln.lang.XJLNEnum;
import com.github.xjln.lang.XJLNMethod;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.NotFoundException;
import javassist.bytecode.*;

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
        if(!Files.exists(compiled) && !new File("compiled").mkdirs()) throw new RuntimeException("unable to validate compiled folder");
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
        HashMap<String, Compilable> classes = new HashMap<>();
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())){
            if(fileEntry.isDirectory()) compileFolder(fileEntry);
            else if(fileEntry.getName().endsWith(".xjln")) classes.putAll(parser.parseFile(fileEntry));
        }
        ClassPool cp = ClassPool.getDefault();
        for(String name:classes.keySet()){
            cp.makeClass(compileClass(classes.get(name), name));
            try{
                cp.get(name).writeFile("compiled");
            }catch (NotFoundException | IOException | CannotCompileException ignored){
                throw new RuntimeException("internal compiler error");
            }
        }
    }

    private ClassFile compileClass(Compilable clazz, String name){
        if(clazz instanceof XJLNEnum) return compileEnum((XJLNEnum) clazz, name);
        else if(clazz instanceof XJLNClass) return compileClass((XJLNClass) clazz, name);
        else throw new RuntimeException("internal compiler error");
    }

    private ClassFile compileEnum(XJLNEnum enumm, String name){
        ClassFile cf = new ClassFile(false, name, null);
        cf.setAccessFlags(AccessFlag.setPublic(AccessFlag.ENUM));

        String[] values = enumm.values;

        // enum values
        for(String value: values){
            FieldInfo f = new FieldInfo(cf.getConstPool(), value, toDesc(name));
            f.setAccessFlags(AccessFlag.of(AccessFlag.toModifier(16409)));
            try {
                cf.addField(f);
            }catch(DuplicateMemberException ignored){
                throw new RuntimeException("field " + value + " is defined more times in " + name);
            }
        }

        // <init>
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");
        code.addReturn(null);

        MethodInfo m = new MethodInfo(
                cf.getConstPool(), "<init>", "()V");
        m.setCodeAttribute(code.toCodeAttribute());
        m.setAccessFlags(AccessFlag.PRIVATE);
        cf.addMethod2(m);

        // <clinit>
        code = new Bytecode(cf.getConstPool());

        for (String value : values) {
            code.addNew(toDesc(name));
            code.add(89);
            code.addInvokespecial(name, "<init>", "()V");
            code.addPutstatic(name, value, toDesc(name));
        }

        code.addReturn(null);

        m = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        m.setAccessFlags(AccessFlag.STATIC);
        m.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(m);

        return cf;
    }

    private ClassFile compileClass(XJLNClass clazz, String name){
        ClassFile cf = new ClassFile(false, name, null);
        cf.setAccessFlags(AccessFlag.PUBLIC);

        //fields
        for(String fieldName: clazz.fields.keySet()){
            FieldInfo f = new FieldInfo(cf.getConstPool(), fieldName, clazz.fields.get(fieldName).startsWith("inner ") ? toDesc(clazz.fields.get(fieldName).split(" ")[1]) : toDesc(clazz.fields.get(fieldName)));
            f.setAccessFlags(clazz.fields.get(fieldName).startsWith("inner ") ? AccessFlag.PRIVATE : AccessFlag.PUBLIC);
            cf.addField2(f);
        }

        //<init>
        MethodInfo m = new MethodInfo(cf.getConstPool(), "<init>", toDesc(clazz.parameter, "void"));
        m.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());

        int i = 0;
        for(String parameter:clazz.parameter){
            String[] infos = parameter.split(" ");

            if(clazz.fields.containsKey(infos[1])){
                if(!infos[0].equals(clazz.fields.get(infos[1]).startsWith("inner ") ? clazz.fields.get(infos[1]).split(" ")[1] : clazz.fields.get(infos[1]))) throw new RuntimeException("type exception for class-parameter " + infos[1] + " in class " + name);
            }else{
                FieldInfo f = new FieldInfo(cf.getConstPool(), infos[1], toDesc(infos[0]));
                f.setAccessFlags(AccessFlag.PUBLIC);
                cf.addField2(f);
            }

            code.addAload(0);
            code.addAload(i += 1);
            code.addPutfield(name, infos[1], toDesc(infos[0]));
        }

        if(clazz.constructor != null){
            if(!clazz.methods.containsKey(clazz.constructor)) throw new RuntimeException("method " + clazz.constructor + " does not exist");
            if(!clazz.methods.get(clazz.constructor).returnType.equals("void")) throw new RuntimeException("method " + clazz.constructor + " must be ()V in " + name);
            code.addAload(0);
            code.addInvokevirtual(name, clazz.constructor.split(" ")[0], "()V");
        }

        code.addReturn(null);
        m.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(m);

        //methods
        for(String methodName: clazz.methods.keySet()){
            XJLNMethod method = clazz.methods.get(methodName);
            m = new MethodInfo(cf.getConstPool(), methodName.split(" ")[0], toDesc(method.parameter, method.returnType));
            m.setAccessFlags(method.inner ? AccessFlag.PRIVATE : AccessFlag.PUBLIC);

            code = new Bytecode(cf.getConstPool());
            code.addReturn(null);
            m.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod2(m);
        }

        return cf;
    }

    public static String toDesc(String[] parameters, String returnType){
        StringBuilder sb = new StringBuilder("(");
        for(String parameter:parameters) sb.append(toDesc(parameter.split(" ")[0]));
        sb.append(")");
        sb.append(toDesc(returnType));
        return sb.toString();
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
        return name;
    }
}
