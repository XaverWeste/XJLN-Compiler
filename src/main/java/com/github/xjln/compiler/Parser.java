package com.github.xjln.compiler;

import com.github.xjln.lang.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

class Parser {

    private HashMap<String, String> uses;
    private XJLNClass mainClass;
    private Compilable current;
    private String path;
    private String className;
    private Scanner sc;

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
        mainClass = null;

        String line;
        while (sc.hasNextLine()) {
            line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if (line.startsWith("use ")) parseUseDef(line);
                else if(line.startsWith("def ")){
                    parseDef(line);
                    if(className != null) {
                        String name = className.split("\\.")[className.split("\\.").length - 1];
                        if (uses.containsKey(name))
                            throw new RuntimeException("class " + className + " already exist in: " + line);
                        uses.put(name, className);
                        classes.put(className, current);
                    }
                } else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        if(!classes.containsKey("Main") && mainClass != null){
            classes.put(Compiler.validateName(path + ".Main"), mainClass);
            if(!uses.containsKey("Main"))
                uses.put("Main", Compiler.validateName(path + ".Main"));
        }

        return classes;
    }

    private void resetUse(){
        uses = new HashMap<>();
        uses.put("RuntimeException", "java/lang/RuntimeException");
        uses.put("var", "com.github.xjln.utility.Var");
    }

    private void parseUseDef(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("use");

        ArrayList<String> use = new ArrayList<>();
        StringBuilder from = null;
        String as = null;

        if(th.assertToken(Token.Type.IDENTIFIER, Token.Type.SIMPLE).equals("{")){
            TokenHandler ib = th.getInBracket();
            use.add(ib.assertToken(Token.Type.IDENTIFIER).s());
            while (ib.hasNext()){
                ib.assertToken(",");
                use.add(ib.assertToken(Token.Type.IDENTIFIER).s());
            }
        }else
            use.add(th.current().s());

        if(th.assertToken("/", "from", "as").equals("/")){
            from = new StringBuilder(use.remove(0) + "/");
            from.append(th.assertToken(Token.Type.IDENTIFIER));

            while (th.hasNext() && th.assertToken("/", "as").equals("/"))
                from.append("/").append(th.assertToken(Token.Type.IDENTIFIER));

            use.add(from.toString().split("/")[from.toString().split("/").length - 1]);
            from.delete(from.length() - from.toString().split("/")[from.toString().split("/").length - 1].length() - 1, from.length());
        }else if(th.current().equals("from")){
            from = new StringBuilder();
            from.append(th.assertToken(Token.Type.IDENTIFIER));

            while (th.hasNext() && th.assertToken("/", "as").equals("/"))
                from.append("/").append(th.assertToken(Token.Type.IDENTIFIER));
        }

        if(th.current().equals("as")){
            if(use.size() != 1) throw new RuntimeException("only can alias one in: " + line);
            as = th.assertToken(Token.Type.IDENTIFIER).s();
            if(Compiler.PRIMITIVES.contains(as))
                throw new RuntimeException(as + " is not allowed as alias");
            if(uses.containsKey(as)) throw new RuntimeException("alias " + as + " already exist in: " + line);
            th.assertNull();
        }else
            th.assertNull();

        if(as != null)
            uses.put(as, Compiler.validateName(from + "/" + use.get(0)));
        else
            for(String s:use) {
                if(uses.containsKey(s)) throw new RuntimeException("alias " + s + " already exist in: " + line);
                uses.put(s, Compiler.validateName(from + "/" + s));
            }
    }

    private void parseDef(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        className = th.assertToken(Token.Type.IDENTIFIER).s();
        className = Compiler.validateName(path + "." + className);

        if(th.current().s().equalsIgnoreCase("main"))
            throw new RuntimeException("name \"main\" is not allowed for " + className);

        if(th.assertToken("=", "[", "(").s().equals("=")) parseEnumDef(th);
    }

    private void parseEnumDef(TokenHandler th){
        ArrayList<String> values = new ArrayList<>();
        values.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            if(values.contains(th.assertToken(Token.Type.IDENTIFIER).s())) throw new RuntimeException("value " + th.current().s() + " already exist for enum " + className);
            values.add(th.current().s());
        }

