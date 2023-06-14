package com.github.xjln.compiler;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

class Parser {
    private final Set<String> primitives;
    public final Lexer lexer;

    private HashMap<String, String> uses;
    //private XJLNClass current;
    private String path;
    private String className;
    private Scanner sc;

    public Parser(){
        primitives = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
        lexer = new Lexer();
    }

    public /*HashMap<String, XJLNClass>*/void parseFile(File file){
        //HashMap<String, XJLNClass> classes = new HashMap<>();
        uses = new HashMap<>();
        try {
            sc = new Scanner(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("file " + file.getPath() + " not found");
        }

        path = file.getPath();
        path = path.substring(0, path.length() - 5);

        String line;
        while(sc.hasNextLine()){
            line = sc.nextLine().trim();
            if(!line.equals("") && !line.startsWith("#")){
                if(line.startsWith("use")) parseUseDef(line);
                else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        //return classes;
    }

    private void parseUseDef(String line){

    }
}
