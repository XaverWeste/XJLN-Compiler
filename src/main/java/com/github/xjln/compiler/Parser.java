package com.github.xjln.compiler;

import com.github.xjln.bytecode.AccessFlag;
import com.github.xjln.lang.*;
import com.github.xjln.utility.MatchedList;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

final class Parser {

    private HashMap<String, Compilable> classes;
    private HashMap<String, String> uses;
    private XJLNClass main;
    private XJLNClass current;
    private Scanner scanner;

    private String file;
    private int line;
    private TokenHandler token;

    public XJLNFile parseFile(File src) throws FileNotFoundException {
        scanner = new Scanner(src);

        if(!scanner.hasNextLine())
            return null;

        classes = new HashMap<>();
        uses = new HashMap<>();
        main = new XJLNClass(AccessFlag.ACC_PUBLIC, true, true);
        current = null;

        file = src.getPath().substring(0, src.getPath().length() - 5).replace("\\", "/");
        line = 0;

        while (scanner.hasNextLine()){
            nextLine();

            if(token.hasNext()) {
                token.assertToken(Token.Type.IDENTIFIER);

                switch (token.current().s()) {
                    case "use" -> {
                        try {
                            parseUse();
                        }catch (RuntimeException e){
                            error(e);
                        }
                    }
                    case "def" -> {
                        //try {
                            parseDef();
                        //}catch (RuntimeException e){
                        //    error(e);
                        //}
                    }
                    default -> {
                        try {
                            parseField();
                        }catch (RuntimeException e){
                            error(e);
                        }
                    }
                }
            }
        }

        return new XJLNFile(main, classes, uses);
    }

    private void parseUse(){
        ArrayList<String> use = new ArrayList<>();
        String from;
        String as;

        token.assertToken("//", Token.Type.IDENTIFIER);

        if(token.current().equals("//"))
            from = parsePath();
        else{
            if(token.next().equals("/")) {
                token.last();
                from = parsePath();
            }else{
                token.last();
                use.add(token.current().s());

                while (token.assertToken(",", "from").equals(","))
                    use.add(token.assertToken(Token.Type.IDENTIFIER).s());

                token.next();
                from = parsePath();
            }
        }

        if(token.hasNext()){
            token.assertToken("as");
            as = token.assertToken(Token.Type.IDENTIFIER).s();

            token.assertNull();
        }else
            as = null;

        if(use.isEmpty() || use.size() == 1){
            if(as == null)
                as = from.substring(from.length() - from.split("/")[from.split("/").length - 1].length());

            from = Compiler.validateName(from);

            if(uses.containsKey(as))
                throw new RuntimeException("alias " + as + " is already defined");

            uses.put(as, from);
        }else{
            if(as != null)
                throw new RuntimeException("illegal argument");

            for(String s:use){
                if(uses.containsKey(s))
                    throw new RuntimeException("alias " + s + " is already defined");

                uses.put(s, from + "." + s);
            }
        }
    }

    private String parsePath(){
        StringBuilder path = new StringBuilder();

        if(token.current().equals("//")){
            path.append(file, 0, file.length() - file.split("/")[file.split("/").length - 1].length());

            token.assertToken(Token.Type.IDENTIFIER);
        }

        path.append(token.current());

        while (token.hasNext() && token.next().equals("/"))
            path.append("/").append(token.assertToken(Token.Type.IDENTIFIER));

        if(!path.toString().endsWith(token.current().s()))
            token.last();

        return path.toString();
    }

    private void parseDef(){
        AccessFlag accessFlag = getAccessFlag();

        boolean statik      = false;
        boolean finaly      = false;
        boolean abstrakt    = false;
        boolean synchronise = false;
        String type = token.assertToken(Token.Type.IDENTIFIER).s();

        while (Set.of("static", "final", "abstract", "synchronised").contains(type)){
            switch (type){
                case "static"       -> statik      = true;
                case "final"        -> finaly      = true;
                case "abstract"     -> abstrakt    = true;
                case "synchronised" -> synchronise = true;
            }

            type = token.assertToken(Token.Type.IDENTIFIER).s();
        }

        boolean allConst = false;
        if(type.equals("const")) {
            type = token.assertToken("data").s();
            allConst = true;
        }

        switch (type){
            case "class" -> {
                if(synchronise)
                    throw new RuntimeException("Class should not be synchronised");
                if(statik)
                    throw new RuntimeException("Class should not be static");

                parseClass(accessFlag, finaly, abstrakt);
            }
            case "interface" -> {
                if(synchronise)
                    throw new RuntimeException("Interface should not be synchronised");
                if(statik)
                    throw new RuntimeException("Interface should not be static");
                if(finaly)
                    throw new RuntimeException("Interface should not be final");

                parseInterface(accessFlag);
            }
            case "type" -> {
                if(synchronise)
                    throw new RuntimeException("Type should not be synchronised");
                if(statik)
                    throw new RuntimeException("Type should not be static");
                if(abstrakt)
                    throw new RuntimeException("Type should not be abstract");

                parseType(accessFlag);
            }
            case "data" -> {
                if(synchronise)
                    throw new RuntimeException("Data should not be synchronised");
                if(statik)
                    throw new RuntimeException("Data should not be static");
                if(abstrakt)
                    throw new RuntimeException("Data should not be abstract");

                parseData(accessFlag, finaly, allConst);
            }
            default -> {
                if(finaly)
                    throw new RuntimeException("method should not be final");

                parseMethod(accessFlag, statik, abstrakt, synchronise);
            }
        }
    }

