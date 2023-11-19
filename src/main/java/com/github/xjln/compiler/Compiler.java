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
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), name, toDesc(clazz.staticFields.get(field).type()));
            fInfo.setAccessFlags(clazz.staticFields.get(field).getAccessFlag());
            cf.addField2(fInfo);
        }

        for(String field:clazz.fields.keySet()){
            FieldInfo fInfo = new FieldInfo(cf.getConstPool(), name, toDesc(clazz.fields.get(field).type()));
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
                    AST[] ast = syntacticParser.parseAst(field.initValue());
                    assert ast.length == 1;

                    if (!field.type().equals(((AST.Calc) ast[0]).value.token.t().toString()))
                        throw new RuntimeException("illegal type");

                    //TODO
                }catch(RuntimeException e){
                    throw new RuntimeException(e.getMessage() + "in: " + path + " :" + field.lineInFile());
                }
            }
        }

        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //init
        code = new Bytecode(cf.getConstPool());
        code.addAload(0);
        code.addInvokespecial("java/lang/Object", "<init>", "()V");

        if(clazz.methods.get("init") == null) {
            mInfo = new MethodInfo(cf.getConstPool(), "<init>", "()V");
            mInfo.setAccessFlags(AccessFlag.PUBLIC);
        }else{
            mInfo = new MethodInfo(cf.getConstPool(), "<init>", toDesc(clazz.methods.get("init").parameters.getValueList().toArray(new String[0])));
            mInfo.setAccessFlags(clazz.methods.get("init").getAccessFlag());
        }

        for(int i = 0;i < clazz.fields.size();i++){

        }

        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());

        cf.addMethod2(mInfo);

        for(String method:clazz.methods.keySet()){
            if(clazz.methods.get(method).abstrakt) {
                mInfo = new MethodInfo(cf.getConstPool(), method, "(" + toDesc(clazz.methods.get(method).parameters.getValueList().toArray(new String[0])) + ")" + toDesc(clazz.methods.get(method).returnType)); //TODO
                mInfo.setAccessFlags(clazz.getAccessFlag());
                cf.addMethod2(mInfo);
            }else{
                //TODO
            }
        }

        for(String method:clazz.staticMethods.keySet()){
            //TODO
        }

        writeFile(cf);
    }

    private void compileAST(AST ast, Bytecode code){
        if(ast instanceof AST.Calc)
            compileCalc((AST.Calc) ast, code);
    }

    private void compileCalc(AST.Calc calc, Bytecode code){
        if(calc.next != null)
            compileCalc(calc, code);

        if(calc.value.token != null){
            switch (calc.value.token.t()){ //TODO
                case INTEGER -> code.add(0x10, Integer.valueOf(calc.value.token.s())); //bipush
            }
        }//TODO
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile("compiled");
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException("failed to write ClassFile for " + cf.getName());
        }
    }

    private String toDesc(XJLNField...fields){
        StringBuilder desc = new StringBuilder();

        for(XJLNField field:fields)
            desc.append(toDesc(field.type()));

        return desc.toString();
    }

    private String toDesc(String...types){
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
        return null;
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
