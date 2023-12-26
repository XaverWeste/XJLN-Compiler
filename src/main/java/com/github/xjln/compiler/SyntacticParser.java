package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.Set;

final class SyntacticParser {

    private final Set<String> boolOperators = Set.of("==", "!=", "|", "&");
    private final Set<String> numberOperators = Set.of("+", "-", "*", "/"); //TODO

    private TokenHandler token;
    private ArrayList<AST> ast;
    private String[] code;
    private int line;

    AST[] parseAst(String allCode){
        if(allCode == null || allCode.trim().equals(""))
            return new AST[0];

        code = allCode.split("\n");
        line = 0;
        ast = new ArrayList<>();

        nextLine();

        ast.add(parseCalc());
        token.assertNull();

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(){
        AST.Calc calc = new AST.Calc();
        calc.value = parseValue();
        calc.type = calc.value.type;

        while(token.hasNext()){
            if(token.next().t() != Token.Type.OPERATOR && !(token.current().t() == Token.Type.OPERATOR && token.current().equals("->"))){
                token.last();
                return calc;
            }

            calc.setRight();

            calc.opp = token.current().s();
            token.assertHasNext();

            //TODO brackets

            calc.value = parseValue();

            checkCalc(calc.right.type, calc.value.type, calc.opp);
        }

        return calc;
    }

    AST.Value parseValue() {
        AST.Value value = new AST.Value();

        switch (token.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG, SHORT, CHAR -> {
                value.token = token.current();
                value.type = token.current().t().toString();
            }
            case IDENTIFIER -> {
                if(token.current().equals("true") || token.current().equals("false")){
                    value.token = token.current();
                    value.type = "boolean";
                }else throw new RuntimeException("illegal argument");
            }
            default -> throw new RuntimeException("illegal argument"); //TODO
        }

        return value;
    }

    /*
    AST.CalcArg parseCalcArg(){
        AST.CalcArg arg = new AST.CalcArg();

        switch (token.assertToken(Token.Type.NUMBER).t()){ //TODO
            case NUMBER, CHAR, STRING -> arg.token = token.current();
            case IDENTIFIER -> {
                //TODO
            }
            case OPERATOR -> {
                arg.opp = token.current().s();
                //TODO
            }
            case SIMPLE -> {
                if(token.current().equals("(")){
                    //TODO
                }else throw new RuntimeException("illegal argument");
            }
        }

        return arg;
    }

     */
    private void checkCalc(String type1, String type2, String opp){
        switch (type1){
            case "boolean" -> {
                if(!boolOperators.contains(opp) || !type2.equals("boolean"))
                    notDefinedException(type1, type2, opp);
            }
            case "int", "double", "char", "byte", "short", "float", "long" -> {
                if(!numberOperators.contains(opp) || !type2.equals(type1))
                    notDefinedException(type1, type2, opp);
            }
            default -> notDefinedException(type1, type2, opp);
        }
    }

    private void notDefinedException(String type1, String type2, String opp){
        throw new RuntimeException("Operator " + opp + " is not defined for " + type1 + " and " + type2);
    }

    private void nextLine(){
        if(line >= code.length)
            throw new RuntimeException("internal compiler error");

        token = Lexer.lex(code[line]);
        line++;
    }
}
