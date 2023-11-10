package com.github.xjln.compiler;

import com.github.xjln.lang.Compilable;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;

public final class Parser {

    private HashMap<String, Compilable> result;
    private Scanner scanner;

    private String file;
    private int line;
    private TokenHandler token;

    public HashMap<String, Compilable> parseFile(File src) throws FileNotFoundException {
        result = new HashMap<>();
        scanner = new Scanner(src);

        if(!scanner.hasNextLine())
            return result;

        file = src.getPath();
        line = 1;
        token = Lexer.lex(scanner.nextLine());

        error("illegal character");

        return result;
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
