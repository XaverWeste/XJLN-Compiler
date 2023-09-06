package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

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
        if(main != null)
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
        cf.setAccessFlags(AccessFlag.PUBLIC);

        if(name.endsWith(".Main"))
            return cf;

        //Fields
        for(XJLNField field:clazz.getFields())
            addField(cf, field);

        try{
            clazz.validateSuperClasses();
        }catch (ClassNotFoundException e){
            throw new RuntimeException(e);
        }

        return cf;
    }

    private void addField(ClassFile cf, XJLNField field){
        FieldInfo fieldInfo = new FieldInfo(cf.getConstPool(), field.name(), toDesc(field.type()));
        fieldInfo.setAccessFlags(field.constant() ? field.inner() ? AccessFlag.setPrivate(AccessFlag.FINAL) : AccessFlag.setPublic(AccessFlag.FINAL) : field.inner() ? AccessFlag.PRIVATE : AccessFlag.PUBLIC);

        try{
            cf.addField(fieldInfo);
        }catch (DuplicateMemberException ignored){}
    }

    private void compileMethods(String className, XJLNClass clazz, CtClass ct){
        currentClass = clazz;
        currentClassName = className;

        if(ct.isFrozen())
            ct.defrost();

        //Constructor
        if(!className.equalsIgnoreCase("Main")) {
            try {
                StringBuilder src = new StringBuilder();

                src.append("public ").append(className.split("\\.")[className.split("\\.").length - 1]).append("(");

                for (String para : clazz.parameter.getFirstList())
                    src.append(clazz.parameter.getSecond(para).type).append(" ").append(para).append(",");

                if (src.toString().endsWith(","))
                    src.deleteCharAt(src.length() - 1);

                src.append("){");

                for (String name : clazz.parameter.getFirstList())
                    src.append("this.").append(name).append(" = ").append(name).append(";");


                src.append("}");

                CtConstructor constructor = CtNewConstructor.make(src.toString(), ct);
                ct.addConstructor(constructor);
            } catch (CannotCompileException e) {
                throw new RuntimeException(e);
            }
        }

        //Methods
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

    private CtMethod compileMethod(CtClass clazz){
        StringBuilder src = new StringBuilder();

        if(currentMethodName.equalsIgnoreCase("main")){
            src.append("public static void main(String[] args");
        }else {
            src.append(currentMethod.inner() ? "private " : "public ");
            src.append(currentMethod.returnType()).append(" ").append(currentMethodName).append("(");

            for (String para : currentMethod.parameterTypes().getFirstList())
                src.append(currentMethod.parameterTypes().getSecond(para)).append(" ").append(para).append(",");

            if (src.toString().endsWith(","))
                src.deleteCharAt(src.length() - 1);
        }

        src.append("){");

        for(String statement:currentMethod.code()){
            switch(statement.split(" ")[0]){
                case "if" -> src.append(compileIf(statement));
                case "while" -> src.append(compileWhile(statement));
                case "return" -> src.append(compileReturn(Lexer.toToken(statement)));
                case "end" -> {
                    if(!statement.equals("end"))
                        throw new RuntimeException("illegal argument in: " + statement);
                    src.append("}");
                }
                default -> src.append(compileStatement(Lexer.toToken(statement)));
            }
        }

        src.append("}");

        try {
            return CtMethod.make(src.toString(), clazz);
        }catch (CannotCompileException e){
            System.out.println(src);
            throw new RuntimeException(e);
        }
    }

    private String compileIf(String statement){
        TokenHandler th = Lexer.toToken(statement);
        StringBuilder sb = new StringBuilder();

        th.assertToken("if");
        sb.append("if(").append(compileCalc(th)[1]).append("){");

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
        sb.append("while(").append(compileCalc(th)[1]).append("){");

        if(th.hasNext()){
            th.assertToken("->");
            th.assertHasNext();
            sb.append(compileStatement(th)).append("}");
        }

        return sb.toString();
    }

    private String compileReturn(TokenHandler th){
        th.assertToken("return");
        th.assertHasNext();
        return "return " + compileCalc(th)[1] + ";";
    }

    private String compileStatement(TokenHandler th){
        Token first = th.assertToken(Token.Type.IDENTIFIER);
        if(first.equals("return")){
            th.last();
            return compileReturn(th);
        }
        if(first.equals("{")){
            StringBuilder sb = new StringBuilder("{");
            sb.append(th.assertToken(Token.Type.IDENTIFIER));
            while (th.assertToken(",", "}").equals(","))
                sb.append(th.assertToken(Token.Type.IDENTIFIER));
            first = new Token(sb.toString(), Token.Type.IDENTIFIER);
        }
        if(th.next().equals(Token.Type.IDENTIFIER)){
            first = new Token(currentClass.aliases.get(first.s()), Token.Type.IDENTIFIER);
            currentMethod.parameterTypes().add(first.s(), first.s());
            if(!th.hasNext())
                return first + " " + th.current() + ";";
            Token second = th.current();
            th.assertToken("=");
            return first + " " + second + "=" + compileCalc(th)[1] + ";";
        }else if(th.current().equals("=")){
            th.assertHasNext();
            String[] calc = compileCalc(th);
            if(currentMethod.parameterTypes().getSecond(first.s()) == null && currentClass.parameter.getSecond(first.s()) == null && !currentClass.hasField(first.s())) {
                currentMethod.parameterTypes().add(first.s(), calc[0]);

                if(calc[0].equals("NUMBER"))
                    calc[0] = getPrimitiveType(calc[0]);

                if(calc[0].startsWith("[")){
                    int i = 0;
                    while (calc[0].startsWith("[")){
                        i++;
                        calc[0] = calc[0].substring(1);
                    }
                    while (i > 0){
                        i--;
                        calc[0] = calc[0] + "[]";
                    }
                }

                return calc[0] + " " + first + " = " + calc[1] + ";";
            }else
                return first + " = " + calc[1] + ";";
        }else{
            th.last();
            th.last();
            return compileCalc(th)[1] + ";";
        }
    }

    private String[] compileCalc(TokenHandler th){
        StringBuilder sb = new StringBuilder();
        String[] current = compileCalcArg(th);
        String type = current[0];
        sb.append(current[1]);

        while(th.hasNext()) {
            Token operator = th.assertToken(Token.Type.OPERATOR, Token.Type.SIMPLE);

            if(operator.equals(Token.Type.SIMPLE) || operator.equals("->")) {
                th.last();
                return new String[]{type, sb.toString()};
            }

            if(type == null)
                throw new RuntimeException("illegal argument in: " + th);

            current = compileCalcArg(th);
            String currentType = current[0];
            String currentArg = current[1];

            switch (type){
                case "short", "int", "long", "float", "double" -> {
                    if(!PRIMITIVE_NUMBER_OPERATORS.contains(operator.s()) || !Set.of("short", "int", "long", "float", "double").contains(currentType))
                        throw new RuntimeException("Operator " + operator + " is not defined for " + type + " and " + currentType + " in: " + th);
                    sb.append(operator).append(currentArg);
                }
                case "boolean" -> {
                    if(!PRIMITIVE_BOOLEAN_OPERATORS.contains(operator.s()) || !currentType.equals("boolean"))
                        throw new RuntimeException("Operator " + operator + " is not defined for boolean and " + currentType + " in: " + th);
                    if(operator.equals("|"))
                        operator = new Token("||", Token.Type.OPERATOR);
                    if(operator.equals("&"))
                        operator = new Token("&&", Token.Type.OPERATOR);
                    sb.append(operator).append(currentArg);
                }
                case "java/lang/String" -> {
                    if(!operator.equals("+") || !currentType.equals("java/lang/String"))
                        throw new RuntimeException("Operator " + operator + " is not defined for java/lang/String and " + currentType + " in: " + th);
                    sb.append("+").append(currentArg);
                }
                default -> {
                    if(!hasMethod(type, toIdentifier(operator.s()), currentType))
                        throw new RuntimeException("Operator " + operator + " (Method " + toIdentifier(operator.s()) + ") is not defined for " + type + " and " + currentType + " in: " + th);
                    sb.append(".").append(toIdentifier(operator.s())).append("(").append(currentArg).append(")");
                }
            }

            type = getReturnType(type, PRIMITIVES.contains(type) ? operator.s() : toIdentifier(operator.s()), currentType);
        }

        return new String[]{type, sb.toString()};
    }

    private String[] compileCalcArg(TokenHandler th){
        String arg;
        String type;

        switch (th.next().t()){
            case STRING -> {
                type = "java/lang/String";
                arg = th.current().s();
            }
            case NUMBER -> {
                type = getPrimitiveType(th.current().s());
                arg = th.current().s();
            }
            case SIMPLE -> {
                if(th.current().equals("(")){
                    String[] calc = compileCalc(th);
                    th.assertToken(")");
                    return calc;
                }else if(th.current().equals("[")){
                    StringBuilder typeBuilder = new StringBuilder("[");
                    StringBuilder argBuilder = new StringBuilder("new ");

                    type = th.assertToken(Token.Type.IDENTIFIER).s();
                    if(!PRIMITIVES.contains(type))
                        type = currentClass.aliases.get(type);

                    argBuilder.append(type).append("[");

                    th.assertToken(",");
                    argBuilder.append(th.assertToken(Token.Type.NUMBER)).append("]");

                    while(!th.assertToken("]", ",").equals("]")) {
                        typeBuilder.append("[");
                        argBuilder.append("[").append(th.assertToken(Token.Type.NUMBER)).append("]");
                    }

                    type = typeBuilder.append(type).toString();
                    arg = argBuilder.toString();
                }else throw new RuntimeException("illegal argument in: " + th);
            }
            case OPERATOR -> {
                throw new RuntimeException("not yet supported argument in: " + th); //TODO
            }
            case IDENTIFIER -> {
                String current = compileCurrent(th);
                type = current.split(" ", 2)[0];
                arg = current.split(" ", 2)[1];
            }
            default -> throw new RuntimeException("illegal argument in: " + th);
        }

        return new String[]{type, arg};
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
                    String[] calc = compileCalc(th);
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
                    sb.append(compileCalc(th)[1]);
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
                if(th.assertToken(Token.Type.IDENTIFIER, Token.Type.NUMBER).equals(Token.Type.NUMBER)){
                    if(!lastType.startsWith("["))
                        throw new RuntimeException("Expected IDENTIFIER got NUMBER in: " + th);
                    sb.append("[").append(th.current()).append("]");
                    if(lastType.split("\\[").length == 2)
                        lastType = lastType.substring(1);
                    else{
                        String[] sa = lastType.split("\\[");
                        lastType = "[".repeat(Math.max(0, sa.length - 1)) + sa[sa.length - 1];
                    }
                }else {
                    sb.append(".");
                    call = th.current().s();
                    sb.append(call);

                    if (!th.hasNext())
                        return getFieldType(lastType, call) + " " + sb;

                    switch (th.next().s()) {
                        case ":" -> {
                            th.last();
                            lastType = getFieldType(lastType, call);
                        }
                        case "(" -> {
                            sb.append("(");
                            th.assertHasNext();
                            ArrayList<String> types = new ArrayList<>();
                            while (th.hasNext()) {
                                if (th.next().equals(")")) {
                                    sb.append(")");
                                    break;
                                }
                                th.last();
                                String[] calc = compileCalc(th);
                                types.add(calc[0]);
                                sb.append(calc[1]);
                                if (th.assertToken(",", ")").equals(")")) {
                                    sb.append(")");
                                    break;
                                } else {
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
                }
            }else{
                th.last();
                break;
            }
        }

        return lastType + " " + sb;
    }

    private String primitiveToObject(String value){
        return "new " + WRAPPER_CLASSES.getSecond(getPrimitiveType(value)) + "(" + value + ")";
    }

    private String getType(String clazz, String method, String var){
        if(var == null){
            if(classes.get(clazz) instanceof XJLNClass && ((XJLNClass) classes.get(clazz)).methods.containsKey(method)){
                return ((XJLNClass) classes.get(clazz)).methods.get(method).returnType();
            }else
                throw new RuntimeException("illegal argument");
        }else if(classes.get(clazz) instanceof XJLNClass){
            if(method != null && ((XJLNClass) classes.get(clazz)).methods.get(method).parameterTypes().getSecond(var) != null)
                return ((XJLNClass) classes.get(clazz)).methods.get(method).parameterTypes().getSecond(var);
            if(((XJLNClass) classes.get(clazz)).hasField(var))
                try {
                    return ((XJLNClass) classes.get(clazz)).getField(var).type();
                }catch (NoSuchFieldException ignored){}
            if(((XJLNClass) classes.get(clazz)).parameter.getSecond(var) != null)
                return ((XJLNClass) classes.get(clazz)).parameter.getSecond(var).type;
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
                    return ((XJLNClass) classes.get(clazz)).methods.get(method).returnType();
                }
            }
            case "unknown" -> {
                return null;
            }
            case "primitive" -> {
                if(method.contains("="))
                    return "boolean";
                return clazz;
            }
        }
        return null;
    }

    private String getFieldType(String clazz, String name){
        if(clazz.startsWith("[")){
            if(!name.equals("length"))
                throw new RuntimeException(new NoSuchFieldException());
            return "int";
        }
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
                    if(((XJLNClass) classes.get(clazz)).hasField(name))
                        try {
                            return ((XJLNClass) classes.get(clazz)).getField(name).type();
                        }catch (NoSuchFieldException ignored){}
                    if(((XJLNClass) classes.get(clazz)).parameter.getSecond(name) != null)
                        return ((XJLNClass) classes.get(clazz)).parameter.getSecond(name).type;
                    return null;
                }
            }
            case "unknown" -> {
                return null;
            }
            case "primitive" -> throw new RuntimeException(new NoSuchFieldException());
        }
        return null;
    }

    private void executeMain(String path){
        path = path.replace("/", ".").replace("\\", ".");
        if(!classes.containsKey(path + ".Main"))
            throw new RuntimeException("file " + path + ".Main does not exist");
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

    public static Compilable getXJLNClass(String validName){
        return classes.get(validName);
    }

    public static boolean hasMethod(String clazz, String method, String...types){
        switch (getClassLang(clazz)){
            case "xjln" -> {
                if(classes.get(clazz) instanceof XJLNClass)
                    return ((XJLNClass) classes.get(clazz)).methods.containsKey(method);
            }
            case "java" -> {
                try {
                    Class<?>[] classTypes = new Class[types.length];
                    for(int i = 0;i < types.length;i++)
                        classTypes[i] = Class.forName(types[i]);
                    Class.forName(clazz).getMethod(method, classTypes);
                    return true;
                }catch (ClassNotFoundException | NoSuchMethodException ignored){}
            }
        }
        throw new RuntimeException("Class " + clazz + " didn't exist");
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