        current = new XJLNEnum(className, values);
    }

    private void parseInterface(){

    }

    private void parseRecord(){

    }

    private void parseClass(){

    }

    private XJLNField parseField(String line, boolean staticContext){
        boolean inner;
        boolean constant;
        String type;
        String name;
        String initValue;

        TokenHandler th = Lexer.toToken(line);

        if(th.next().equals("inner")){
            inner = true;
            th.next();
        }else
            inner = false;

        if(th.current().equals("const")){
            constant = true;
            th.next();
        }else
            constant = false;

        type = TokenHandler.assertToken(th.current(), Token.Type.IDENTIFIER).s();
        th.assertHasNext();

        if(th.next().equals("=")){
            name = type;
            type = null;
            th.last();
        }else
            name = TokenHandler.assertToken(th.current(), Token.Type.IDENTIFIER).s();

        if(th.hasNext()){
            th.assertToken("=");
            th.assertHasNext();

            StringBuilder value = new StringBuilder();
            while (th.hasNext())
                value.append(th.next()).append(" ");

            initValue = value.toString();
        }else
            initValue = null;

        if(type == null && initValue == null)
            throw new RuntimeException("Expected Type or Value for Field " + name + " in Class " + className); //TODO check className reference

        if(staticContext && constant && initValue == null)
            throw new RuntimeException("Expected Value for Field " + name + " in Class " + className);

        return new XJLNField(inner, constant, type, name, initValue);
    }

    private XJLNMethodAbstract parseMethod(String line, boolean staticContext){
        boolean inner = false;
        String name;
        ArrayList<String> genericTypes = null;
        HashMap<String, XJLNParameter> parameter;
        String returnType = "void";

        boolean abstrakt = false;
        TokenHandler th = Lexer.toToken(line);

        th.assertToken("def");
        th.assertToken(Token.Type.IDENTIFIER);

        if(th.current().equals("abstract")) {
            abstrakt = true;
            th.assertToken(Token.Type.IDENTIFIER);
        }

        if(th.current().equals("inner")){
            inner = true;
            th.assertToken(Token.Type.IDENTIFIER);
        }

        name = th.current().s();

        if(th.assertToken("<", "(").equals("<")){
            genericTypes = new ArrayList<>();
            genericTypes.add(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertHasNext();

            while (th.assertToken(">", ",").equals(",")){
                if(genericTypes.contains(th.assertToken(Token.Type.IDENTIFIER).s()))
                    throw new RuntimeException("Value " + th.current() + " is already defined in method " + name);

                genericTypes.add(th.current().s());
                th.assertHasNext();
            }
            th.assertToken("(");
        }

        parameter = parseParameterList(th.getInBracket());

        if(th.hasNext()){
            if(th.assertToken(":", "->", "{").equals(":")){
                th.assertToken(":");
                returnType = th.assertToken(Token.Type.IDENTIFIER).s();
                if(genericTypes == null || !genericTypes.contains(returnType))
                    returnType = validateType(returnType);

                if(th.hasNext())
                    th.assertToken("->", "{", "=");
            }
        }

        if(!abstrakt) {
            ArrayList<String> code = new ArrayList<>();

            if(th.current().equals("->")){
                StringBuilder statement = new StringBuilder();
                while (th.hasNext())
                    statement.append(th.next()).append(" ");

                code.add(statement.toString());
            }else if(th.current().equals("=")){
                StringBuilder statement = new StringBuilder("return ");
                while (th.hasNext())
                    statement.append(th.next()).append(" ");

                code.add(statement.toString());
            }else if(th.current().equals("{")){
                throw new RuntimeException("Statement \"" + th + "\" is currently not supported");
                //TODO {...}
            }else
                code.addAll(parseCode());

            return new XJLNMethod(inner, name, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, returnType, code.toArray(new String[0]));
        }else {
            if(th.current().equals("->") || th.current().equals("=") || th.current().equals("{"))
                throw new RuntimeException("Method " + name + " should not be abstract");

            return new XJLNMethodAbstract(inner, name, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, returnType);
        }
    }

    private HashMap<String, XJLNParameter> parseParameterList(TokenHandler th){
        HashMap<String, XJLNParameter> parameterList = new HashMap<>();

        if(th.length() == 1){
            if(th.next().equals("/"))
                return null;
            th.last();
        }

        while (th.hasNext()){
            boolean constant = false;
            String type;
            String name;
            StringBuilder value = null;

            if(th.assertToken(Token.Type.IDENTIFIER).equals("const")){
                constant = true;
                th.assertToken(Token.Type.IDENTIFIER);
            }

            type = th.current().s();
            name = th.assertToken(Token.Type.IDENTIFIER).s();

            if(th.hasNext() && th.assertToken(",", "=").equals("=")){
                value = new StringBuilder();
                while (th.hasNext() && !th.next().equals(",")){
                    if(th.current().equals("("))
                        value.append(" ( ").append(th.getInBracket().toString()).append(" ) ");
                    else
                        value.append(th.current().toString()).append(" ");
                }
                th.last();
            }else
                th.last();

            if(parameterList.containsKey(name))
                throw new RuntimeException("Parameter " + name + " is already defined");

            parameterList.put(name, new XJLNParameter(constant, type, name, value == null ? null : value.toString()));

            if(th.hasNext()){
                th.assertToken(",");
                th.assertHasNext();
            }
        }

        return parameterList;
    }

    private ArrayList<String> parseCode(){
        ArrayList<String> code = new ArrayList<>();
        int i = 1;
        while (i > 0 && sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if(line.equals("end")) i--;
                if((line.startsWith("if ") || line.startsWith("while ") || line.startsWith("for ")) && !line.contains("->")) i++;
                if(line.startsWith("switch ")) i++;
                if(i > 0) code.add(line);
            }
        }

        if(i > 0)
            throw new RuntimeException("method in class " + className + " was not closed");

        return code;
    }

    private String validateType(String type){
        if(Compiler.PRIMITIVES.contains(type)) return type;
        if(uses.containsKey(type)) return uses.get(type);
        if(type.startsWith("[")){
            String[] sa = type.split("\\[");
            return "[".repeat(Math.max(0, sa.length - 1)) + Compiler.validateName(sa[sa.length - 1]);
        }
        return Compiler.validateName(path + "\\" + type);
    }
}
