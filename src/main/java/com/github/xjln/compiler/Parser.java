package com.github.xjln.compiler;

import com.github.xjln.lang.XJLNClass;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import java.util.Set;

class Parser {
    private final Set<String> primitives;
    public final Lexer lexer;

    private HashMap<String, String> uses;
    private XJLNClass current;
    private String path;
    private String className;
    private Scanner sc;

    public Parser(){
        primitives = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
        lexer = new Lexer();
    }

    public HashMap<String, XJLNClass> parseFile(File file) {
        HashMap<String, XJLNClass> classes = new HashMap<>();
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
                    classes.put(className, current);
                } else throw new RuntimeException("illegal argument in: " + line);
            }
        }

        return classes;
    }

    private void resetUse(){
        uses = new HashMap<>();
        uses.put("boolean", "xjln/core/Boolean/boolean");
        uses.put("byte", "xjln/core/Byte/byte");
        uses.put("char", "xjln/core/Char/char");
        uses.put("double", "xjln/core/Double/double");
        uses.put("float", "xjln/core/Float/float");
        uses.put("int", "xjln/core/Int/int");
        uses.put("long", "xjln/core/Long/long");
        uses.put("short", "xjln/core/Short/short");
    }

    private void parseUseDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("use");

        StringBuilder use = new StringBuilder();
        use.append(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext() && th.assertToken(Token.Type.IDENTIFIER, Token.Type.SIMPLE).t().equals(Token.Type.SIMPLE)){
            TokenHandler.assertToken(th.current(), "/");
            use.append("/");
            use.append(th.assertToken(Token.Type.IDENTIFIER));
            th.assertHasNext();
        }

        String as = null;

        if(use.toString().contains("/") || th.current().t() == Token.Type.IDENTIFIER){
            TokenHandler.assertToken(th.current(), "as");
            as = th.assertToken(Token.Type.IDENTIFIER).s();
        }

        if(as == null) as = use.toString().split("/")[use.toString().split("/").length - 1];

        if(uses.containsKey(as)){
            System.out.println("[WARNING] " + as + " is already defined in " + path);
            uses.replace(as, use.toString());
        }else uses.put(as, use.toString());
    }

    private void parseDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("def");
        className = th.assertToken(Token.Type.IDENTIFIER).s();

        if(th.assertToken("=", "[").s().equals("=")) parseEnumDef(th);
        else parseClassDef(th);
    }

    private void parseEnumDef(TokenHandler th){
        ArrayList<String> values = new ArrayList<>();
        values.add(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext()){
            th.assertToken("|");
            if(values.contains(th.assertToken(Token.Type.IDENTIFIER).s())) throw new RuntimeException("value " + th.current().s() + " already exist for enum " + path + "/" + className);
            values.add(th.current().s());
        }
    }

    private void parseClassDef(TokenHandler th){

    }
}
