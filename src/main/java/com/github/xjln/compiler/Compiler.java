package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

import javassist.*;
import javassist.bytecode.*;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Compiler {

    public static final MatchedList<String, String> OPERATOR_LIST = MatchedList.of(
            new String[]{"+"  , "-"       , "*"       , "/"     , "="     , "<"       , ">"          , "!"  , "%"     , "&"  , "|" },
            new String[]{"add", "subtract", "multiply", "divide", "equals", "lessThan", "greaterThan", "not", "modulo", "and", "or"});

    public static final MatchedList<String, String> WRAPPER_CLASSES = MatchedList.of(
            new String[]{"var"                        , "int"              , "double"          , "long"          , "float"          , "boolean"          , "char"               , "byte"          , "short"},
            new String[]{"com.github.xjln.utility.Var", "java.lang.Integer", "java.lang.Double", "java.lang.Long", "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short"});

    public  static final Set<String> PRIMITIVES                  = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
    private static final Set<String> PRIMITIVE_NUMBER_OPERATORS  = Set.of("+", "-", "*", "/", "!=", "==", ">=", "<=", "<", ">", "%", "=");
    private static final Set<String> PRIMITIVE_BOOLEAN_OPERATORS = Set.of("==", "!=", "=", "&&", "||");

    private static String[] srcFolders = new String[0];
    private static HashMap<String, Compilable> classes;

    private final Parser parser;

    private XJLNClassStatic currentClass;
    private XJLNMethodAbstract currentMethod;
    private CompilingMethod compilingMethod;

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

    private void validateFolders() throws RuntimeException{
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

    private void clearFolder(File folder, boolean delete) throws RuntimeException{
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
            else if(compilable instanceof XJLNClass && ((XJLNClass) compilable).isDataClass)
                compileDataClass((XJLNClass) compilable);
            else if(compilable instanceof XJLNClassStatic)
                compileClassFirstIteration((XJLNClassStatic) compilable);
        }

        for(Compilable clazz: classes.values())
            if(clazz instanceof XJLNClassStatic)
                compileClassSecondIteration((XJLNClassStatic) clazz);
    }

    private void writeFile(ClassFile cf){
        ClassPool cp = ClassPool.getDefault();
        writeFile(cp.makeClass(cf));
    }

    private void writeFile(CtClass ct) throws RuntimeException{
        try {
            ct.writeFile("compiled");
        }catch(CannotCompileException | IOException e){
            throw new RuntimeException("Failed to write output File", e);
        }
    }

    private void compileInterface(XJLNInterface xjlnInterface) throws RuntimeException{
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

    private void compileDataClass(XJLNClass xjlnClass){
        ClassFile cf = new ClassFile(false, xjlnClass.name, null);

        for(XJLNParameter parameter:xjlnClass.parameter.getValueList()){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), parameter.name(), toDesc(parameter.type()));
            fInfo.setAccessFlags(parameter.constant() ? AccessFlag.setPublic(AccessFlag.FINAL) : AccessFlag.PUBLIC);
            cf.addField2(fInfo);
        }

        cf.addMethod2(compileDefaultInit(xjlnClass, cf.getConstPool()));

        writeFile(cf);
    }

    private MethodInfo compileDefaultInit(XJLNClass clazz, ConstPool cp){
        MethodInfo mInfo = new MethodInfo(cp, "<init>", toDesc(clazz.generateDefaultInit()));
        mInfo.setAccessFlags(AccessFlag.PUBLIC);

        Bytecode code = new Bytecode(cp);
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");

        int i = 0;

        for(XJLNParameter parameter:clazz.parameter.getValueList()){
            code.addAload(0);
            i += 1;

            switch(parameter.type()){
                case "int", "byte", "char", "short", "boolean" -> code.addIload(i);
                case "double" -> code.addDload(i);
                case "long" -> code.addLload(i);
                case "float" -> code.addFload(i);
                default -> code.addAload(i);
            }

            code.addPutfield(clazz.name, parameter.name(), toDesc(parameter.type()));
        }

        code.addReturn(null);

        mInfo.setCodeAttribute(code.toCodeAttribute());

        return mInfo;
    }

    private void compileClassFirstIteration(XJLNClassStatic clazz){
        if(clazz.name.endsWith(".Main") && clazz.isEmpty()) {
            classes.remove(clazz.name);
            return;
        }
        currentClass = clazz;

        ClassFile cf = new ClassFile(false, clazz.name, null);

        //static Fields
        compileFields(cf, clazz.getStaticFields(), true);

        //non-static Fields
        if(clazz instanceof XJLNClass){
            ((XJLNClass) clazz).createParameterFields();
            compileFields(cf, ((XJLNClass) clazz).getFields(), false);
        }

        //static Methods
        compileMethods(cf, clazz.getStaticMethods(), true);

        //non-static Methods
        if(clazz instanceof XJLNClass)
            compileMethods(cf, ((XJLNClass) clazz).getMethods(), false);

        //Constructor
        if(clazz instanceof XJLNClass) {
            MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<init>", toDesc(((XJLNClass) clazz).generateDefaultInit()));
            mInfo.setAccessFlags(AccessFlag.PUBLIC);
            cf.addMethod2(mInfo);
        }

        currentClass = null;
        ClassPool.getDefault().makeClass(cf);
    }

    private void compileClassSecondIteration(XJLNClassStatic clazz) throws RuntimeException{
        currentClass = clazz;
        CtClass ct;

        try {
            ct = ClassPool.getDefault().get(clazz.name);
        }catch (NotFoundException e){
            throw new RuntimeException(e);
        }

        //static Methods
        for(String method:clazz.getStaticMethods().keySet()){
            currentMethod = clazz.getStaticMethods().get(method);

            try {
                ct.removeMethod(ct.getMethod(currentMethod.name, toDesc(currentMethod)));
                ct.addMethod(CtMethod.make(compileMethod(true), ct));
            }catch(NotFoundException ignore){
                throw new RuntimeException("internal Compiler error");
            }catch(CannotCompileException e){
                throw new RuntimeException(e);
            }

            currentMethod = null;
        }

        //non-static Methods
        if(clazz instanceof XJLNClass) {
            for (String method : ((XJLNClass) clazz).getMethods().keySet()) {
                currentMethod = ((XJLNClass) clazz).getMethods().get(method);

                if(!currentMethod.name.equals("init")) {
                    try {
                        ct.removeMethod(ct.getMethod(currentMethod.name, toDesc(currentMethod)));
                        ct.addMethod(CtMethod.make(compileMethod(false), ct));
                    } catch (NotFoundException ignore) {
                        throw new RuntimeException("internal Compiler error");
                    } catch (CannotCompileException e) {
                        throw new RuntimeException(e);
                    }
                }else{
                    try {
                        ct.removeMethod(ct.getMethod("<init>", toDesc(currentMethod)));
                        ct.addConstructor(CtNewConstructor.make(compileMethod(false), ct));
                    } catch (NotFoundException ignore) {
                        throw new RuntimeException("internal Compiler error");
                    } catch (CannotCompileException e) {
                        throw new RuntimeException(e);
                    }
                }

                currentMethod = null;
            }

            if(!((XJLNClass) clazz).getMethods().containsKey("init")){
                try{
                    currentMethod = ((XJLNClass) clazz).generateDefaultInit();
                    ct.removeConstructor(ct.getConstructor(toDesc(currentMethod)));
                    ct.addConstructor(CtNewConstructor.make(compileMethod(false), ct));
                }catch (CannotCompileException e){
                    throw new RuntimeException(e);
                } catch (NotFoundException ignored) {
                    throw new RuntimeException("internal Compiler error");
                }
            }
        }

        currentClass = null;
        writeFile(ct);
    }

    private void compileFields(ClassFile cf, HashMap<String, XJLNField> fields, boolean statik) throws RuntimeException{
        ConstPool cp = cf.getConstPool();

        for(String fieldName : fields.keySet()){
            if(!fieldName.equals("this")) {
                XJLNField field = fields.get(fieldName);
                FieldInfo fInfo = new FieldInfo(cp, fieldName, toDesc(field.type()));
                fInfo.setAccessFlags(accessFlag(field.inner(), field.constant(), statik, false));

                try {
                    cf.addField(fInfo);
                } catch (DuplicateMemberException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void compileMethods(ClassFile cf, HashMap<String, ? extends XJLNMethodAbstract> methods, boolean statik) throws RuntimeException{
        ConstPool cp = cf.getConstPool();

        for(String methodName : methods.keySet()){
            XJLNMethodAbstract method = methods.get(methodName);
            MethodInfo mInfo = new MethodInfo(cp, method.name, toDesc(method));
            mInfo.setAccessFlags(accessFlag(method.inner, false, statik, !(method instanceof XJLNMethod)));

            try {
                cf.addMethod(mInfo);
            }catch (DuplicateMemberException e){
                throw new RuntimeException(e);
            }
        }
    }

    private String compileMethod(boolean statik) throws RuntimeException{
        StringBuilder result = new StringBuilder();
        boolean isConstructor = false;
        boolean isAbstract = !(currentMethod instanceof XJLNMethod);

        if(currentMethod.name.equals("main"))
            result.append("public static void main(String[] args){\n");
        else{
            if(currentMethod.name.equals("init")){
                isConstructor = true;
                result.append("public ").append(currentClass.name.split("\\.")[currentClass.name.split("\\.").length - 1]).append("(");
            }else {
                result.append(currentMethod.inner ? "private " : "public ");

                if (statik)
                    result.append("static ");
                if (isAbstract)
                    result.append("abstract ");

                result.append(validateType(currentMethod.returnType)).append(" ").append(currentMethod.name).append("(");
            }

            for(XJLNParameter p:currentMethod.parameterTypes.getValueList())
                result.append(validateType(p.type())).append(" ").append(p.name()).append(",");

            if(!result.toString().endsWith("("))
                result.deleteCharAt(result.length() - 1);

            if(isAbstract)
                return result.append(");").toString();

            result.append("){\n");
        }

        if(isConstructor){
            assert currentClass instanceof XJLNClass;

            for(XJLNParameter p:((XJLNClass) currentClass).parameter.getValueList())
                result.append("this.").append(p.name()).append(" = ").append(p.name()).append(";");
        }

        assert currentMethod instanceof XJLNMethod;
        compilingMethod = new CompilingMethod((XJLNMethod) currentMethod);
        result.append(compileCode());
        compilingMethod = null;

        return result.append("}\n").toString();
    }

    private String compileCode(){
        StringBuilder code = new StringBuilder();
        assert currentMethod instanceof XJLNMethod;

        while (compilingMethod.hasNextLine()) {
            if(compilingMethod.nextLine().equals("end") || compilingMethod.currentLine().equals("else"))
                return code.toString();
            else
                code.append(compileStatement(Lexer.toToken(compilingMethod.currentLine())));
        }

        return code.toString();
    }

    private String compileStatement(TokenHandler th){
        return switch (th.next().s()){
            case "if" -> compileIf(th);
            case "while" -> compileWhile(th);
            case "for" -> compileFor(th);

            case "return", "throw" -> {
                Token first = th.current();
                String[] calc = compileCalc(th);

                if(first.equals("return") && !calc[1].equals(currentMethod.returnType))
                    throw new RuntimeException("Expected Type " + currentMethod.returnType + " got " + calc[1] + " in " + th);

                yield first + " " + calc[0] + ";\n";
            }

            default -> {
                th.last();
                Token first = th.assertToken(Token.Type.IDENTIFIER);
                if(th.next().t() == Token.Type.IDENTIFIER){
                    if (compilingMethod.scope().varExist(first.s()))
                        throw new RuntimeException("Variable " + first.s() + " is already defined in Method " + toCompilerDesc(currentMethod) + " in Class " + currentClass.name);

                    if(th.hasNext()){
                        Token second = th.current();
                        th.assertToken("=");
                        th.assertHasNext();

                        String[] calc = compileCalc(th);

                        if(!calc[1].equals(first.s()))
                            throw new RuntimeException("Type " + calc[1] + " is not allowed for variable " + second.s() + " in Method " + toCompilerDesc(currentMethod) + " in Class " + currentClass.name);

                        compilingMethod.scope().add(second.s(), first.s());

                        yield first.s() + " " + second.s() + "=" + calc[0] + ";\n";
                    }else{
                        compilingMethod.scope().add(first.s(), th.current().s());
                        yield first.s() + " " + th.current().s() + ";\n";
                    }
                }else if(th.current().equals("=")){
                    String[] calc = compileCalc(th);

                    if(compilingMethod.scope().varExist(first.s())){
                        if(!compilingMethod.scope().getType(first.s()).equals(calc[1]))
                            throw new RuntimeException("Type " + calc[1] + " is not allowed for variable" + first.s() + " in Method " + toCompilerDesc(currentMethod) + " of Class " + currentClass.name);

                        yield first.s() + " = " + calc[0] + ";\n";
                    }

                    compilingMethod.scope().add(first.s(), calc[1]);
                    yield calc[1] + " " + first.s() + " = " + calc[0] + ";\n";
                }else{
                    th.last();
                    th.last();
                    yield compileCall(th)[1] + ";\n";
                }
            }
        };
    }

    private String compileIf(TokenHandler th){
        String[] calc = compileCalc(th);

        if(!calc[0].equals("boolean") && !calc[0].equals("java/lang/Boolean"))
            throw new RuntimeException("expected boolean in " + th);

        String code;
        compilingMethod.newScope();

        if(!th.hasNext())
            code =  "if(" + calc[1] + "){\n" + compileCode() + "}\n";
        else {
            th.assertToken("->");
            code = "if(" + calc[1] + "){\n" + compileStatement(th) + "}\n";
        }

        //TODO ifs else case

        compilingMethod.lastScope();
        return code;
    }

    private String compileWhile(TokenHandler th){
        String[] calc = compileCalc(th);

        if(!calc[0].equals("boolean") && !calc[0].equals("java/lang/Boolean"))
            throw new RuntimeException("expected boolean in " + th);

        String code;
        compilingMethod.newScope();

        if(!th.hasNext())
            code =  "while(" + calc[1] + "){\n" + compileCode() + "}\n";
        else {
            th.assertToken("->");
            code = "while(" + calc[1] + "){\n" + compileStatement(th) + "}\n";
        }

        compilingMethod.lastScope();
        return code;
    }

    private String compileFor(TokenHandler th){
        return null;//TODO for loop
    }

    private String[] compileCalc(TokenHandler th){
        th.assertHasNext();
        String[] current = compileCalcArg(th);

        StringBuilder arg = new StringBuilder(current[0]);
        String type = current[1];

        while (th.hasNext()){
            if(!th.next().equals(Token.Type.OPERATOR)) {
                th.last();
                return new String[]{arg.toString(), type};
            }

            Token operator = th.current();
            current = compileCalcArg(th);

            if(PRIMITIVES.contains(type)){
                operator = new Token(toJavaOperator(operator.s()), Token.Type.OPERATOR);
                switch(type){
                    case "boolean" -> {
                        if(!current[1].equals("boolean"))
                            throw new RuntimeException("expected type boolean in: " + th);
                        if(!PRIMITIVE_BOOLEAN_OPERATORS.contains(operator.s()))
                            throw new RuntimeException("operator " + operator + " is not defined for type boolean and boolean");

                        arg.append(" ").append(operator).append(" ").append(current[0]);
                    }

                    case "int", "double", "short", "long", "float" -> {
                        if(!Set.of("int", "double", "short", "long", "float").contains(current[1]))
                            throw new RuntimeException("expected type " + type + " in: " + th);
                        if(!PRIMITIVE_NUMBER_OPERATORS.contains(operator.s()))
                            throw new RuntimeException("operator " + operator + " is not defined for type " + type + " and " + current[1]);

                        arg.append(" ").append(operator).append(" ").append(current[0]);
                    }

                    default -> throw new RuntimeException("not yet implemented"); //TODO
                }
            }else{
                String returnType = getMethodReturnType(type, toIdentifier(operator.s()), current[1]);

                if(returnType == null)
                    throw new RuntimeException("Operator " + operator + " is not defined for " + type + " and " + current[1]);

                arg.append(".").append(toIdentifier(operator.s())).append("(").append(current[0]).append(")");

                type = returnType;
            }
        }

        return new String[]{arg.toString(), type};
    }

    private String[] compileCalcArg(TokenHandler th){
        String arg;
        String type;

        switch (th.next().t()){
            case CHAR -> {
                type = "char";
                arg = th.current().s();
            }
            case STRING -> {
                type = "java/lang/String";
                arg = th.current().s();
            }
            case NUMBER -> {
                type = getPrimitiveType(th.current().s());
                arg = th.current().s();
            }
            case SIMPLE -> {
                switch (th.current().s()){
                    case "(" -> {
                        String[] result = compileCalc(th);
                        th.assertToken(")");
                        arg = result[0];
                        type = result[1];
                    }
                    case "{" -> {
                        th.last();
                        String[] result = compileCall(th);
                        arg = result[0];
                        type = result[1];
                    }
                    default -> throw new RuntimeException("illegal argument in " + th);
                }
            }
            case IDENTIFIER -> {
                th.last();
                String[] result = compileCall(th);
                arg = result[0];
                type = result[1];
            }
            default -> throw new RuntimeException("illegal argument in " + th); //TODO operator calling
        }

        return new String[]{arg, type};
    }

    private String[] compileCall(TokenHandler th){
        StringBuilder arg = new StringBuilder();
        String type = currentClass.name;

        Token identifier = th.current();

        if(th.hasNext() && Set.of("{", ":", "[", "(").contains(th.next().s())){
            if(identifier.equals("{")){
                //TODO array initialisation in calculations
            }else{
                switch (th.current().s()) {
                    case ":" -> th.last();
                    case "[" -> arg.append("new ").append(validateType(identifier.s())).append("(").append(th.getInBracket()).append(")");
                    case "(" -> {
                        String[] parameter = compileParameterList(th.getInBracket());
                        arg.append(identifier.s()).append("(").append(parameter[0]).append(")");

                        String returnType = getMethodReturnType(type, identifier.s(), Arrays.copyOfRange(parameter, 1, parameter.length));

                        if(returnType == null)
                            throw new RuntimeException("Method " + identifier + " is not defined for Class " + type);

                        type = returnType;
                    }
                }
            }

            while (th.hasNext()){
                if(!th.next().equals(":")){
                    th.last();
                    break;
                }

                identifier = th.assertToken(Token.Type.IDENTIFIER);

                if(th.hasNext()){
                    if(th.next().equals("(")){
                        String[] parameter = compileParameterList(th.getInBracket());
                        arg.append(identifier.s()).append("(").append(parameter[0]).append(")");

                        String returnType = getMethodReturnType(type, identifier.s(), Arrays.copyOfRange(parameter, 1, parameter.length));

                        if(returnType == null)
                            throw new RuntimeException("Method " + identifier + " is not defined for Class " + type);

                        type = returnType;
                    }else{
                        th.last();
                        String fieldType = getFieldType(type, th.current().s());

                        arg.append(".").append(th.current().s());

                        if(fieldType == null)
                            throw new RuntimeException("Field " + th.current() + " is not defined in Class " + type);

                        type = fieldType;
                    }
                }
            }
        }else{
            th.last();

            if(identifier.equals("{"))
                throw new RuntimeException("illegal argument in: " + th);

            if(!currentMethod.statik){
                assert currentClass instanceof XJLNClass;

                if(currentClass.getStaticFields().get(identifier.s()) != null)
                    return new String[]{identifier.s(), currentClass.getStaticFields().get(identifier.s()).type()};
            }

            if(currentClass.getStaticFields().get(identifier.s()) != null){
                arg.append(identifier.s());
                type = currentClass.getStaticFields().get(identifier.s()).type();
            }else throw new RuntimeException("Field " + identifier + " does not exist in: " + th);
        }

        return new String[]{arg.toString(), type};
    }

    private String[] compileParameterList(TokenHandler th){
        StringBuilder arg = new StringBuilder();
        ArrayList<String> result = new ArrayList<>();

        while (th.hasNext()){
            String[] calc = compileCalc(th);

            result.add(calc[1]);
            arg.append(calc[0]);

            arg.append(",");
        }

        if(arg.charAt(arg.length() - 1) == ',')
            arg.deleteCharAt(arg.length() - 1);

        result.add(0, arg.toString());

        return result.toArray(new String[0]);
    }

    private String getMethodReturnType(String clazz, String method, String...parameterTypes){
        String classLang = getClassLang(clazz);

        if(classLang == null)
            return null;

        return switch (classLang){
            case "XJLN" -> {
                String desc = toCompilerDesc(method, parameterTypes);

                if(classes.get(clazz) instanceof XJLNClassStatic xjlnClassStatic){
                    if(xjlnClassStatic.getStaticMethods().containsKey(desc))
                        yield xjlnClassStatic.getStaticMethods().get(desc).returnType;

                    if(xjlnClassStatic instanceof XJLNClass && ((XJLNClass) xjlnClassStatic).getMethods().containsKey(desc))
                        yield ((XJLNClass) xjlnClassStatic).getMethods().get(desc).returnType;

                    yield null;
                }else if(classes.get(clazz) instanceof XJLNInterface xjlnInterface)
                    yield xjlnInterface.methods().containsKey(desc) ? xjlnInterface.methods().get(desc).returnType : null;
                else yield null;
            }
            case "JAVA" -> {
                try {
                    Class<?> javaClass = Class.forName(clazz);

                    for(Method m:javaClass.getMethods()){
                        boolean matches = true;

                        if(parameterTypes.length == m.getParameterTypes().length) {
                            for (int i = 0; i < parameterTypes.length; i++) {
                                if (parameterTypes[i].equals(m.getParameterTypes()[i].toString().split(" ")[1])) {
                                    matches = false;
                                    break;
                                }
                            }
                        }else
                            matches = false;

                        if(matches && !m.getName().equals(method))
                            matches = false;

                        if(matches)
                            yield m.getReturnType().toString().split(" ")[1];
                    }

                    yield null;
                } catch (ClassNotFoundException ignored) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String getFieldType(String clazz, String field){
        String classLang = getClassLang(clazz);

        if(classLang == null)
            return null;

        return switch (classLang){
            case "XJLN" -> {
                if(classes.get(clazz) instanceof XJLNClassStatic classStatic){
                    if(classStatic.getStaticFields().containsKey(field))
                        yield classStatic.getStaticFields().get(field).type();

                    if(classStatic instanceof XJLNClass xjlnClass && xjlnClass.getFields().containsKey(field))
                        yield xjlnClass.getFields().get(field).type();

                    yield null;
                }else yield null;
            }
            case "JAVA" -> {
                try {
                    yield Class.forName(clazz).getField(field).getType().toString().split(" ")[1];
                } catch (ClassNotFoundException | NoSuchFieldException ignored) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private String getClassLang(String clazz){
        if(PRIMITIVES.contains(clazz))
            return "PRIMITIVE";
        if(classes.containsKey(clazz))
            return "XJLN";
        try{
            Class.forName(clazz);
            return "JAVA";
        }catch (ClassNotFoundException ignored){}
        try{
            ClassPool.getDefault().get(clazz);
            return "UNKNOWN";
        }catch (NotFoundException ignored){}
        return null;
    }

    private String validateType(String type) throws RuntimeException{
        if(PRIMITIVES.contains(type) || type.equals("void"))
            return type;

        if(currentClass instanceof XJLNClass && currentClass.isGeneric(type))
            return "java/lang/Object";

        if(!currentClass.aliases.containsKey(type))
            throw new RuntimeException("illegal Type " + type);

        return currentClass.aliases.get(type);
    }

    private int accessFlag(boolean inner, boolean constant, boolean statik, boolean abstrakt){
        int accessFlag = inner ? AccessFlag.PRIVATE : AccessFlag.PUBLIC;

        if(constant)
            accessFlag += AccessFlag.FINAL;
        if(statik)
            accessFlag += AccessFlag.STATIC;
        if(abstrakt)
            accessFlag += AccessFlag.ABSTRACT;

        return accessFlag;
    }

    private String toJavaOperator(String primitiveOperator){
        return switch (primitiveOperator){
            case "|" -> "||";
            case "&" -> "&&";
            default -> primitiveOperator;
        };
    }

    private String toDesc(String type){
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

    private String toDesc(XJLNMethodAbstract method){
        StringBuilder desc = new StringBuilder();

        desc.append("(");

        for(XJLNParameter parameter:method.parameterTypes.getValueList())
            desc.append(toDesc(validateType(parameter.type())));

        desc.append(")").append(toDesc(validateType(method.returnType)));

        return desc.toString();
    }

    public static String toCompilerDesc(XJLNMethodAbstract method){
        StringBuilder desc = new StringBuilder();
        desc.append("(");
        if(method.parameterTypes != null)
            for(XJLNParameter p:method.parameterTypes.getValueList())
                desc.append(p.type()).append(",");
        if(!desc.toString().endsWith("("))
            desc.deleteCharAt(desc.length() - 1);
        desc.append(")").append(method.name);
        return desc.toString();
    }

    public String toCompilerDesc(String name, String[] types){
        StringBuilder desc = new StringBuilder();

        desc.append("(");
        if(types != null)
            for(String type:types)
                desc.append(type).append(",");
        if(!desc.toString().endsWith("("))
            desc.deleteCharAt(desc.length() - 2);
        desc.append(")").append(name);

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
                Double.parseDouble(value);
                return "double";
            }catch (NumberFormatException ignored){}
            /*try{
                Float.parseFloat(value);
                return "float";
            }catch (NumberFormatException ignored){}*/
        }else{
            /*try{
                Short.parseShort(value);
                return "short";
            }catch (NumberFormatException ignored){}*/
            try{
                Integer.parseInt(value);
                return "int";
            }catch (NumberFormatException ignored){}
            /*try{
                Long.parseLong(value);
                return "long";
            }catch (NumberFormatException ignored){}*/
        }
        throw new RuntimeException("internal compiler error");
    }

    public static Compilable getClass(String name){
        return classes.get(name);
    }
}
