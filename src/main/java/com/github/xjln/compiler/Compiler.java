package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

import javassist.*;
import javassist.bytecode.*;

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

    private XJLNClassStatic currentClass;
    private XJLNMethodAbstract currentMethod;

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
                currentMethod = clazz.getStaticMethods().get(method);

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

        if(currentMethod.name.equals("main")){
            if(!currentClass.name.endsWith(".Main"))
                throw new RuntimeException("Method main is not allowed in Class " + currentClass.name);

            result.append("public static void main(String[] args){");
        }else{
            if(currentMethod.name.equals("init")){
                if(currentClass.name.endsWith(".Main"))
                    throw new RuntimeException("Method init is not allowed in Class " + currentClass.name);

                isConstructor = true;
                result.append("public ").append(currentClass.name.split("\\.")[currentClass.name.split("\\.").length - 1]).append("(");
            }else {
                result.append(currentMethod.inner ? "private " : "public ");

                if (statik)
                    result.append("static ");

                result.append(validateType(currentMethod.returnType)).append(" ").append(currentMethod.name).append("(");
            }

            for(XJLNParameter p:currentMethod.parameterTypes.getValueList())
                result.append(validateType(p.type())).append(" ").append(p.name()).append(",");

            if(!result.toString().endsWith("("))
                result.deleteCharAt(result.length() - 1);

            result.append("){");
        }

        if(isConstructor){
            assert currentClass instanceof XJLNClass;

            for(XJLNParameter p:((XJLNClass) currentClass).parameter.getValueList())
                result.append("this.").append(p.name()).append(" = ").append(p.name()).append(";");
        }

        return result.append("}").toString();
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
