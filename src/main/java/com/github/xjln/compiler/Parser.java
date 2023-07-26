package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

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
                else if(line.startsWith("main")){
                    parseMain(line);
                    if(classes.containsKey("Main")) throw new RuntimeException("main is already defined in " + path);
                    else classes.put(Compiler.validateName(path + ".Main"), mainClass);
                    if(!uses.containsKey("Main"))
                        uses.put("Main", Compiler.validateName(path + ".Main"));
                }else if(line.startsWith("def ")){
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
            from.delete(from.toString().split("/")[from.toString().split("/").length - 1].length() + 1, from.length());
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

    private void parseMain(String line){
        if(mainClass == null)
            mainClass = new XJLNClass(new MatchedList<>(), new String[0], uses);
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("main");
        if(th.hasNext()){
            th.assertToken("->");
            th.assertHasNext();
            StringBuilder code = new StringBuilder();
            while (th.hasNext())
                code.append(th.next()).append(" ");
            mainClass.methods.put("main", new XJLNMethod(new MatchedList<>(), false, "void", new String[]{code.toString()}));
        }else
            mainClass.methods.put("main", new XJLNMethod(new MatchedList<>(), false, "void", parseCode()));
    }

    private void parseDef(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        className = th.assertToken(Token.Type.IDENTIFIER).s();
        className = Compiler.validateName(path + "." + className);

        if(th.current().s().equalsIgnoreCase("main"))
            throw new RuntimeException("name \"main\" is not allowed for " + className);

        if(th.assertToken("=", "[", "(").s().equals("=")) parseEnumDef(th);
        else if(th.current().equals("[")) parseClassDef(th);
        else{
            className = null;
            parseMethodDef(line, true);
        }
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
        MatchedList<String, XJLNVariable> parameter = parseParameterList(th.getInBracket());
        ArrayList<String> supers = new ArrayList<>();

        current = new XJLNClass(parameter, supers.toArray(new String[0]), uses);

        String line = sc.nextLine().trim();
        while (!line.equals("end")) {
            if (!line.equals("") && !line.startsWith("#")) {
                if(line.startsWith("def "))
                    parseMethodDef(line, false);
                else
                    parseFieldDef(line);
            }
            if(!sc.hasNextLine())
                throw new RuntimeException("class " + path + "/" + className + " was not class");
            line = sc.nextLine().trim();
        }
    }

    private void parseFieldDef(String line){
        TokenHandler th = Lexer.toToken(line);

        boolean inner = false, constant = false;

        String type = th.assertToken(Token.Type.IDENTIFIER).s();
        if(type.equals("inner")){
            inner = true;
            type = th.assertToken(Token.Type.IDENTIFIER).s();
        }
        if(type.equals("const")){
            constant = true;
            type = th.assertToken(Token.Type.IDENTIFIER).s();
        }
        type = validateType(type);
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(current instanceof XJLNClass)
            ((XJLNClass) current).addField(name, new XJLNVariable(inner, constant, type));
        else
            throw new RuntimeException("internal compiler error at: " + line);
    }

    private void parseMethodDef(String line, boolean main){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        String name = th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR).s();
        boolean inner = false;
        if(name.equalsIgnoreCase("main"))
            throw new RuntimeException("name \"main\" is not allowed as method name in " + className);
        if(name.equals("inner")){
            inner = true;
            name = th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR).s();
        }
        th.assertToken("(");
        MatchedList<String, XJLNVariable> parameter = parseParameterList(th.getInBracket());

        if(Lexer.isOperator(name)){
            if(parameter.size() >= 2)
                throw new RuntimeException("for Method " + name + " is only one parameter allowed");
            name = Compiler.toIdentifier(name);
        }

        String returnType = "void";
        String code = null;

        if(th.hasNext()){
            if(th.assertToken(":", "=", "->").equals(":")){
                th.assertToken(":");
                returnType = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
                if(th.hasNext())
                    th.assertToken("=", "->");
            }

            if(th.current().equals("=")){
                th.assertHasNext();
                StringBuilder sb = new StringBuilder("return ");
                while (th.hasNext())
                    sb.append(th.next()).append(" ");
                code = sb.toString();
            }else if(th.current().equals("->")){
                th.assertHasNext();
                StringBuilder sb = new StringBuilder();
                while (th.hasNext())
                    sb.append(th.next()).append(" ");
                code = sb.toString();
            }
        }

        if(main) {
            if(mainClass == null)
                mainClass = new XJLNClass(new MatchedList<>(), new String[0], uses);
            mainClass.addMethod(name, new XJLNMethod(parameter, inner, returnType, code == null ? parseCode() : new String[]{code}));
        }else if(current instanceof XJLNClass)
            ((XJLNClass) current).addMethod(name, new XJLNMethod(parameter, inner, returnType, code == null ? parseCode() : new String[]{code}));
        else
            throw new RuntimeException("internal compiler error at method " + name + " definition");
    }

    private String[] parseCode(){
        ArrayList<String> code = new ArrayList<>();
        int i = 1;
        while (i > 0 && sc.hasNextLine()) {
            String line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if(line.equals("end")) i--;
                if((line.startsWith("if ") || line.startsWith("while ")) && !line.contains("->")) i++;
                if(i > 0) code.add(line);
            }
        }

        if(i > 0)
            throw new RuntimeException("method in class " + className + " was not closed");

        return code.toArray(new String[0]);
    }

    private MatchedList<String, XJLNVariable> parseParameterList(TokenHandler th){
        MatchedList<String, XJLNVariable> paraList = new MatchedList<>();

        if(th.length() == 0)
            return paraList;

        if(th.length() == 1)
            throw new RuntimeException("illegal argument in definition of class " + className);

        String type, name;

        while (th.hasNext()){
            type = th.assertToken(Token.Type.IDENTIFIER).s();
            name = th.assertToken(Token.Type.IDENTIFIER).s();

            paraList.add(name, new XJLNVariable(type));

            if(th.hasNext()){
                th.assertToken(",");
                th.assertHasNext();
            }
        }

        return paraList;
    }

    private String validateType(String type){
        if(Compiler.PRIMITIVES.contains(type)) return type;
        if(type.equals("var")) return "java/lang/Object";
        if(uses.containsKey(type)) return uses.get(type);
        return path + "/" + type;
    }
}
