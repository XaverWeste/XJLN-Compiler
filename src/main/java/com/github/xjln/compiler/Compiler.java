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
import java.util.ArrayList;
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

    private XJLNClass current;

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
        code.add(Opcode.ARETURN);
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
        code.add(Opcode.ARETURN);
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
        code.add(Opcode.RETURN);
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
            code.add(Opcode.AASTORE);
        }
        code.add(Opcode.ARETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        //<clinit>
        mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(0x8);
        code = new Bytecode(cf.getConstPool());
        for(int i = 0;i < type.values.length;i++) {
            code.addNew(name);
            code.add(Opcode.DUP);
            code.addLdc(type.values[i]);
            code.addIconst(i);
            code.addInvokespecial(name, "<init>", "(Ljava/lang/String;I)V");
            code.addPutstatic(name, type.values[i], "L" + name + ";");
        }
        code.addInvokestatic(name, "$values", "()[L" + name + ";");
        code.addPutstatic(name, "$VALUES", "[L" + name + ";");
        code.add(Opcode.RETURN);
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
        code.add(Opcode.RETURN);
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

        //clinit TODO init values
        MethodInfo mInfo = new MethodInfo(cf.getConstPool(), "<clinit>", "()V");
        mInfo.setAccessFlags(AccessFlag.STATIC);
        Bytecode code = new Bytecode(cf.getConstPool());

        for(String fieldName:clazz.staticFields.keySet()){
            XJLNField field = clazz.staticFields.get(fieldName);
            if(field.initValue() != null){
                try {
                    AST.Calc ast = syntacticParser.parseCalc(false);

                    if(!field.type().equals(ast.type))
                        throw new RuntimeException("illegal type " + ast.type);

                    compileCalc(ast, code, cf.getConstPool(), new OperandStack());

                    code.addPutstatic(name, fieldName, toDesc(field.type()));
                }catch(Exception e){
                    e.printStackTrace();
                    throw new RuntimeException(e.getMessage() + " in: " + path + " :" + field.lineInFile());
                }
            }
        }

        code.add(Opcode.RETURN);
        mInfo.setCodeAttribute(code.toCodeAttribute());
        cf.addMethod2(mInfo);

        if(!clazz.methods.containsKey("init"))
            clazz.createDefaultInit();

        current = clazz;

        //methods
        compileMethods(clazz.methods, cf, path + "." + name);

        //static methods
        compileMethods(clazz.staticMethods, cf, path + "." + name);

        writeFile(cf);
    }

    private void compileMethods(HashMap<String, XJLNMethod> methods, ClassFile cf, String clazzName){
        for(String method:methods.keySet()){
            MethodInfo mInfo = new MethodInfo(cf.getConstPool(), method.equals("init") ? "<init>" : method, toDesc(methods.get(method)));
            mInfo.setAccessFlags(methods.get(method).getAccessFlag());

            Bytecode code = new Bytecode(cf.getConstPool());

            if(method.equals("<init>")){
                code.addAload(0);
                code.addInvokespecial("java/lang/Object", "<init>", "()V");
            }

            AST[] astList = syntacticParser.parseAst(methods.get(method).code);
            OperandStack os = OperandStack.forMethod(methods.get(method));

            for(int i = 0;i < astList.length;i++){
                if(astList[i] instanceof AST.Return && !astList[i].type.equals(methods.get(method).returnType))
                    throw new RuntimeException("expected " + methods.get(method).returnType + " got " + astList[i].type+ " in: " + clazzName + " :" + (methods.get(method).line + i));

                compileAST(astList[i], code, cf.getConstPool(), os);
            }

            if(methods.get(method).returnType.equals("void"))
                code.add(Opcode.RETURN);
            else if(!(astList[astList.length - 1] instanceof AST.Return))
                throw new RuntimeException("Expected return");

            mInfo.setCodeAttribute(code.toCodeAttribute());
            cf.addMethod2(mInfo);
        }
    }

    private void compileAST(AST ast, Bytecode code, ConstPool cp, OperandStack os){
        if(ast instanceof  AST.Return)
            compileReturn((AST.Return) ast, code, cp, os);
        else if(ast instanceof AST.Calc)
            compileCalc((AST.Calc) ast, code, cp, os);
        else if(ast instanceof AST.VarAssigment)
            compileVarAssignment((AST.VarAssigment) ast, code, cp, os);
        else if(ast instanceof AST.While)
            compileWhile((AST.While) ast, code, cp, os);
        else if(ast instanceof AST.If)
            compileIf((AST.If) ast, code, cp, os);
    }

    private void compileWhile(AST.While ast, Bytecode code, ConstPool cp, OperandStack os){
        int start = code.getSize();
        compileCalc(ast.condition, code, cp, os);
        code.addOpcode(Opcode.IFEQ);
        int branch = code.getSize();
        code.addIndex(0);

        for(AST statement: ast.ast)
            compileAST(statement, code, cp, os);

        code.addOpcode(Opcode.GOTO);
        code.addIndex(-(code.getSize() - start));
        code.write16bit(branch, code.getSize() - branch + 1);
    }

    private void compileIf(AST.If ast, Bytecode code, ConstPool cp, OperandStack os){
        ArrayList<Integer> gotos = new ArrayList<>();
        int branch = 0;

        while (ast != null){
            if(ast.condition != null) {
                compileCalc(ast.condition, code, cp, os);
                code.addOpcode(Opcode.IFEQ);
                branch = code.getSize();
                code.addIndex(0);
            }

            for(AST statement:ast.ast)
                compileAST(statement, code, cp, os);

            if(ast.elif != null){
                code.addOpcode(Opcode.GOTO);
                gotos.add(code.getSize());
                code.addIndex(0);
            }

            if(ast.condition != null)
                code.write16bit(branch, code.getSize() - branch + 1);

            ast = ast.elif;
        }

        for(int i:gotos)
            code.write16bit(i, code.getSize() - i + 1);
    }

    private void compileCalc(AST.Calc calc, Bytecode code, ConstPool cp, OperandStack os){
        if(calc.right == null) {
            if(calc.value.call != null)
                compileCall(calc.value.call, code, cp, os);
            else
                addValue(calc.value, code, cp, os);
        }else{
            if(calc.opp.equals("=") || calc.opp.equals("#")){
                compileCalc(calc.left, code, cp, os);
                if(calc.opp.equals("=")) code.add(Opcode.DUP);
                compileStore(calc.right.value.call.call, calc.type, code, os);
                return;
            }

            compileCalc(calc.right, code, cp, os);

            if(calc.left == null) {
                if(calc.value.call != null)
                    compileCall(calc.value.call, code, cp, os);
                else
                    addValue(calc.value, code, cp, os);
            }else
                compileCalc(calc.left, code, cp, os);

            switch(calc.type){
                case "int", "char", "byte", "short", "boolean" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.IADD);
                        case "-" -> code.add(Opcode.ISUB);
                        case "*" -> code.add(Opcode.IMUL);
                        case "/" -> code.add(Opcode.IDIV);
                        case "==", "!=", "<", "<=", ">", ">=" -> {
                            switch(calc.opp) {
                                case "==" -> code.addOpcode(Opcode.IF_ICMPEQ);
                                case "!=" -> code.addOpcode(Opcode.IF_ICMPNE);
                                case "<" -> code.addOpcode(Opcode.IF_ICMPLT);
                                case "<=" -> code.addOpcode(Opcode.IF_ICMPLE);
                                case ">" -> code.addOpcode(Opcode.IF_ICMPGT);
                                case ">=" -> code.addOpcode(Opcode.IF_ICMPGE);
                            }
                            int branchLocation = code.getSize();
                            code.addIndex(0);
                            code.addIconst(0);
                            code.addOpcode(Opcode.GOTO);
                            int endLocation = code.getSize();
                            code.addIndex(0);
                            code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
                            code.addIconst(1);
                            code.write16bit(endLocation, code.getSize() - endLocation + 1);
                        }
                    }
                    os.pop();
                    if(SyntacticParser.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "double" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.DADD);
                        case "-" -> code.add(Opcode.DSUB);
                        case "*" -> code.add(Opcode.DMUL);
                        case "/" -> code.add(Opcode.DDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.DCMPG);
                            compileBoolOp(calc, code, cp, os);
                        }
                    }
                    os.pop();
                    if(SyntacticParser.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "float" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.FADD);
                        case "-" -> code.add(Opcode.FSUB);
                        case "*" -> code.add(Opcode.FMUL);
                        case "/" -> code.add(Opcode.FDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.FCMPG);
                            compileBoolOp(calc, code, cp, os);
                        }
                    }
                    os.pop();
                    if(SyntacticParser.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
                case "long" -> {
                    switch (calc.opp){
                        case "+" -> code.add(Opcode.LADD);
                        case "-" -> code.add(Opcode.LSUB);
                        case "*" -> code.add(Opcode.LMUL);
                        case "/" -> code.add(Opcode.LDIV);
                        case "==", "!=", "<=", "<", ">=", ">" -> {
                            code.add(Opcode.LCMP);
                            compileBoolOp(calc, code, cp, os);
                        }
                    }
                    os.pop();
                    if(SyntacticParser.BOOL_OPERATORS.contains(calc.opp)) {
                        os.pop();
                        os.push(1);
                    }
                }
            }
        }
    }

    private void compileBoolOp(AST.Calc calc, Bytecode code, ConstPool cp, OperandStack os) {
        switch(calc.opp){
            case "==", "!=" -> {
                code.addIconst(0);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<", ">" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(calc.opp.equals("<") ? "-1" : "1", Token.Type.INTEGER);
                addValue(value, code, cp, os);
                code.addOpcode(Opcode.IF_ICMPEQ);
            }
            case "<=", ">=" -> {
                AST.Value value = new AST.Value();
                value.type = "int";
                value.token = new Token(calc.opp.equals("<=") ? "1" : "-1", Token.Type.INTEGER);
                addValue(value, code, cp, os);
                code.addOpcode(Opcode.IF_ICMPNE);
            }
        }
        int branchLocation = code.getSize();
        code.addIndex(0);
        if(calc.opp.equals("!=")) code.addIconst(1);
        else code.addIconst(0);
        code.addOpcode(Opcode.GOTO);
        int endLocation = code.getSize();
        code.addIndex(0);
        code.write16bit(branchLocation, code.getSize() - branchLocation + 1);
        if(calc.opp.equals("!=")) code.addIconst(0);
        else code.addIconst(1);
        code.write16bit(endLocation, code.getSize() - endLocation + 1);
    }

    private void compileCast(AST.Value value, Bytecode code, OperandStack os){
        switch(value.type){
            case "int", "short", "byte" -> {
                switch(value.cast){
                    case "double" -> {
                        code.add(Opcode.I2D);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "long" -> {
                        code.add(Opcode.I2L);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "float" -> code.add(Opcode.I2F);
                    case "byte" -> code.add(Opcode.I2B);
                    case "char" -> code.add(Opcode.I2C);
                    case "short" -> code.add(Opcode.I2S);
                }
            }
            case "double" -> {
                switch(value.cast){
                    case "int" -> {
                        code.add(Opcode.D2I);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                    case "long" -> code.add(Opcode.D2L);
                    case "float" -> {
                        code.add(Opcode.D2F);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                }
            }
            case "long" -> {
                switch(value.cast){
                    case "double" -> code.add(Opcode.L2D);
                    case "int" -> {
                        code.add(Opcode.L2I);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                    case "float" -> {
                        code.add(Opcode.L2F);
                        String temp = os.pop();
                        os.push(temp, 1);
                    }
                }
            }
            case "float" -> {
                switch(value.cast){
                    case "double" -> {
                        code.add(Opcode.F2D);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "long" -> {
                        code.add(Opcode.F2L);
                        String temp = os.pop();
                        os.push(temp, 2);
                    }
                    case "int" -> code.add(Opcode.F2I);
                }
            }
        }
    }

    private void addValue(AST.Value value, Bytecode code, ConstPool cp, OperandStack os){
        switch (value.token.t().toString()){
            case "int", "short", "byte", "char" -> {
                int intValue;
                if(value.type.equals("char"))
                    intValue = value.token.s().toCharArray()[1];
                else
                    intValue = Integer.parseInt(value.token.getWithoutExtension().s());

                if(intValue < 6 && intValue >= 0)
                    code.addIconst(intValue);
                else
                    code.add(0x10 ,intValue); //Bipush

                os.push(1);
            }
            case "boolean" -> {
                code.addIconst(value.token.s().equals("true") ? 1 : 0);
                os.push("temp", 1);
            }
            case "float" -> {
                int index = 0;
                float floatValue = Float.parseFloat(value.token.getWithoutExtension().s());
                cp.addFloatInfo(floatValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getFloatInfo(index) == floatValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(1);
            }
            case "double" -> {
                int index = 0;
                double doubleValue = Double.parseDouble(value.token.getWithoutExtension().s());
                cp.addDoubleInfo(doubleValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getDoubleInfo(index) == doubleValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
            }
            case "long" -> {
                int index = 0;
                long longValue = Long.parseLong(value.token.getWithoutExtension().s());
                cp.addLongInfo(longValue);
                while(index < cp.getSize()){
                    try{
                        if(cp.getLongInfo(index) == longValue) break;
                        else index++;
                    }catch(Exception ignored){index++;}
                }
                code.addLdc(index);
                os.push(2);
            }
        }

        if(value.cast != null)
            compileCast(value, code, os);
    }

    private void compileCall(AST.Call call, Bytecode code, ConstPool cp, OperandStack os){
        compileLoad(call, code, os);
    }

    private void compileVarAssignment(AST.VarAssigment ast, Bytecode code, ConstPool cp, OperandStack os){
        compileCalc(ast.calc, code, cp, os);
        compileStore(ast.name, ast.type, code, os);
        int length = (ast.type.equals("double") || ast.type.equals("long")) ? 2 : 1;
        os.pop();
        os.push(ast.name, length);
    }

    private void compileLoad(AST.Call ast, Bytecode code, OperandStack os){
        if(ast.type == null)
            return; //TODO

        if(os.contains(ast.call)) {
            switch (ast.type) {
                case "int", "boolean", "char", "byte", "short" -> {
                    code.addIload(os.get(ast.call));
                    os.push(1);
                }
                case "float" -> {
                    code.addFload(os.get(ast.call));
                    os.push(1);
                }
                case "double" -> {
                    code.addDload(os.get(ast.call));
                    os.push(2);
                }
                case "long" -> {
                    code.addLload(os.get(ast.call));
                    os.push(2);
                }
            }
        }else{
            throw new RuntimeException("Variable " + ast.call + " did not exist");
        }
    }

    private void compileStore(String name, String type, Bytecode code, OperandStack os){
        if(os.contains(name) || !current.fields.containsKey(name)) {
            int where = os.get(name);

            if(where == -1) {
                os.pop();
                where = os.push(name, Set.of("double", "long").contains(type) ? 2 : 1);
            }

            switch (type) {
                case "int", "boolean", "char", "byte", "short" -> code.addIstore(where);
                case "float" -> code.addFstore(where);
                case "double" -> code.addDstore(where);
                case "long" -> code.addLstore(where);
            }
        }else{
            throw new RuntimeException("Variable " + name + " did not exist");
        }
    }

    private void compileReturn(AST.Return ast, Bytecode code, ConstPool cp, OperandStack os){ //TODO
        compileCalc(ast.calc, code, cp, os);

        switch(ast.type){
            case "double" -> code.add(Opcode.DRETURN);
            case "float" -> code.add(Opcode.FRETURN);
            case "long" -> code.add(Opcode.LRETURN);
            case "int", "boolean", "short", "byte", "char" -> code.add(Opcode.IRETURN);
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
        if(opp.equals("=") && type1 == null)
            return type2;

        if(opp.equals("=") && type1.equals(type2))
            return type1;

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
