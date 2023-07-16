package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import javassist.*;
import javassist.bytecode.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public class Compiler {

    public static final Set<String> PRIMITIVES = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
    private static final Set<String> PRIMITIVE_NUMBER_OPERATORS = Set.of("+", "-", "*", "/", "==", ">=", "<=", "<", ">", "%", "=");

    private static String srcFolder = "";
    private static HashMap<String, Compilable> classes;

    private final Parser parser;

    private XJLNClass currentClass;
    private String currentClassName;
    private XJLNMethod currentMethod;
    private String currentMethodName;

    public Compiler(String srcFolder){
        parser = new Parser();
        classes = new HashMap<>();
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

    /*
    private void clearFolder(File folder, boolean delete){
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())){
            if(fileEntry.isDirectory()){
                clearFolder(fileEntry, true);
                if(delete && folder.listFiles().length == 0) if(!fileEntry.delete()) throw new RuntimeException("unable to clear out folders");
            }else if(fileEntry.getName().endsWith(".class")) if(!fileEntry.delete()) throw new RuntimeException("unable to clear out folders");
        }
        if(delete)
            folder.delete();
    }

     */

    private void clearFolder(File folder, boolean delete){
        for(File file:folder.listFiles())
            if(file.isDirectory())
                clearFolder(file, true);
            else if(file.getName().endsWith(".class")) file.delete();
        if(delete && folder.listFiles().length == 0)
            folder.delete();
    }

    private void compileFolder(File folder){
        for (File fileEntry : Objects.requireNonNull(folder.listFiles())){
            if(fileEntry.isDirectory()) compileFolder(fileEntry);
            else if(fileEntry.getName().endsWith(".xjln")) classes.putAll(parser.parseFile(fileEntry));
        }
        ClassPool cp = ClassPool.getDefault();
        for(String name:classes.keySet()){
            cp.makeClass(compileClass(classes.get(name), name));
            try{
                if(classes.get(name) instanceof XJLNClass)
                    compileMethods(name, (XJLNClass) classes.get(name), cp.get(name));
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
        cf.setAccessFlags(AccessFlag.setPublic(AccessFlag.PUBLIC));

        //Fields
        for(String n:clazz.fields.keySet())
            addField(cf, clazz.fields.get(n), n);
        for(String n:clazz.parameter.getKeys())
            addField(cf, clazz.parameter.get(n), n);

        //Constructor
        MethodInfo method = new MethodInfo(cf.getConstPool(), "<init>", toDesc(clazz.parameter.getValues(), "void"));
        method.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        int i = 0;
        for(String n:clazz.parameter.getKeys()){
            XJLNVariable v = clazz.parameter.get(n);
            code.addAload(0);
            i += 1;
            switch(v.type){
                case "int", "byte", "char", "short", "boolean" -> code.addIload(i);
                case "double" -> code.addDload(i);
                case "long" -> code.addLload(i);
                case "float" -> code.addFload(i);
                default -> code.addAload(i);
            }
            code.addAload(i);
            code.addPutfield(name, n, toDesc(v.type));
        }
        code.addReturn(null);
        method.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(method);

        return cf;
    }

    private void addField(ClassFile cf, XJLNVariable v, String name){
        FieldInfo field = new FieldInfo(cf.getConstPool(), name, toDesc(v.type));
        field.setAccessFlags(v.constant ? v.inner ? AccessFlag.setPrivate(AccessFlag.FINAL) : AccessFlag.setPublic(AccessFlag.FINAL) : v.inner ? AccessFlag.PRIVATE : AccessFlag.PUBLIC);

        try{
            cf.addField(field);
        }catch (DuplicateMemberException ignored){}
    }

    private void compileMethods(String className, XJLNClass clazz, CtClass ct){
        currentClass = clazz;
        currentClassName = className;

        if(ct.isFrozen())
            ct.defrost();

        for(String methodName: clazz.methods.keySet()){
            currentMethod = clazz.methods.get(methodName);
            currentMethodName = methodName;
            try {
                ct.addMethod(compileMethod(ct));
            }catch (CannotCompileException e){
                throw new RuntimeException(e);
            }
        }
    }

    private CtMethod compileMethod(CtClass clazz) throws CannotCompileException{
        StringBuilder src = new StringBuilder();

        src.append(currentMethod.inner ? "private " : "public ").append(currentMethod.returnType).append(" ").append(currentMethodName).append("(");

        for(String para:currentMethod.parameter.getKeys())
            src.append(currentMethod.parameter.get(para).type).append(" ").append(para).append(",");

        if(src.toString().endsWith(","))
            src.deleteCharAt(src.length() - 1);

        src.append("){");

        for(String statement:currentMethod.code){
            switch(statement.split(" ")[0]){
                case "if" -> src.append(compileIf(statement));
                case "while" -> src.append(compileWhile(statement));
                case "return" -> src.append(compileReturn(statement));
                case "end" -> {
                    if(!statement.equals("end"))
                        throw new RuntimeException("illegal argument in: " + statement);
                    src.append("}");
                }
                default -> src.append(compileStatement(Lexer.toToken(statement)));
            }
        }

        src.append("}");

        return CtMethod.make(src.toString(), clazz);
    }

    private String compileIf(String statement){
        TokenHandler th = Lexer.toToken(statement);
        StringBuilder sb = new StringBuilder();

        th.assertToken("if");
        sb.append("if(").append(compileCalc(th)).append("){");

        if(th.hasNext()){
            th.assertToken("->");
            th.assertHasNext();
            sb.append(compileStatement(th)).append("}");
        }

        return sb.toString();
    }

    private String compileWhile(String statement){
        TokenHandler th = Lexer.toToken(statement);
        StringBuilder sb = new StringBuilder();

        th.assertToken("while");
        sb.append("while(").append(compileCalc(th)).append("){");

        if(th.hasNext()){
            th.assertToken("->");
            th.assertHasNext();
            sb.append(compileStatement(th)).append("}");
        }

        return sb.toString();
    }

    private String compileReturn(String statement){
        TokenHandler th = Lexer.toToken(statement);
        th.assertToken("return");
        return "return " + compileCalc(th) + ";";
    }

    private String compileStatement(TokenHandler th){
        Token first = th.assertToken(Token.Type.IDENTIFIER);
        if(th.next().equals(Token.Type.IDENTIFIER)){
            th.last();
            return first.toString() + " " + compileCalc(th) + ";";
        }else {
            th.last();
            th.last();
            return compileCalc(th) + ";";
        }
    }

    private String compileCalc(TokenHandler th){
        StringBuilder sb = new StringBuilder();
        String type = th.next().t().toString();

        switch (th.current().t()) {
            case NUMBER -> sb.append(th.current());
            case IDENTIFIER -> {
                String current = compileCurrent(th);
                type = current.split(" ", 2)[0];
                sb.append(current.split(" ", 2)[1]);
            }
            default -> throw new RuntimeException("illegal argument in: " + th);
        }

        while (th.hasNext()) {
            Token operator = th.assertToken(Token.Type.OPERATOR, Token.Type.SIMPLE);

            if(operator.equals(Token.Type.SIMPLE) || operator.equals("->")) {
                th.last();
                return sb.toString();
            }

            switch (type){
                case "NUMBER", "int", "double", "long", "short" -> {
                    if(!PRIMITIVE_NUMBER_OPERATORS.contains(operator.s()))
                           throw new RuntimeException("illegal operator");
                    sb.append(operator.s());
                }
                default -> {
                    if(!classExist(type))
                        throw new RuntimeException("class " + type + " does not exist in: " + th);
                    sb.append(".").append(operator.s()).append("(");
                }
            }

            String currentType;

            switch (th.next().t()) {
                case NUMBER -> {
                    sb.append(th.current());
                    currentType = "NUMBER";
                }
                case IDENTIFIER -> {
                    String current = compileCurrent(th);
                    currentType = current.split(" ", 2)[0];
                    sb.append(current.split(" ", 2)[1]);
                }
                default -> throw new RuntimeException("illegal argument in: " + th);
            }

            if(!hasMethod(type, operator.s(), currentType))
                throw new RuntimeException("operator " + operator + " is not defined for " + type + " and " + currentType);

            if(!PRIMITIVES.contains(type))
                sb.append(")");

            type = currentType;
        }

        return sb.toString();
    }

    private String compileCurrent(TokenHandler th){
        StringBuilder sb = new StringBuilder();
        String lastType = currentClassName;
        String call = th.current().s();
        sb.append(call);

        switch (th.next().s()){
            case "(" -> {
                sb.append("(");
                th.assertHasNext();
                while(th.hasNext()){
                    if(th.next().equals(")")){
                        sb.append(")");
                        break;
                    }
                    sb.append(compileCalc(th));
                    th.assertToken(",", ")");
                    if(th.current().equals(")")) {
                        sb.append(")");
                        th.last();
                    }else {
                        sb.append(",");
                        th.assertHasNext();
                    }
                }
                lastType = getType(lastType, call, null);
            }
            case ":" -> {
                th.last();
                lastType = getType(lastType, currentMethodName, call);
            }
            default -> {
                th.last();
                return getType(lastType, currentMethodName, call) + " " + call;
            }
        }

        while (th.hasNext()){
            if(th.next().equals(":")){
                call = th.current().s();
                sb.append(call);

                switch (th.next().s()){
                    case ":" -> {
                        th.last();
                        lastType = getType(lastType, null, call);
                    }
                    case "(" -> {
                        sb.append("(");
                        th.assertHasNext();
                        while(th.hasNext()){
                            if(th.next().equals(")")){
                                sb.append(")");
                                break;
                            }
                            sb.append(compileCalc(th));
                            th.assertToken(",", ")");
                            if(th.current().equals(")")) {
                                sb.append(")");
                                th.last();
                            }else {
                                sb.append(",");
                                th.assertHasNext();
                            }
                        }
                        lastType = getType(lastType, call, null);
                    }
                    default -> {
                        th.last();
                        return lastType + " " + sb;
                    }
                }
            }else{
                th.last();
                break;
            }
        }

        th.last();
        return lastType + " " + sb;
    }

    private String getType(String clazz, String method, String var){
        if(var == null){
            if(classes.get(clazz) instanceof XJLNClass && ((XJLNClass) classes.get(clazz)).methods.containsKey(method)){
                return ((XJLNClass) classes.get(clazz)).methods.get(method).returnType;
            }else
                throw new RuntimeException("illegal argument");
        }else if(classes.get(clazz) instanceof XJLNClass){
            if(method != null && ((XJLNClass) classes.get(clazz)).methods.get(method).parameter.get(var) != null)
                return ((XJLNClass) classes.get(clazz)).methods.get(method).parameter.get(var).type;
            if(((XJLNClass) classes.get(clazz)).fields.get(var) != null)
                return ((XJLNClass) classes.get(clazz)).fields.get(var).type;
            if(((XJLNClass) classes.get(clazz)).parameter.get(var) != null)
                return ((XJLNClass) classes.get(clazz)).parameter.get(var).type;
        }else if(classes.get(clazz) instanceof XJLNEnum)
            return clazz;
        return null;
    }

    public static String toDesc(ArrayList<XJLNVariable> parameters, String returnType){
        StringBuilder sb = new StringBuilder("(");
        for(XJLNVariable parameter:parameters) sb.append(toDesc(parameter.type));
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

    public static boolean classExist(String validName){
        if(PRIMITIVES.contains(validName)) return true;
        if(classes.containsKey(validName)) return true;
        try {
            Class.forName(validName);
            return true;
        }catch (ClassNotFoundException ignored){}
        try {
            ClassPool.getDefault().get(validName);
            return true;
        }catch (NotFoundException ignored){}
        return false;
    }

    public static boolean hasMethod(String clazz, String method, String...types){
        if(Set.of("NUMBER", "int", "double", "long", "short").contains(clazz)) return PRIMITIVE_NUMBER_OPERATORS.contains(method) && types.length == 1 && Set.of("int", "double", "long", "short", "NUMBER").contains(types[0]);
        if(classes.containsKey(clazz)){
            if(classes.get(clazz) instanceof XJLNClass && ((XJLNClass) classes.get(clazz)).methods.containsKey(method))
                return ((XJLNClass) classes.get(clazz)).methods.get(method).matches(types);
            else
                return false;
        }
        return false;
    }
}