    private void parseType(AccessFlag accessFlag){
        ArrayList<String> values = new ArrayList<>();
        String name = token.assertToken(Token.Type.IDENTIFIER).s();

        token.assertToken("=");
        token.assertHasNext();

        while (token.hasNext()){
            String value = token.assertToken(Token.Type.IDENTIFIER).s();

            if(values.contains(value))
                throw new RuntimeException("value is already defined");

            values.add(value);

            if(token.hasNext()){
                token.assertToken("|");
                token.assertHasNext();
            }
        }

        XJLNTypeClass type = new XJLNTypeClass(accessFlag, values.toArray(new String[0]));

        if(classes.containsKey(name))
            throw new RuntimeException("Class is already defined");

        classes.put(name, type);
    }

    private void parseData(AccessFlag accessFlag, boolean finaly, boolean allConst){
        String name = token.assertToken(Token.Type.IDENTIFIER).s();

        token.assertToken("=");
        token.assertToken("[");

        TokenHandler th = token.getInBracket();

        token.assertNull();

        MatchedList<String, XJLNField> fields = new MatchedList<>();
        while(th.hasNext()){
            boolean constant = allConst;
            String fieldType;
            String fieldName;

            fieldType = th.assertToken(Token.Type.IDENTIFIER).s();

            if(fieldType.equals("const")){
                constant = true;
                fieldType = th.assertToken(Token.Type.IDENTIFIER).s();
            }

            fieldName = th.assertToken(Token.Type.IDENTIFIER).s();

            if(fields.hasKey(fieldName))
                throw new RuntimeException("field is already defined");

            fields.add(fieldName, new XJLNField(AccessFlag.ACC_PUBLIC, false, false, false, constant, fieldType, null, line));

            if(th.hasNext()){
                th.assertToken(",");
                th.assertHasNext();
            }
        }

        XJLNDataClass data = new XJLNDataClass(accessFlag, fields, finaly);

        if(classes.containsKey(name))
            throw new RuntimeException("Class is already defined");

        classes.put(name, data);
    }

    private void parseInterface(AccessFlag accessFlagInterface){
        String name = token.assertToken(Token.Type.IDENTIFIER).s();

        token.assertToken("{");
        token.assertNull();

        MatchedList<String, XJLNInterfaceMethod> methods = new MatchedList<>();

        while (scanner.hasNext()){
            nextLine();

            if(!token.isEmpty()){
                if(token.toStringNonMarked().trim().equals("}"))
                    break;

                token.assertToken("def");

                if(token.assertToken(Token.Type.IDENTIFIER).equals("public"))
                    token.assertToken(Token.Type.IDENTIFIER);

                String methodName = token.current().s();

                if(methods.hasKey(methodName))
                    throw new RuntimeException("Method is already defined");

                token.assertToken("(");

                TokenHandler th = token.getInBracket();
                MatchedList<String, String> parameters = new MatchedList<>();
                while(th.hasNext()){
                    String parameterType = th.assertToken(Token.Type.IDENTIFIER).s();
                    String parameterName = th.assertToken(Token.Type.IDENTIFIER).s();

                    if(parameters.hasKey(parameterName))
                        throw new RuntimeException("parameter is already defined");

                    parameters.add(parameterName, parameterType);

                    if(th.hasNext()){
                        th.assertToken(",");
                        th.assertHasNext();
                    }
                }

                String returnType;

                if(token.hasNext()){
                    token.assertToken(":");
                    token.assertToken(":");

                    returnType = token.assertToken(Token.Type.IDENTIFIER).s();
                }else
                    returnType = "void";

                methods.add(methodName, new XJLNInterfaceMethod(returnType, parameters));
            }
        }

        if(!token.toStringNonMarked().trim().equals("}"))
            throw new RuntimeException("Expected }");

        XJLNInterface clazz = new XJLNInterface(accessFlagInterface, methods);

        if(classes.containsKey(name))
            throw new RuntimeException("Class is already defined");

        classes.put(name, clazz);
    }

