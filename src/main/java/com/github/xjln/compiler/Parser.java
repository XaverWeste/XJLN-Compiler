package com.github.xjln.compiler;

import com.github.xjln.lang.*;

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
    private Compilable current;
    private String path;
    private String className;
    private Scanner sc;

    public Parser(){
        primitives = Set.of("int", "double", "long", "float", "boolean", "char", "byte", "short");
        lexer = new Lexer();
    }

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
        TokenHandler th = lexer.toToken(line);
        th.assertToken("use");

        StringBuilder use = new StringBuilder();
        use.append(th.assertToken(Token.Type.IDENTIFIER).s());

        while (th.hasNext() && th.assertToken(Token.Type.IDENTIFIER, Token.Type.OPERATOR).t().equals(Token.Type.OPERATOR)){
            TokenHandler.assertToken(th.current(), "/");
            use.append("/");
            use.append(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertHasNext();
        }

        String as = null;

        if(use.toString().contains("/") || th.current().t() == Token.Type.IDENTIFIER){
            TokenHandler.assertToken(th.current(), "as");
            as = th.assertToken(Token.Type.IDENTIFIER).s();
        }

        if(as == null) as = use.toString().split("/")[use.toString().split("/").length - 1];

        if(uses.containsKey(as)){
            System.out.println("[WARNING] alias " + as + " was already defined in " + path);
            uses.replace(as, use.toString());
        }else uses.put(as, use.toString());
    }

    private void parseDef(String line){
        TokenHandler th = lexer.toToken(line);
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
        TokenHandler parameterList = th.getInBracket();
        String[] parameter = parseParameterList(parameterList);
        ArrayList<String> superClasses = new ArrayList<>();

        if(th.hasNext() && th.assertToken("->", "=>").equals("=>")){
            superClasses.add(validateType(th.assertToken(Token.Type.IDENTIFIER).s()));
            while (th.hasNext() && th.assertToken(",", "->").equals(",")){
                superClasses.add(validateType(th.assertToken(Token.Type.IDENTIFIER).s()));
            }
            if(th.current().equals("->")){
                current = new XJLNClass(parameter, superClasses.toArray(new String[0]), th.assertToken(Token.Type.IDENTIFIER).s() + " ()V");
                th.assertToken("(");
                th.assertToken(")");
                th.assertNull();
            }
        }else{
            current = new XJLNClass(parameter, new String[0], th.assertToken(Token.Type.IDENTIFIER).s() + " ()V");
            th.assertToken("(");
            th.assertToken(")");
            th.assertNull();
        }

        String line = "";
        while(sc.hasNextLine() && !line.equals("end")){
            line = sc.nextLine().trim();
            if(!line.equals("") && !line.startsWith("#") && !line.equals("end")){
                if(line.startsWith("def ")) parseMethodDef(line);
                else parseFieldDef(line);
            }
        }
        if(!line.equals("end")) throw new RuntimeException("class definition was not closed in class " + className);
    }

    private void parseFieldDef(String line){
        boolean inner = false, constant = false;
        ArrayList<String> types = new ArrayList<>();
        String value = null;
        String name;

        TokenHandler th = lexer.toToken(line);
        String latest = th.assertToken(Token.Type.IDENTIFIER, Token.Type.SIMPLE).s();

        if(latest.equals("inner")){
            inner = true;
            latest = th.assertToken(Token.Type.IDENTIFIER, Token.Type.SIMPLE).s();
        }

        if(latest.equals("const")){
            constant = true;
            latest = th.assertToken(Token.Type.IDENTIFIER, Token.Type.SIMPLE).s();
        }

        if(th.current().t() == Token.Type.SIMPLE){
            TokenHandler.assertToken(th.current(), "{");
            TokenHandler typedef = th.getInBracket();
            while (typedef.hasNext()){
                types.add(validateType(typedef.assertToken(Token.Type.IDENTIFIER).s()));
                if(typedef.hasNext()){
                    typedef.assertToken("|");
                    typedef.assertHasNext();
                }
            }
        }else types.add(validateType(latest));

        name = th.assertToken(Token.Type.IDENTIFIER).s();
        if(th.hasNext()){
            //TODO
        }


        if(current instanceof XJLNClass){
            ((XJLNClass) current).fields.put(name, new XJLNVariable(inner, constant, types.toArray(new String[0]), null, null));
        }else throw new RuntimeException("internal Compiler error");
    }

    private void parseMethodDef(String line){
        TokenHandler th = lexer.toToken(line);
        th.assertToken("def");
        String name = th.assertToken(Token.Type.IDENTIFIER).s();
        boolean inner = name.equals("inner");
        if(inner) name = th.assertToken(Token.Type.IDENTIFIER).s();
        th.assertToken("(");
        String[] parameter = parseParameterList(th.getInBracket());
        if(parameter == null) throw new RuntimeException("illegal argument in " + th);
        String returnType = "void";
        if(th.hasNext()){
            th.assertToken(":");
            th.assertToken(":");
            returnType = validateType(th.assertToken(Token.Type.IDENTIFIER).s());
            th.assertNull();
        }

        StringBuilder code = new StringBuilder();
        int i = 1;
        while(sc.hasNextLine() && i > 0){
            line = sc.nextLine().trim();
            if(!line.equals("") && !line.startsWith("#")){
                if(Set.of("if", "while", "for").contains(line.split(" ")[0])) i++;
                if(line.equals("end")) i--;
                if(i > 0) code.append(line).append("\n");
            }
        }
        if(i > 0) throw new RuntimeException("method " + name + " was not closed in class " + className);

        if(current instanceof XJLNClass){
            name = name + " " + Compiler.toDesc(parameter, returnType);
            if(((XJLNClass) current).methods.containsKey(name)) throw new RuntimeException("method " + name + " already exists in " + className);
            ((XJLNClass) current).methods.put(name, new XJLNMethod(parameter, inner, returnType, parseCode(code.toString())));
        }else throw new RuntimeException("internal Compiler error");
    }

    private String[] parseParameterList(TokenHandler parameterList){
        ArrayList<String> parameter = new ArrayList<>();

        if(parameterList.hasNext() && parameterList.next().equals("/")){
            parameterList.assertNull();
            return null;
        }else parameterList.toFirst();

        String type, name, names = "", value = null;

        while (parameterList.hasNext()){
            type = parameterList.assertToken(Token.Type.IDENTIFIER).s();
            type = validateType(type);

            name = parameterList.assertToken(Token.Type.IDENTIFIER).s();
            if(names.contains(" " + name + " ")) throw new RuntimeException(name + " is already defined in (" + parameterList + ")");
            else names += " " + name + " ";

            if(parameterList.hasNext()) {
                if (parameterList.assertToken("=", ",").equals("=")) {
                    value = parameterList.next().s();
                    if(parameterList.hasNext()) parameterList.assertToken(",");
                }

                if(parameterList.current().equals(",")) parameterList.assertHasNext();
            }

            parameter.add(type + " " + name + (value == null ? "" : " " + value));
        }

        return parameter.toArray(new String[0]);
    }

    private String validateType(String type){
        if(primitives.contains(type)) return type;
        if(type.equals("var")) return "java/lang/Object";
        return uses.getOrDefault(type, type);
    }

    public AST[] parseCode(String code){
        ArrayList<AST> ast = new ArrayList<>();
        String[] lines = code.split("\n");

        for(int j = 0;j < lines.length;j++){
            String line = lines[j];
            if(line.startsWith("if ") || line.startsWith("while ")){
                boolean whil = line.startsWith("while ");
                String condition = line.split(" ", 2)[1];

                StringBuilder content = new StringBuilder();
                int i = 1;
                while(j < line.length() && i > 0){
                    line = lines[j];
                    if(!line.equals("") && !line.startsWith("#")){
                        if(Set.of("if", "while", "for").contains(line.split(" ")[0])) i++;
                        if(line.equals("end")) i--;
                        if(i > 0) content.append(line).append("\n");
                    }
                }

                if(i > 0) throw new RuntimeException("illegal argument");

                ast.add(whil ? new AST.While(condition, parseCode(code)) : new AST.If(condition, parseCode(code)));
            }else ast.add(new AST.Statement(line));
        }

        return ast.toArray(new AST[0]);
    }

    public AST.Calculation parseCalculation(){
        return null;
    }
}
