package com.github.xjln.compiler;

import java.util.ArrayList;

final class SyntacticParser {

    TokenHandler token;
    ArrayList<AST> ast;
    String[] code;
    int line;

    AST[] parseAst(String allCode){
        if(allCode == null || allCode.trim().equals(""))
            return new AST[0];

        code = allCode.split("\n");
        line = 0;
        ast = new ArrayList<>();

        nextLine();

        ast.add(parseCalc());

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(){
        AST.Calc calc = new AST.Calc();
        AST.Value value = parseValue();

        while (token.hasNext()){
            if((token.next().t() != Token.Type.OPERATOR) || ((token.current().t() == Token.Type.OPERATOR) && token.current().equals("=>"))){
                if(calc.right == null)
                    calc.value = value;

                token.last();
                return calc;
            }

            String opp = token.current().s();


        }

        return calc;
    }

    AST.Value parseValue() {
        AST.Value value = new AST.Value();

        switch (token.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG -> {
                value.token = token.current();
                value.type = token.current().t().toString();
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

    void nextLine(){
        if(line >= code.length)
            throw new RuntimeException("internal compiler error");

        token = Lexer.lex(code[line]);
        line++;
    }
}