    private void parseClass(AccessFlag accessFlag, boolean finaly, boolean abstrakt){
        String name = token.assertToken(Token.Type.IDENTIFIER).s();
        current = new XJLNClass(accessFlag, finaly, abstrakt);

        token.assertToken("{");
        token.assertNull();

        while (scanner.hasNext()) {
            nextLine();

            if (!token.isEmpty()) {
                if (token.toStringNonMarked().trim().equals("}"))
                    break;

                if(token.assertToken(Token.Type.IDENTIFIER).s().equals("def")) {
                    AccessFlag methodAccessFlag = getAccessFlag();

                    boolean statik = false;
                    boolean methodAbstrakt = false;
                    boolean synchronise = false;
                    String methodName = token.assertToken(Token.Type.IDENTIFIER).s();

                    while (Set.of("static", "abstract", "synchronised").contains(methodName)){
                        switch (methodName){
                            case "static"       -> statik      = true;
                            case "abstract"     -> abstrakt    = true;
                            case "synchronised" -> synchronise = true;
                        }

                        methodName = token.assertToken(Token.Type.IDENTIFIER).s();
                    }

                    parseMethod(methodAccessFlag, statik, methodAbstrakt, synchronise);
                }else
                    parseField();
            }
        }

        if(!token.toStringNonMarked().trim().equals("}"))
            throw new RuntimeException("Expected }");

        if(classes.containsKey(name))
            throw new RuntimeException("Class is already defined");

        classes.put(name, current);
        current = null;
    }

    private void parseMethod(AccessFlag accessFlag, boolean statik, boolean abstrakt, boolean synchronise){
        token.last();

        int startingLine = line;

        String name = token.assertToken(Token.Type.IDENTIFIER).s();
        token.assertToken("(");

        TokenHandler th = token.getInBracket();
        MatchedList<String, String> parameters = new MatchedList<>();
        while(th.hasNext()){
            String parameterType = th.assertToken(Token.Type.IDENTIFIER).s();
            String parameterName = th.assertToken(Token.Type.IDENTIFIER).s();

            if(parameters.hasKey(parameterName))
                throw new RuntimeException("parameter is already defined");

            parameters.add(parameterName, parameterType);

            if(th.hasNext()){
                th.assertToken(",");
                th.assertHasNext();
            }
        }

        String returnType = "void";

        if(token.hasNext()){
            token.assertToken(":");
            token.assertToken(":");
            returnType = token.assertToken(Token.Type.IDENTIFIER).s();
            token.assertNull();
        }

        if(abstrakt && statik)
            throw new RuntimeException("Method should not be static abstract");

        if((name.equals("init")) && (statik || abstrakt || synchronise))
            throw new RuntimeException("Did not expect modifier");

        StringBuilder code = new StringBuilder();

        if(!abstrakt){
            int i = 1;

            while (scanner.hasNext() && i > 0){
                nextLine();

                if(!token.isEmpty()) {
                    switch (token.assertToken(Token.Type.IDENTIFIER).s()) {
                        case "if", "while", "for" -> i++;
                        case "end" -> {
                            token.assertNull();
                            i--;
                        }
                    }

                    if(i>0)
                        code.append(token.toStringNonMarked()).append("\n");
                }
            }

            if(i > 0)
                throw new RuntimeException("method was not closed");
        }

        XJLNMethod method = new XJLNMethod(accessFlag, returnType, parameters, code.toString(), statik, abstrakt, synchronise, line);

        if(current == null)
            main.addStaticMethod(name, method);
        else if(statik)
            current.addStaticMethod(name, method);
        else
            current.addMethod(name, method);
    }

    private void parseField(){
        token.toFirst();

        AccessFlag accessFlag = getAccessFlag();

        boolean statik    = false;
        boolean transiend = false;
        boolean volatil   = false;
        boolean constant  = false;
        String type = token.assertToken(Token.Type.IDENTIFIER).s();

        while(Set.of("static", "transient", "volatile", "const").contains(type)){
            switch (type){
                case "static"    -> statik    = true;
                case "transient" -> transiend = true;
                case "volatile"  -> volatil   = true;
                case "const"     -> constant  = true;
            }

            type = token.assertToken(Token.Type.IDENTIFIER).s();
        }

        if(volatil && constant)
            throw new RuntimeException("Field should not be transient and constant");

        String name = token.assertToken(Token.Type.IDENTIFIER).s();

        StringBuilder initValue = new StringBuilder();
        if(token.hasNext()){
            token.assertToken("=");
            token.assertHasNext();

            while (token.hasNext())
                initValue.append(token.next().s());
        }

        XJLNField field = new XJLNField(accessFlag, statik, transiend, volatil, constant, type, initValue.toString(), line);

        if(current == null)
            main.addStaticField(name, field);
        else if(statik)
            current.addStaticField(name, field);
        else
            current.addField(name, field);
    }

    private AccessFlag getAccessFlag(){
        AccessFlag accessFlag = switch (token.assertToken(Token.Type.IDENTIFIER).s()){
            case "public"    -> AccessFlag.ACC_PUBLIC;
            case "protected" -> AccessFlag.ACC_PROTECTED;
            case "private"   -> AccessFlag.ACC_PRIVATE;
            default          -> null;
        };

        if(accessFlag == null){
            accessFlag = AccessFlag.ACC_PUBLIC;
            token.last();
        }

        return accessFlag;
    }

    private void nextLine(){
        if(scanner.hasNextLine()) {
            line++;
            token = Lexer.lex(scanner.nextLine());
        }else
            throw new RuntimeException("Internal Compiler Error");
    }

    private void error(String message){
        throw new RuntimeException(message + " in: " + file + " :" + line);
    }

    private void error(RuntimeException e){
        error(e.getMessage());
    }
}
