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
    private HashMap<String, Compilable> classes;
    private XJLNClass main;
    private String src;
    private Scanner sc;

    public HashMap<String, Compilable> parseFile(File file) {
        classes = new HashMap<>();
        resetUse();
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file " + file.getPath() + " not found");
        }

        src = file.getPath();
        src = src.substring(0, src.length() - 5);

        String line;
        while (sc.hasNextLine()) {
            line = sc.nextLine().trim();
            if (!line.equals("") && !line.startsWith("#")) {
                if (line.startsWith("use ")) parseUseDef(line);
                else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        HashMap<String, Compilable> result = classes;

        src = null;
        classes = null;
        uses = null;
        main = null;

        return result;
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

    private void parseEnumDef(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        String name = Compiler.validateName(src + "." + th.assertToken(Token.Type.IDENTIFIER).s());
        th.assertToken("=");
        ArrayList<String> values = new ArrayList<>();
        values.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            if(values.contains(th.assertToken(Token.Type.IDENTIFIER).s())) throw new RuntimeException("value " + th.current().s() + " already exist for enum " + name);
            values.add(th.current().s());
        }

        if(classes.containsKey(name))
            throw new RuntimeException("Enum " + name + " already exist");

        classes.put(name, new XJLNEnum(name, values));
    }

    private void parseInterface(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");

        String name = Compiler.validateName(src + "." + th.assertToken(Token.Type.IDENTIFIER).s());;
        th.assertNull();

        HashMap<String, XJLNMethodAbstract> methods = new HashMap<>();

        while (sc.hasNextLine()){
            line = sc.nextLine().trim();

            if(line.equals("end"))
                break;

            if(!line.equals("")) {
                XJLNMethodAbstract method = parseMethod(line, false);
                String desc = Compiler.toDesc(method);

                if(method instanceof XJLNMethod)
                    throw new RuntimeException("Interface " + name + " should not contain non abstract Method " + desc);

                if(methods.containsKey(desc))
                    throw new RuntimeException("Method " + desc + " is already defined in Interface " + name);

                methods.put(desc, method);
            }
        }

        if(!line.equals("end"))
            throw new RuntimeException("Interface " + name + " was not closed");

        if(classes.containsKey(name))
            throw new RuntimeException("Interface " + name + " already exist");

        classes.put(name, new XJLNInterface(methods, uses));
    }

    private void parseRecord(String line){
        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");
        String name = Compiler.validateName(src + "." + th.assertToken(Token.Type.IDENTIFIER).s());
        th.assertToken("=");
        th.assertToken("[");
        MatchedList<String, XJLNParameter> parameter = parseParameterList(th.getInBracket());
        th.assertNull();

        if(classes.containsKey(name))
            throw new RuntimeException("Data Class " + name + " already exist");

        classes.put(name, new XJLNClass(false, name, null, parameter, new String[0], uses));
    }

    private void parseClass(String line){
        boolean abstrakt = false;
        boolean inner = false;
        boolean statik;
        String name;
        ArrayList<String> genericTypes = null;
        ArrayList<String> superClasses = new ArrayList<>();
        HashMap<String, XJLNField> fields = new HashMap<>();
        HashMap<String, XJLNMethodAbstract> methods = new HashMap<>();

        TokenHandler th = Lexer.toToken(line);
        th.assertToken("def");

        if(th.assertToken(Token.Type.IDENTIFIER).equals("abstract")){
            abstrakt = true;
            th.assertToken(Token.Type.IDENTIFIER);
        }

        if(th.current().equals("inner")){
            inner = true;
            th.assertToken(Token.Type.IDENTIFIER);
        }

        name = Compiler.validateName(src + "." + th.current().s());

        if(th.assertToken("<", "[").equals("<")){
            genericTypes = new ArrayList<>();
            genericTypes.add(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertHasNext();

            while (th.assertToken(">", ",").equals(",")){
                if(genericTypes.contains(th.assertToken(Token.Type.IDENTIFIER).s()))
                    throw new RuntimeException("Generic Type " + th.current() + " is already defined in Class " + name);

                genericTypes.add(th.current().s());
                th.assertHasNext();
            }
            th.assertToken("[");
        }

        MatchedList<String, XJLNParameter> parameter = parseParameterList(th.getInBracket());
        statik = parameter == null;

        if(statik)
            th.assertNull();
        else if(th.hasNext()){
            th.assertToken("=>");
            th.assertHasNext();

            while(th.hasNext()){
                superClasses.add(th.assertToken(Token.Type.IDENTIFIER) + "[" + th.getInBracket().toString() + "]");
                if(th.hasNext()){
                    th.assertToken(",");
                    th.assertHasNext();
                }
            }
        }

        while (sc.hasNextLine()) {
            line = sc.nextLine().trim();

            if(!line.equals("")){
                if(line.startsWith("def ")){
                    XJLNMethodAbstract method = parseMethod(line, statik);
                    String desc = Compiler.toDesc(method);

                    if(methods.containsKey(desc))
                        throw new RuntimeException("Method " + desc + " is already defined in Class " + name);

                    methods.put(desc, method);
                }else{
                    XJLNField field = parseField(line, statik);

                    if(fields.containsKey(field.name()))
                        throw new RuntimeException("Field " + field.name() + " is already defined in Class " + name);

                    fields.put(field.name(), field);
                }
            }
        }

        if(!line.equals("end"))
            throw new RuntimeException("Class " + name + " was not closed");

        if(classes.containsKey(name)){
            if(classes.get(name) instanceof XJLNClass){
                XJLNClass clazz = (XJLNClass) classes.get(name);
                if(!clazz.staticMethods.values().isEmpty() || !clazz.staticFields.values().isEmpty())
                    throw new RuntimeException("Class " + name + " already exist");
                else{
                    clazz.staticFields.putAll(fields);
                    for(String desc: methods.keySet()){
                        if(methods.get(desc) instanceof XJLNMethod)
                            clazz.staticMethods.put(desc, (XJLNMethod) methods.get(desc));
                        else
                            throw new RuntimeException("internal Parser error");
                    }
                }
            }else if(classes.get(name) instanceof XJLNClassStatic){
                XJLNClass clazz = new XJLNClass((XJLNClassStatic) classes.get(name), abstrakt, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, superClasses.toArray(new String[0]));
                classes.remove(name);
                classes.put(name, clazz);
            }else
                throw new RuntimeException("Class " + name + " already exist");
        }else{
            if(statik)
                classes.put(name, new XJLNClassStatic(name, uses, fields, methods));
            else
                classes.put(name, new XJLNClass(abstrakt, name, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, superClasses.toArray(new String[0]), uses));
        }
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
            throw new RuntimeException("Expected Type or Value for Field " + name + " in File " + src);

        if(staticContext && constant && initValue == null)
            throw new RuntimeException("Expected Value for Field " + name + " in File " + src);

        return new XJLNField(inner, constant, type, name, initValue);
    }

    private XJLNMethodAbstract parseMethod(String line, boolean staticContext){
        boolean inner = false;
        String name;
        ArrayList<String> genericTypes = null;
        MatchedList<String, XJLNParameter> parameter;
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

            return new XJLNMethod(staticContext, inner, name, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, returnType, code.toArray(new String[0]));
        }else{
            if(th.current().equals("->") || th.current().equals("=") || th.current().equals("{"))
                throw new RuntimeException("Method " + name + " should not be abstract");

            if(staticContext)
                throw new RuntimeException("Method " + name + " should not be abstract in a static Class");

            return new XJLNMethodAbstract(staticContext, inner, name, genericTypes == null ? null : genericTypes.toArray(new String[0]), parameter, returnType);
        }
    }

    private MatchedList<String, XJLNParameter> parseParameterList(TokenHandler th){
        MatchedList<String, XJLNParameter> parameterList = new MatchedList<>();

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

            if(parameterList.hasKey(name))
                throw new RuntimeException("Parameter " + name + " is already defined");

            parameterList.add(name, new XJLNParameter(constant, type, name, value == null ? null : value.toString()));

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
            throw new RuntimeException("method in File " + src + " was not closed");

        return code;
    }

    private String validateType(String type){
        if(Compiler.PRIMITIVES.contains(type)) return type;
        if(uses.containsKey(type)) return uses.get(type);
        if(type.startsWith("[")){
            String[] sa = type.split("\\[");
            return "[".repeat(Math.max(0, sa.length - 1)) + Compiler.validateName(sa[sa.length - 1]);
        }
        return Compiler.validateName(src + "\\" + type);
    }
}
