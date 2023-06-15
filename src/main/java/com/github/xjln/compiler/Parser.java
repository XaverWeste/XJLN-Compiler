package com.github.xjln.compiler;

import com.github.xjln.lang.Compilable;
import com.github.xjln.lang.XJLNClass;
import com.github.xjln.lang.XJLNEnum;
import com.github.xjln.lang.XJLNMethod;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

class Parser {
    private final Set<String> primitives;
    public final Lexer lexer;

    private HashMap<String, String> uses;
    private Compilable current;
    private String path;
    private String className;
    private Scanner sc;

    public Parser(){
        primitives = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
        lexer = new Lexer();
    }

    public HashMap<String, Compilable> parseFile(File file) {
        HashMap<String, Compilable> classes = new HashMap<>();
        resetUse();
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file " + file.getPath() + " not found");
        }

        path = file.getPath();
        path = path.substring(0, path.length() - 5);

        String line;
        while (sc.hasNextLine()) {
            line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if (line.startsWith("use")) parseUseDef(line);
                else if(line.startsWith("def")){
                    parseDef(line);
                    classes.put(className, current);
                } else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        return classes;
    }

    private void resetUse(){
        uses = new HashMap<>();
        uses.put("boolean", "xjln/core/Boolean/boolean");
        uses.put("byte", "xjln/core/Byte/byte");
        uses.put("char", "xjln/core/Char/char");
        uses.put("double", "xjln/core/Double/double");
        uses.put("float", "xjln/core/Float/float");
        uses.put("int", "xjln/core/Int/int");
        uses.put("long", "xjln/core/Long/long");
        uses.put("short", "xjln/core/Short/short");
    }

    private void parseUseDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("use");

        StringBuilder use = new StringBuilder();
        use.append(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext() && th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR).t().equals(Token.Type.OPERATOR)){
            TokenHandler.assertToken(th.current(), "/");
            use.append("/");
            use.append(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertHasNext();
        }

        String as = null;

        if(use.toString().contains("/") || th.current().t() == Token.Type.IDENTIFIER){
            TokenHandler.assertToken(th.current(), "as");
            as = th.assertToken(Token.Type.IDENTIFIER).s();
        }

        if(as == null) as = use.toString().split("/")[use.toString().split("/").length - 1];

        if(uses.containsKey(as)){
            System.out.println("[WARNING] alias " + as + " was already defined in " + path);
            uses.replace(as, use.toString());
        }else uses.put(as, use.toString());
    }

    private void parseDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("def");
        className = th.assertToken(Token.Type.IDENTIFIER).s();
        className = Compiler.validateName(path + "." + className);

        if(th.assertToken("=", "[").s().equals("=")) parseEnumDef(th);
        else parseClassDef(th);
    }

    private void parseEnumDef(TokenHandler th){
        ArrayList<String> values = new ArrayList<>();
        values.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            if(values.contains(th.assertToken(Token.Type.IDENTIFIER).s())) throw new RuntimeException("value " + th.current().s() + " already exist for enum " + className);
            values.add(th.current().s());
        }

        current = new XJLNEnum(values);
    }

    private void parseClassDef(TokenHandler th){
        TokenHandler parameterList = th.getInBracket();
        String[] parameter = parseParameterList(parameterList);
        ArrayList<String> superClasses = new ArrayList<>();

        if(th.hasNext() && th.assertToken("->", "=>").equals("=>")){
            superClasses.add(validateType(th.assertToken(Token.Type.IDENTIFIER).s()));
            while (th.hasNext() && th.assertToken(",", "->").equals(",")){
                superClasses.add(validateType(th.assertToken(Token.Type.IDENTIFIER).s()));
            }
            if(th.current().equals("->")){
                current = new XJLNClass(parameter, superClasses.toArray(new String[0]), th.assertToken(Token.Type.IDENTIFIER).s());
                th.assertToken("(");
                th.assertToken(")");
                th.assertNull();
            }
        }else{
            current = new XJLNClass(parameter, new String[0], th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertToken("(");
            th.assertToken(")");
            th.assertNull();
        }

        String line = "";
        while(sc.hasNextLine() && !line.equals("end")){
            line = sc.nextLine().trim();
            if(!line.equals("") && !line.startsWith("#") && !line.equals("end")){
                if(line.startsWith("def ")) parseMethodDef(line);
                else parseFieldDef(line);
            }
        }
    }

    private void parseFieldDef(String line){
        TokenHandler th = lexer.toToken(line);
        String type = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
        String name = th.assertToken(Token.Type.IDENTIFIER).s();
        String value = null;

        if(th.hasNext()){
            th.assertToken("=");
            value = th.next().s();
        }

        if(current instanceof XJLNClass){
            if(((XJLNClass) current).fields.containsKey(name)) throw new RuntimeException("field " + name + " is already defined in " + className);
            ((XJLNClass) current).fields.put(name, type); //TODO values
        }else throw new RuntimeException("internal Compiler error");
    }

    private void parseMethodDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("def");
        String name = th.assertToken(Token.Type.IDENTIFIER).s();
        boolean inner = name.equals("inner");
        if(inner) name = th.assertToken(Token.Type.IDENTIFIER).s();
        th.assertToken("(");
        String[] parameter = parseParameterList(th.getInBracket());
        if(parameter == null) throw new RuntimeException("illegal argument in " + th);
        String returnType = "void";
        if(th.hasNext()){
            th.assertToken(":");
            th.assertToken(":");
            returnType = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertNull();
        }

        StringBuilder code = new StringBuilder();
        int i = 1;
        while(sc.hasNextLine() && i > 0){
            line = sc.nextLine().trim();
            if(!line.equals("") && !line.startsWith("#")){
                if(Set.of("if", "while", "for").contains(line.split(" ")[0])) i++;
                if(line.equals("end")) i--;
                if(i > 0) code.append(line).append("\n");
            }
        }

        if(current instanceof XJLNClass){
            if(((XJLNClass) current).methods.containsKey(name)) throw new RuntimeException("method " + name + " already exists in " + className);
            ((XJLNClass) current).methods.put(name, new XJLNMethod(parameter, inner, returnType, code.toString()));
        }else throw new RuntimeException("internal Compiler error");
    }

    private String[] parseParameterList(TokenHandler parameterList){
        ArrayList<String> parameter = new ArrayList<>();

        if(parameterList.hasNext() && parameterList.next().equals("/")){
            parameterList.assertNull();
            return null;
        }else parameterList.toFirst();

        String type, name, names = "", value = null;

        while (parameterList.hasNext()){
            type = parameterList.assertToken(Token.Type.IDENTIFIER).s();
            type = validateType(type);

            name = parameterList.assertToken(Token.Type.IDENTIFIER).s();
            if(names.contains(" " + name + " ")) throw new RuntimeException(name + " is already defined in [" + parameterList + "]");
            else names += " " + name + " ";

            if(parameterList.hasNext()) {
                if (parameterList.assertToken("=", ",").s().equals("=")) {
                    value = parameterList.next().s();
                    if(parameterList.hasNext()) parameterList.assertToken(",");
                }

                parameterList.assertHasNext();
            }

            parameter.add(type + " " + name + (value == null ? "" : " " + value));
        }

        return parameter.toArray(new String[0]);
    }

    private String validateType(String type){
        return uses.getOrDefault(type, type);
    }
}
