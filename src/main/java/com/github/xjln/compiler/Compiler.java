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
                compileClass(file.main, path + ".Main");

            for(String name:file.classes.keySet()){
                Compilable c = file.classes.get(name);

                System.out.println(name);

                if(c instanceof XJLNType)
                    compileType((XJLNType) c, name);
            }
        }
    }

    private void compileType(XJLNType type, String name){
        ClassFile cf = new ClassFile(false, name, "java.lang.Enum");
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
        code.addGetstatic(name, "$Values", "[L" + name + ";");
        code.addInvokevirtual("[L" + name + ";", "clone","()[Ljava.lang.Object;");
        code.addCheckcast("[L" + name + ";");
        code.add(0xb0); //areturn
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //valueOf
        mInfo = new MethodInfo(cf.getConstPool(), "valueOf", "L" + name + ";");
        mInfo.setAccessFlags(0x9);
        code = new Bytecode(cf.getConstPool());
        code.addLdc("L" + name + ";.class");
        code.addAload(0);
        code.addInvokestatic("java.lang.Enum", "valueOf", "(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;");
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
        code.addInvokespecial("java.lang.Enum", "<inti>", "(Ljava/lang/String;I)V");
        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //$values
        mInfo = new MethodInfo(cf.getConstPool(), "$values", "()[L" + name + ";");
        mInfo.setAccessFlags(0x100A);
        code = new Bytecode(cf.getConstPool());
        code.add(0x5); //iconst_2
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

        /*
        System.out.print(mInfo.getAccessFlags() + " " + mInfo.getDescriptor() + " " + mInfo.getName());
        CodeAttribute ca = mInfo.getCodeAttribute();

        if(ca != null) {
            CodeIterator ci = ca.iterator();

            int last = -1;

            while (ci.hasNext()) {
                try {
                    int index = ci.next();
                    while (index > (last += 1)) {
                        System.out.print(" " + ci.byteAt(last));
                    }
                    System.out.println(" ");
                    int op = ci.byteAt(index);
                    System.out.print("   " + index + " " + Mnemonic.OPCODE[op]);
                }catch (Exception ignored){}
            }
        }

         */

        //<clinit>
        mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(0x8);
        code = new Bytecode(cf.getConstPool());
        for(int i = 0;i < type.values.length;i++) {
            code.addNew(name);
            code.add(0x59); //dup
            code.addLdc(type.values[i]);
            code.addIconst(i);
            code.addInvokestatic(name, "<init>", "(Ljava/lang/String;I)V");
            code.addPutstatic(name, type.values[i], "L" + name + ";");
        }
        code.addInvokestatic(name, "$values", "()[L" + name + ";");
        code.addPutstatic(name, "$VALUES", "[L" + name + ";");
        code.add(0xb1); //return
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        writeFile(cf);
    }

    private void compileClass(XJLNClass clazz, String name){
        ClassFile cf = new ClassFile(false, name, null);
        cf.setAccessFlags(AccessFlag.PUBLIC); //TODO accessflag

        for(String field:clazz.staticFields.keySet()){
            cf.addField2(compileField(field, clazz.getStaticField(field), cf.getConstPool()));
        }

        writeFile(cf);
    }

    private FieldInfo compileField(String name, XJLNField field, ConstPool cp){
        FieldInfo fInfo = new FieldInfo(cp, name, toDesc(field.type()));
        fInfo.setAccessFlags(field.getAccessFlag());
        return fInfo;
    }

    private void writeFile(ClassFile cf){
        try{
            ClassPool.getDefault().makeClass(cf).writeFile("compiled");
        }catch (IOException | CannotCompileException e) {
            throw new RuntimeException("failed to write ClassFile for " + cf.getName());
        }
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
                default -> desc.append("L").append(type).append(";"); //TODO arrays
            }
        }

        return desc.toString();
    }

    public static String validateName(String name){
        name = name.replace("/", ".");
        name = name.replace("\\", ".");
        return name;
    }

    private static void printDebug(String message){
        if(debug)
            System.out.println(message);
    }
}
