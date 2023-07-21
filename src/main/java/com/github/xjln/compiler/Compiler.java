package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import javassist.*;
import javassist.bytecode.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

public class Compiler {

    public static final Set<String> PRIMITIVES = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
    private static final Set<String> PRIMITIVE_NUMBER_OPERATORS = Set.of("+", "-", "*", "/", "==", ">=", "<=", "<", ">", "%", "=");

    private static String[] srcFolders = new String[0];
    private static HashMap<String, Compilable> classes;

    private final Parser parser;

    private XJLNClass currentClass;
    private String currentClassName;
    private XJLNMethod currentMethod;
    private String currentMethodName;

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
        for(String folder:srcFolders) compileFolder(new File(folder));
        executeMain(main);
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
        for(File file:folder.listFiles())
            if(file.isDirectory())
                clearFolder(file, true);
            else if(file.getName().endsWith(".class") && !file.delete())
                throw new RuntimeException("failed to delete " + file.getPath());
        if(delete && folder.listFiles().length == 0)
            if(!folder.delete())
                throw new RuntimeException("unable to clear folder " + folder.getPath());
    }

    private void compileFolder(File folder){
        for (File fileEntry : folder.listFiles()){
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

        if(name.endsWith("Main"))
            return cf;

        //Fields
        for(String n:clazz.fields.keySet())
            addField(cf, clazz.fields.get(n), n);
        for(String n:clazz.parameter.getKeys())
            addField(cf, clazz.parameter.get(n), n);

        //Constructor
        MethodInfo method = new MethodInfo(cf.getConstPool(), "<init>", toDesc(clazz.parameter.getValues(), "void"));
        method.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");
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

        if(currentMethodName.equalsIgnoreCase("main")){
            src.append("public static void main(String[] args");
        }else {
            src.append(currentMethod.inner ? "private " : "public ");
            src.append(currentMethod.returnType).append(" ").append(currentMethodName).append("(");

            for (String para : currentMethod.parameter.getKeys())
                src.append(currentMethod.parameter.get(para).type).append(" ").append(para).append(",");

            if (src.toString().endsWith(","))
                src.deleteCharAt(src.length() - 1);
        }

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
        sb.append("if(").append(compileCalc(th).split(" ", 2)[1]).append("){");

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
        sb.append("while(").append(compileCalc(th).split(" ", 2)[1]).append("){");

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
        th.assertHasNext();
        return "return " + compileCalc(th).split(" ", 2)[1] + ";";
    }

    private String compileStatement(TokenHandler th){
        Token first = th.assertToken(Token.Type.IDENTIFIER);
        if(th.next().equals(Token.Type.IDENTIFIER)){
            currentMethod.parameter.add(th.current().s(), new XJLNVariable(first.s()));
            th.last();
            return first + " " + compileCalc(th).split(" ", 2)[1] + ";";
        }else if(th.current().equals("=")){
            th.assertHasNext();
            String calc = compileCalc(th);
            if(currentMethod.parameter.get(first.s()) == null && currentClass.parameter.get(first.s()) == null && !currentClass.fields.containsKey(first.s())) {
                currentMethod.parameter.add(first.s(), new XJLNVariable(calc.split(" ", 2)[0]));
                return (calc.split(" ", 2)[0].equals("NUMBER") ? "double" : calc.split(" ", 2)[0]) + " " + first + " = " + calc.split(" ", 2)[1] + ";";
            }else
                return first + " = " + calc.split(" ", 2)[1] + ";";
        }else{
            th.last();
            th.last();
            return compileCalc(th).split(" ", 2)[1] + ";";
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
                return type + " " + sb;
            }

            switch (type){
                case "NUMBER", "int", "double", "long", "short" -> {
                    if(!PRIMITIVE_NUMBER_OPERATORS.contains(operator.s()))
                           throw new RuntimeException("illegal operator");
                    sb.append(operator.s());
                }
                default -> {
                    if(getClassLang(type) == null)
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

        return type + " " + sb;
    }

    private String compileCurrent(TokenHandler th){
        StringBuilder sb = new StringBuilder();
        String lastType = currentClassName;
        String call = th.current().s();
        sb.append(call);

        if(!th.hasNext())
            return getType(currentClassName, currentMethodName, call) + " " + sb;

        switch (th.next().s()){
            case "(" -> {
                sb.append("(");
                th.assertHasNext();
                ArrayList<String> types = new ArrayList<>();
                while(th.hasNext()){
                    if(th.next().equals(")")){
                        sb.append(")");
                        break;
                    }
                    th.last();
                    String[] calc = compileCalc(th).split(" ", 2);
                    types.add(calc[0]);
                    sb.append(calc[1]);
                    if(th.assertToken(",", ")").equals(")")) {
                        sb.append(")");
                        break;
                    } else {
                        sb.append(",");
                        th.assertHasNext();
                    }
                }
                lastType = getReturnType(lastType, call, types.toArray(new String[0]));
            }
            case "[" -> {
                if(!currentClass.aliases.containsKey(sb.toString()) || getClassLang(currentClass.aliases.get(sb.toString())) == null)
                    throw new RuntimeException("class " + (currentClass.aliases.containsKey(sb.toString()) ? currentClass.aliases.get(sb.toString()) : sb) + " does not exist");
                lastType = currentClass.aliases.get(sb.toString());
                sb = new StringBuilder("new " + currentClass.aliases.get(sb.toString()));
                sb.append("(");
                th.assertHasNext();
                while(th.hasNext()){
                    if(th.next().equals("]")){
                        sb.append(")");
                        break;
                    }
                    th.last();
                    sb.append(compileCalc(th).split(" ", 2)[1]);
                    if(th.assertToken(",", "]").equals("]")) {
                        sb.append(")");
                        break;
                    }else {
                        sb.append(",");
                        th.assertHasNext();
                    }
                }
            }
            case ":" -> {
                th.last();
                if(getType(lastType, currentMethodName, call) == null){
                    sb = new StringBuilder(currentClass.aliases.get(call));
                    lastType = sb.toString();
                }else
                    lastType = getType(lastType, currentMethodName, call);

            }
            default -> {
                th.last();
                return getType(lastType, currentMethodName, call) + " " + call;
            }
        }

        while (th.hasNext()){
            if(th.next().equals(":")){
                sb.append(".");
                call = th.assertToken(Token.Type.IDENTIFIER).s();
                sb.append(call);

                switch (th.next().s()){
                    case ":" -> {
                        th.last();
                        lastType = getFieldType(lastType, call);
                    }
                    case "(" -> {
                        sb.append("(");
                        th.assertHasNext();
                        ArrayList<String> types = new ArrayList<>();
                        while(th.hasNext()){
                            if(th.next().equals(")")){
                                sb.append(")");
                                break;
                            }
                            th.last();
                            String[] calc = compileCalc(th).split(" ", 2);
                            types.add(calc[0]);
                            sb.append(calc[1]);
                            if(th.assertToken(",", ")").equals(")")) {
                                sb.append(")");
                                break;
                            }else {
                                sb.append(",");
                                th.assertHasNext();
                            }
                        }
                        lastType = getReturnType(lastType, call, types.toArray(new String[0]));
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

    private String getReturnType(String clazz, String method, String...parameter){
        switch (getClassLang(clazz)){
            case "java" -> {
                try{
                    Class<?>[] classes = new Class[parameter.length];
                    for(int i = 0;i < parameter.length;i++)
                        classes[i] = Class.forName(parameter[i]);
                    return Class.forName(clazz).getMethod(method, classes).getReturnType().toString().split(" ", 2)[1];
                }catch (ClassNotFoundException | NoSuchMethodException ignored){
                    return null;
                }
            }
            case "xjln" -> {
                if(classes.get(clazz) instanceof XJLNClass){
                    if(!((XJLNClass) classes.get(clazz)).methods.containsKey(method))
                        return null;
                    return ((XJLNClass) classes.get(clazz)).methods.get(method).returnType;
                }
            }
            case "unknown" -> {
                return null;
            }
            case "primitive" -> throw new RuntimeException("no such method");
        }
        return null;
    }

    private String getFieldType(String clazz, String name){
        switch (getClassLang(clazz)){
            case "java" -> {
                try{
                    return Class.forName(clazz).getField(name).getType().toString().split(" ", 2)[1];
                }catch (ClassNotFoundException | NoSuchFieldException ignored){
                    return null;
                }
            }
            case "xjln" -> {
                if(classes.get(clazz) instanceof XJLNClass){
                    if(!((XJLNClass) classes.get(clazz)).fields.containsKey(name))
                        return null;
                    return ((XJLNClass) classes.get(clazz)).fields.get(name).type;
                }
            }
            case "unknown" -> {
                return null;
            }
            case "primitive" -> throw new RuntimeException("no such method");
        }
        return null;
    }

    private void executeMain(String path){
        path = path.replace("/", ".").replace("\\", ".");
        if(!classes.containsKey(path + ".Main"))
            throw new RuntimeException("file " + path + " does not exist");
        if(classes.get(path + ".Main") instanceof XJLNClass && !((XJLNClass) classes.get(path + ".Main")).methods.containsKey("main"))
            throw new RuntimeException(path + " contains no main method");
        try {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(System.getProperty("user.dir") + "/compiled").toURI().toURL()});
            Class<?> clazz = classLoader.loadClass(path + ".Main");
            Method method = clazz.getMethod("main", String[].class);
            method.invoke(null, (Object) null);
        }catch (MalformedURLException | ClassNotFoundException | InvocationTargetException | IllegalAccessException | NoSuchMethodException e){
            throw new RuntimeException(e);
        }
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

    public static String getClassLang(String validName){
        if(PRIMITIVES.contains(validName)) return "primitive";
        if(classes.containsKey(validName)) return "xjln";
        try {
            Class.forName(validName);
            return "java";
        }catch (ClassNotFoundException ignored){}
        try {
            ClassPool.getDefault().get(validName);
            return "unknown";
        }catch (NotFoundException ignored){}
        return null;
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
