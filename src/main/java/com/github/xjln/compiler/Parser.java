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

        if(th.length() == 1) {
            if (th.next().equals("/")) {
                return null;
            } else {
                throw new RuntimeException("illegal argument in definition of class " + className + " in: " + th);
            }
        }

        String type, name;

        while (th.hasNext()){
            type = th.assertToken(Token.Type.IDENTIFIER).s();
            name = th.assertToken(Token.Type.IDENTIFIER).s();

            paraList.add(name, new XJLNVariable(validateType(type)));

            if(th.hasNext()){
                th.assertToken(",");
                th.assertHasNext();
            }
        }

        return paraList;
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
