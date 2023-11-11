package com.github.xjln.compiler;

import com.github.xjln.lang.Compilable;
import com.github.xjln.lang.XJLNFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public final class Parser {

    private HashMap<String, Compilable> classes;
    private HashMap<String, String> uses;
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

        file = src.getPath().substring(0, src.getPath().length() - 5);
        line = 1;
        token = Lexer.lex(scanner.nextLine());

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
                        error("illegal argument");
                    }
                    default -> {
                        error("illegal argument");
                    }
                }
            }
        }

        return new XJLNFile(classes, uses);
    }

    private void parseUse(){
        ArrayList<String> use = new ArrayList<>();
        String from;
        String as;

        token.assertToken("/", Token.Type.IDENTIFIER);

        if(token.current().equals("/"))
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

                from = parsePath();
            }
        }

        if(token.hasNext()){
            token.assertToken("as");
            as = token.assertToken(Token.Type.IDENTIFIER).s();

            token.assertNull();
        }else
            as = null;

        if(use.isEmpty()){
            if(as == null)
                as = from.substring(from.length() - from.split("/")[from.split("/").length - 1].length() + 1);

            from = Compiler.validateName(from);

            if(uses.containsKey("as"))
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

        if(token.current().equals("/")){
            token.assertToken("/");

            path.append(file, 0, file.length() - file.split("/")[file.split("/").length - 1].length());

            token.assertToken(Token.Type.IDENTIFIER);
        }

        path.append(token.current());

        while (token.hasNext() && token.next().equals("/"))
            path.append("/").append(token.assertToken(Token.Type.IDENTIFIER));

        if(token.current().equals("/"))
            token.last();

        return path.toString();
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
