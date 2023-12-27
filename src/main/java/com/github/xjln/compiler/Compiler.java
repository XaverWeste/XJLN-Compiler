package com.github.xjln.compiler;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.bytecode.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Set;

public final class Compiler {

    private static final MatchedList<String, String> OPERATOR_LIST = MatchedList.of(
            new String[]{"+"  , "-"       , "*"       , "/"     , "="     , "<"       , ">"          , "!"  , "%"     , "&"  , "|" },
            new String[]{"add", "subtract", "multiply", "divide", "equals", "lessThan", "greaterThan", "not", "modulo", "and", "or"});

    private static final MatchedList<String, String> WRAPPER_CLASSES = MatchedList.of(
            new String[]{"var"                        , "int"              , "double"          , "long"          , "float"          , "boolean"          , "char"               , "byte"          , "short"},
            new String[]{"com.github.xjln.utility.Var", "java.lang.Integer", "java.lang.Double", "java.lang.Long", "java.lang.Float", "java.lang.Boolean", "java.lang.Character", "java.lang.Byte", "java.lang.Short"});

    public static final Set<String> PRIMITIVES = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");

    private static boolean debug;

    private final HashMap<String, XJLNFile> files = new HashMap<>();
    private final SyntacticParser syntacticParser = new SyntacticParser();
    private final Parser parser = new Parser();

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
            compileClass(srcFolders);
            //TODO run main
        }
    }

    /**
     * compiles all .xjln Files in the given Folders. No Main Method will be executed
     * @param enableDebugInformation if information of the compilation process should be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(boolean enableDebugInformation, String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = enableDebugInformation;
            compileClass(srcFolders);
        }
    }

    /**
     * compiles all .xjln Files in the given Folders. No Main Method will be executed, no information of the compilation process will be shown
     * @param srcFolders the folders to compile
     * @throws RuntimeException if there are errors within the .xjln Files
     */
    public Compiler(String... srcFolders) throws RuntimeException{
        if(srcFolders.length > 0) {
            debug = false;
            compileClass(srcFolders);
        }
    }

    private void compileClass(String[] srcFolders){
        validateFolders(srcFolders);

        for(String folder:srcFolders)
            parseFolder(new File(folder));

        printDebug("parsing finished successfully");

        compileFiles();

        System.out.println("\nFinished compilation process successfully\n");
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

            printDebug("output Folder has been cleared");
        }
    }

    private void clearFolder(File folder, boolean delete){
        for(File file: Objects.requireNonNull(folder.listFiles())){
            if(file.isDirectory())
                clearFolder(file, true);
            else if(!file.delete())
                throw new RuntimeException("failed to delete " + file.getPath());
        }

        if(delete){
            if(Objects.requireNonNull(folder.listFiles()).length != 0)
                throw new RuntimeException("failed to delete files in " + folder.getPath());

            if(!folder.delete())
                throw new RuntimeException("failed to delete " + folder.getPath());
        }
    }

    private void parseFolder(File folder){
        for(File file: Objects.requireNonNull(folder.listFiles())) {
            if (file.isDirectory())
                parseFolder(file);
            else
                parseFile(file);
        }
    }

    private void parseFile(File file){
        if(file.getName().endsWith(".xjln")) {
            try {
                XJLNFile xjlnFile = parser.parseFile(file);
                if(xjlnFile != null)
                    files.put(file.getPath().substring(0, file.getPath().length() - 5).replace("\\", "."), xjlnFile);
            } catch (FileNotFoundException ignored) {
                throw new RuntimeException("Unable to access " + file.getPath());
            }
        }
    }

    private void compileFiles(){
        for(String path: files.keySet()){
            XJLNFile file = files.get(path);

            if(!file.main.isEmpty())
                compileClass(file.main, "Main", path);

            for(String name:file.classes.keySet()){
                Compilable c = file.classes.get(name);

                if(c instanceof XJLNTypeClass)
                    compileType((XJLNTypeClass) c, name, path);
                else if(c instanceof XJLNDataClass)
                    compileData((XJLNDataClass) c, name, path);
                else if(c instanceof XJLNInterface)
                    compileInterface((XJLNInterface) c, name, path);
                else if(c instanceof XJLNClass)
                    compileClass((XJLNClass) c, name, path);
            }
        }
    }

    private void compileType(XJLNTypeClass type, String name, String path){
        ClassFile cf = new ClassFile(false, path + "." + name, "java.lang.Enum");
        cf.setAccessFlags(type.getAccessFlag());

        //Types
        for(String value: type.values){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), value, "L" + name + ";");
            fInfo.setAccessFlags(0x4019);
            cf.addField2(fInfo);
        }

        //$VALUES
        FieldInfo fInfo = new FieldInfo(cf.getConstPool(), "$VALUES", "[L" + name + ";");
        fInfo.setAccessFlags(0x101A);
        cf.addField2(fInfo);

        //values()
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "values","()[L" + name + ";");
        mInfo.setAccessFlags(0x9);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addGetstatic(name, "$VALUES", "[L" + name + ";");
        code.addInvokevirtual("[L" + name + ";", "clone","()[Ljava.lang.Object;");
        code.addCheckcast("[L" + name + ";");
        code.add(0xb0); //areturn
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //valueOf
        mInfo = new MethodInfo(cf.getConstPool(), "valueOf", "(Ljava/lang/String;)L" + name + ";");
        mInfo.setAccessFlags(0x9);
        code = new Bytecode(cf.getConstPool());
        code.addLdc("L" + name + ";.class");
        code.addAload(0);
        code.addInvokestatic("java/lang/Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
        code.addCheckcast(name);
        code.add(0xb0); //areturn
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //<inti>
        mInfo = new MethodInfo(cf.getConstPool(), "<init>", "(Ljava/lang/String;I)V");
        mInfo.setAccessFlags(0x2);
        code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addAload(1);
        code.addIload(2);
        code.addInvokespecial("java/lang/Enum", "<init>", "(Ljava/lang/String;I)V");
        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //$values
        mInfo = new MethodInfo(cf.getConstPool(), "$values", "()[L" + name + ";");
        mInfo.setAccessFlags(0x100A);
        code = new Bytecode(cf.getConstPool());
        code.addIconst(type.values.length);
        code.addAnewarray(name);
        for(int i = 0;i < type.values.length;i++) {
            code.add(0x59); //dup
            code.addIconst(i);
            code.addGetstatic(name, type.values[i], "L" + name + ";");
            code.add(0x53); //aastore
        }
        code.add(0xb0); //areturn
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //<clinit>
        mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(0x8);
        code = new Bytecode(cf.getConstPool());
        for(int i = 0;i < type.values.length;i++) {
            code.addNew(name);
            code.add(0x59); //dup
            code.addLdc(type.values[i]);
            code.addIconst(i);
            code.addInvokespecial(name, "<init>", "(Ljava/lang/String;I)V");
            code.addPutstatic(name, type.values[i], "L" + name + ";");
        }
        code.addInvokestatic(name, "$values", "()[L" + name + ";");
        code.addPutstatic(name, "$VALUES", "[L" + name + ";");
        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        writeFile(cf);
    }

    private void compileData(XJLNDataClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, path + "." + name, "java/lang/Object");
        cf.setAccessFlags(clazz.getAccessFlag());

        //Fields
        for(String fieldName:clazz.fields.getKeyList()){
            XJLNField field = clazz.fields.getValue(fieldName);

            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), fieldName, toDesc(field.type()));
            fInfo.setAccessFlags(field.getAccessFlag());
            cf.addField2(fInfo);
        }

        //<init>
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<init>", "(" + toDesc(clazz.fields.getValueList().toArray(new XJLNField[0])) + ")V");
        mInfo.setAccessFlags(AccessFlag.PUBLIC);
        Bytecode code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");
        for(int i = 1;i <= clazz.fields.size();i++){
            code.addAload(0);
            String desc = toDesc(clazz.fields.getValue(i - 1));
            switch(desc){
                case "J" -> code.addLload(i);
                case "D" -> code.addDload(i);
                case "F" -> code.addFload(i);
                case "I", "Z", "B", "C", "S" -> code.addIload(i);
                default -> code.addAload(i);
            }
            code.addPutfield(name, clazz.fields.getKey(i - 1), desc);
        }
        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        writeFile(cf);
    }

    private void compileInterface(XJLNInterface clazz, String name, String path){
        ClassFile cf = new ClassFile(true, path + "." + name, null);
        cf.setAccessFlags(clazz.getAccessFlag());

        for(String methodName:clazz.methods.getKeyList()){
            XJLNInterfaceMethod method = clazz.methods.getValue(methodName);

            MethodInfo mInfo = new MethodInfo(cf.getConstPool(), methodName, "(" + toDesc(method.parameters().getValueList().toArray(new String[0])) + ")" + toDesc(method.returnType()));
            mInfo.setAccessFlags(AccessFlag.PUBLIC + AccessFlag.ABSTRACT);
            cf.addMethod2(mInfo);
        }

        writeFile(cf);
    }

    private void compileClass(XJLNClass clazz, String name, String path){
        ClassFile cf = new ClassFile(false, path + "." + name, null);
        cf.setAccessFlags(clazz.getAccessFlag());

        for(String field:clazz.staticFields.keySet()){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), field, toDesc(clazz.staticFields.get(field).type()));
            fInfo.setAccessFlags(clazz.staticFields.get(field).getAccessFlag());
            cf.addField2(fInfo);
        }

        for(String field:clazz.fields.keySet()){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), field, toDesc(clazz.fields.get(field).type()));
            fInfo.setAccessFlags(clazz.fields.get(field).getAccessFlag());
            cf.addField2(fInfo);
        }

        //clinit
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(AccessFlag.STATIC);
        Bytecode code = new Bytecode(cf.getConstPool());

        for(String fieldName:clazz.staticFields.keySet()){
            XJLNField field = clazz.staticFields.get(fieldName);
            if(field.initValue() != null){
                try {
                    AST.Calc ast = syntacticParser.parseCalc();

                    if(!field.type().equals(ast.type))
                        throw new RuntimeException("illegal type " + ast.type);

                    compileCalc(ast, code, cf.getConstPool());

                    code.addPutstatic(name, fieldName, toDesc(field.type()));
                }catch(Exception e){
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage() + " in: " + path + " :" + field.lineInFile());
                }
            }
        }

        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        if(!clazz.methods.containsKey("init"))
            clazz.createDefaultInit();

        //methods
        for(String method:clazz.methods.keySet()){
            if (clazz.methods.get(method).abstrakt) {
                mInfo = new MethodInfo(cf.getConstPool(), method.equals("init") ? "<init>" : method, "(" + toDesc(clazz.methods.get(method).parameters.getValueList().toArray(new String[0])) + ")" + toDesc(clazz.methods.get(method).returnType)); //TODO
                mInfo.setAccessFlags(clazz.methods.get(method).getAccessFlag());
                cf.addMethod2(mInfo);
            } else {
                mInfo = new MethodInfo(cf.getConstPool(), method, toDesc(clazz.methods.get(method)));
                mInfo.setAccessFlags(clazz.methods.get(method).getAccessFlag());

                code = new Bytecode(cf.getConstPool());

                if(method.equals("init")){
                    code.addAload(0);
                    code.addInvokespecial("java/lang/Object", "<init>", "()V");
                }

                AST[] astList = syntacticParser.parseAst(clazz.methods.get(method).code);

                for(AST ast:astList) {
                    if(ast instanceof AST.Return && !ast.type.equals(clazz.methods.get(method).returnType))
                        throw new RuntimeException("illegal return type in Method " + method + " of class " + path + "." + name);

                    compileAST(ast, code, cf.getConstPool());
                }

                if(clazz.methods.get(method).returnType.equals("void"))
                    code.add(0xb1); //return

                mInfo.setCodeAttribute(code.toCodeAttribute());
                cf.addMethod2(mInfo);
            }
        }

        //static methods
        for(String method:clazz.staticMethods.keySet()){
            mInfo = new MethodInfo(cf.getConstPool(), method, toDesc(clazz.staticMethods.get(method)));
            mInfo.setAccessFlags(clazz.staticMethods.get(method).getAccessFlag());

            code = new Bytecode(cf.getConstPool());

            AST[] astList = syntacticParser.parseAst(clazz.staticMethods.get(method).code);

            for(AST ast:astList) {
                if(ast instanceof AST.Return && !ast.type.equals(clazz.staticMethods.get(method).returnType))
                    throw new RuntimeException("illegal return type in Method " + method + " of class " + path + "." + name);

                compileAST(ast, code, cf.getConstPool());
            }

            if(clazz.staticMethods.get(method).returnType.equals("void"))
                code.add(0xb1); //return

            mInfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod2(mInfo);
        }

        writeFile(cf);
    }

    private void compileAST(AST ast, Bytecode code, ConstPool cp){
        if(ast instanceof AST.Calc)
            compileCalc((AST.Calc) ast, code, cp);
        else if(ast instanceof  AST.Return)
            compileReturn((AST.Return) ast, code, cp);
    }

    private void compileCalc(AST.Calc calc, Bytecode code, ConstPool cp){
        if(calc.left != null)
            compileCalc(calc.left, code, cp);

        if(calc.right == null)
            addValue(calc.value, code, cp);
        else{
            compileCalc(calc.right, code, cp);

            if(calc.left == null)
                addValue(calc.value, code, cp);

            switch(calc.type){
                case "int" -> {
                    switch (calc.opp){
                        case "+" -> code.add(0x60); //iadd
                        case "-" -> code.add(0x64); //isub
                        case "*" -> code.add(0x68); //imul
                        case "/" -> code.add(0x6c); //idiv
                    }
                }
                case "double" -> {
                    switch (calc.opp){
                        case "+" -> code.add(0x63); //dadd
                        case "-" -> code.add(0x67); //dsub
                        case "*" -> code.add(0x6b); //dmul
                        case "/" -> code.add(0x6f); //ddiv
                    }
                }
                case "float" -> {
                    switch (calc.opp){
                        case "+" -> code.add(0x62); //fadd
                        case "-" -> code.add(0x66); //fsub
                        case "*" -> code.add(0x6a); //fmul
                        case "/" -> code.add(0x6e); //fdiv
                    }
                }
                case "long" -> {
                    switch (calc.opp){
                        case "+" -> code.add(0x61); //ladd
                        case "-" -> code.add(0x65); //lsub
                        case "*" -> code.add(0x69); //lmul
                        case "/" -> code.add(0x6d); //ldiv
                    }
                }
            }
        }
    }

    private void compileCast(AST.Cast cast, Bytecode code){
        switch(cast.value.type){
            case "int" -> {
                switch(cast.to){
                    case "double" -> code.add(0x87); //i2d
                    case "long" -> code.add(0x85); //i2l
                    case "float" -> code.add(0x86); //i2f
                    case "byte" -> code.add(0x91); //i2b
                    case "char" -> code.add(0x92); //i2c
                    case "short" -> code.add(0x93); //i2s
                }
            }
            case "double" -> {
                switch(cast.to){
                    case "int" -> code.add(0x8e); //d2i
                    case "long" -> code.add(0x8f); //d2l
                    case "float" -> code.add(0x90); //d2f
                }
            }
            case "long" -> {
                switch(cast.to){
                    case "double" -> code.add(0x8a); //l2d
                    case "int" -> code.add(0x88); //l2i
                    case "float" -> code.add(0x89); //l2f
                }
            }
            case "float" -> {
                switch(cast.to){
                    case "double" -> code.add(0x8d); //f2d
                    case "long" -> code.add(0x8c); //f2l
                    case "int" -> code.add(0x8b); //f2i
                }
            }
        }
    }

    private void addValue(AST.Value value, Bytecode code, ConstPool cp){
        switch (value.type){
            case "int", "short", "byte", "char" -> {
                int intValue;
                if(value.type.equals("char"))
                    intValue = value.token.s().toCharArray()[1];
                else
                    intValue = Integer.parseInt(value.token.getWithoutExtension().s());

                if(intValue < 6)
                    code.addIconst(intValue);
                else
                    code.add(0x10 ,intValue); //Bipush
            }
            case "boolean" -> code.addIconst(value.token.s().equals("true") ? 1 : 0);
            case "float" -> {
                int index = 0;
                float floatValue = Float.parseFloat(value.token.getWithoutExtension().s());
                cp.addFloatInfo(floatValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getFloatInfo(index) == floatValue) break;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
            }
            case "double" -> {
                int index = 0;
                double doubleValue = Double.parseDouble(value.token.getWithoutExtension().s());
                cp.addDoubleInfo(doubleValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getDoubleInfo(index) == doubleValue) break;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
            }
            case "long" -> {
                int index = 0;
                long longValue = Long.parseLong(value.token.getWithoutExtension().s());
                cp.addLongInfo(longValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getLongInfo(index) == longValue) break;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
            }
        }
    }

    private void compileReturn(AST.Return ast, Bytecode code, ConstPool cp){ //TODO
        compileCalc(ast.calc, code, cp);

        switch(ast.type){
            case "double" -> code.add(0xaf); //dreturn
            case "float" -> code.add(0xae); //freturn
            case "long" -> code.add(0xad); //lreturn
            case "int", "boolean", "short", "byte", "char" -> code.add(0xac); //ireturn
        }
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile("compiled");
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException("failed to write ClassFile for " + cf.getName());
        }
    }

    private String toDesc(XJLNMethod method){
        StringBuilder desc = new StringBuilder("(");

        for(String type:method.parameters.getValueList())
            desc.append(toDesc(type));

        desc.append(")").append(toDesc(method.returnType));

        return desc.toString();
    }

    private String toDesc(XJLNField...fields){
        StringBuilder desc = new StringBuilder();

        for(XJLNField field:fields)
            desc.append(toDesc(field.type()));

        return desc.toString();
    }

    static String toDesc(String...types){
        StringBuilder desc = new StringBuilder();

        for(String type:types){
            switch (type){
                case "int"     -> desc.append("I");
                case "short"   -> desc.append("S");
                case "long"    -> desc.append("J");
                case "double"  -> desc.append("D");
                case "float"   -> desc.append("F");
                case "boolean" -> desc.append("Z");
                case "char"    -> desc.append("C");
                case "byte"    -> desc.append("B");
                case "void"    -> desc.append("V");
                default        -> desc.append("L").append(type).append(";"); //TODO arrays
            }
        }

        return desc.toString();
    }

    static String getMethodReturnType(String clazz, String method, String desc){
        return null; //TODO
    }

    static String getOperatorReturnType(String type1, String type2, String opp){
        if(PRIMITIVES.contains(type1)){
            if(!type1.equals(type2))
                return null;

            if(SyntacticParser.BOOL_OPERATORS.contains(opp))
                return "boolean";

            if(SyntacticParser.NUMBER_OPERATORS.contains(opp))
                return type1.equals("boolean") ? null : type1;
        }
        return null; //TODO
    }

    static String validateName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    private static void printDebug(String message){
        if(debug)
            System.out.println(message);
    }
}
