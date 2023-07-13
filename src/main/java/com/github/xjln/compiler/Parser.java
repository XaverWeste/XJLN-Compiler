package com.github.xjln.compiler;

import com.github.xjln.lang.*;
import com.github.xjln.utility.SearchList;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

class Parser {

    private HashMap<String, String> uses;
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

        String line;
        while (sc.hasNextLine()) {
            line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if (line.startsWith("use")) parseUseDef(line);
                else if(line.startsWith("def")){
                    parseDef(line);
                    if(uses.containsKey(className)) throw new RuntimeException("alias " + className + " already exist in: " + line);
                    classes.put(className, current);
                } else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        return classes;
    }

    private void resetUse(){
        uses = new HashMap<>();
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
            if(uses.containsKey(as)) throw new RuntimeException("alias " + as + " already exist in: " + line);
            th.assertNull();
        }else
            th.assertNull();

        if(as != null)
            uses.put(as, from + "/" + use.get(0));
        else
            for(String s:use) {
                if(uses.containsKey(s)) throw new RuntimeException("alias " + s + " already exist in: " + line);
                uses.put(s, from + "/" + s);
            }
    }

    private void parseDef(String line){
        TokenHandler th = Lexer.toToken(line);
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
        SearchList<String, XJLNVariable> parameter = parseParameterList(th.getInBracket());
        String constructor = null;
        ArrayList<String> supers = new ArrayList<>();

        if(th.hasNext()){
            if(th.assertToken("->").equals("->")){
                constructor = th.assertToken(Token.Type.IDENTIFIER).s();

                th.assertToken("(");
                th.assertToken(")");
            }
        }

        current = new XJLNClass(parameter, supers.toArray(new String[0]), constructor);

        String line = sc.nextLine().trim();
        while (!line.equals("end") && sc.hasNextLine()) {
            if (!line.equals("") && !line.startsWith("#")) {
                if(line.startsWith("def "))
                    parseMethodDef(line);
                else
                    parseFieldDef(line);
            }
            line = sc.nextLine().trim();
        }
    }

    private void parseFieldDef(String line){
        TokenHandler th = Lexer.toToken(line);

        String type = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
        String name = th.assertToken(Token.Type.IDENTIFIER).s();

        if(current instanceof XJLNClass)
            ((XJLNClass) current).addField(name, new XJLNVariable(type));
        else
            throw new RuntimeException("internal compiler error at: " + line);
    }

    private void parseMethodDef(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        String name = th.assertToken(Token.Type.IDENTIFIER).s();
        th.assertToken("(");
        SearchList<String, XJLNVariable> parameter = parseParameterList(th.getInBracket());

        String returnType = "void";
        if(th.hasNext()){
            th.assertToken(":");
            th.assertToken(":");
            returnType = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertNull();
        }

        ArrayList<String> code = new ArrayList<>();
        int i = 1;
        while (i > 0 && sc.hasNextLine()) {
            line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if(line.equals("end")) i--;
                if(line.startsWith("if ") || line.startsWith("while ")) i++;
                if(i > 0) code.add(line);
            }
        }

        if(i > 0)
            throw new RuntimeException("method " + name + " in class " + path + "/" + className + " was not closed");

        if(current instanceof XJLNClass){
            ((XJLNClass) current).addMethod(name, new XJLNMethod(parameter, false, returnType, code.toArray(new String[0])));
        }else
            throw new RuntimeException("internal compiler error at method " + name + " definition");
    }

    private SearchList<String, XJLNVariable> parseParameterList(TokenHandler th){
        SearchList<String, XJLNVariable> paraList = new SearchList<>();

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
