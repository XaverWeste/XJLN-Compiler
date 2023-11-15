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

public final class Parser {

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
                        try {
                            parseDef();
                        }catch (RuntimeException e){
                            error(e);
                        }
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
        AccessFlag accessFlag = switch (token.assertToken(Token.Type.IDENTIFIER).s()){
            case "public" -> AccessFlag.ACC_PUBLIC;
            case "protected" -> AccessFlag.ACC_PROTECTED;
            case "private" -> AccessFlag.ACC_PRIVATE;
            default -> null;
        };

        if(accessFlag == null){
            accessFlag = AccessFlag.ACC_PUBLIC;
            token.last();
        }

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

        switch (type){
            case "class" -> {
                if(synchronise)
                    throw new RuntimeException("Class should not be synchronised");
                if(statik)
                    throw new RuntimeException("Class should not be static");

                parseClass();
            }
            case "interface" -> {
                if(synchronise)
                    throw new RuntimeException("Interface should not be synchronised");
                if(statik)
                    throw new RuntimeException("Interface should not be static");
                if(finaly)
                    throw new RuntimeException("Interface should not be final");

                parseInterface();
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

                parseData(accessFlag, finaly);
            }
            default -> {
                if(finaly)
                    throw new RuntimeException("method should not be final");

                parseMethod();
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

    private void parseData(AccessFlag accessFlag, boolean finaly){
        String name = token.assertToken(Token.Type.IDENTIFIER).s();

        token.assertToken("=");
        token.assertToken("[");

        TokenHandler th = token.getInBracket();

        token.assertNull();

        MatchedList<String, XJLNField> fields = new MatchedList<>();
        while(th.hasNext()){
            boolean constant = false;
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

    private void parseInterface(){
        //TODO
    }

    private void parseClass(){
        //TODO
    }

    private void parseMethod(){
        //TODO
    }

    private void parseField(){
        token.toFirst();

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

        token.assertNull(); //TODO compile initValue and remove statement

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
