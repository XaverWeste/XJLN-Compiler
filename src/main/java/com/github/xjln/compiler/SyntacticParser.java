package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.Set;

final class SyntacticParser {

    static final Set<String> BOOL_OPERATORS = Set.of("==", "!=", "|", "&");
    static final Set<String> NUMBER_OPERATORS = Set.of("+", "-", "*", "/"); //TODO

    private TokenHandler th;
    private String[] code;
    private int line;

    AST.Calc parseCalc(String calc){
        th = Lexer.lex(calc);
        AST.Calc result = parseCalc();
        th.assertNull();
        return result;
    }

    AST[] parseAst(String allCode){
        if(allCode == null || allCode.trim().equals(""))
            return new AST[0];

        code = allCode.split("\n");
        line = 0;
        ArrayList<AST> ast = new ArrayList<>();

        while(line < code.length){
            nextLine();
            if(th.next().equals("return")){
                AST.Return statement = new AST.Return();
                statement.calc = parseCalc();
                statement.type = statement.calc.type;
                ast.add(statement);
            }
        }

        return ast.toArray(new AST[0]);
    }

    AST.Calc parseCalc(){
        AST.Calc calc;
        if(th.next().equals("(")){
            calc = parseCalc();
            th.assertToken(")");
        }else{
            th.last();
            calc = new AST.Calc();
            calc.value = parseValue();
            calc.type = calc.value.type;
        }

        while(th.hasNext()){
            if(th.next().t() != Token.Type.OPERATOR && !(th.current().t() == Token.Type.OPERATOR && th.current().equals("->"))){
                th.last();
                return calc;
            }

            calc.setRight();

            calc.opp = th.current().s();
            th.assertHasNext();

            if(th.next().equals("(")){
                calc.left = parseCalc();
                th.assertToken(")");

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.left.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.left.type + " and " + calc.value.type);

                calc.type = returnType;
            }else{
                th.last();
                calc.value = parseValue();

                String returnType = Compiler.getOperatorReturnType(calc.right.type, calc.value.type, calc.opp);

                if(returnType == null)
                    throw new RuntimeException("Operator " + calc.opp + " is not defined for " + calc.right.type + " and " + calc.value.type);

                calc.type = returnType;
            }
        }

        return calc;
    }

    AST.Value parseValue() {
        AST.Value value = new AST.Value();

        switch (th.next().t()){
            case INTEGER, DOUBLE, FLOAT, LONG, SHORT, CHAR -> {
                value.token = th.current();
                value.type = th.current().t().toString();
            }
            case IDENTIFIER -> {
                if(th.current().equals("true") || th.current().equals("false")){
                    value.token = th.current();
                    value.type = "boolean";
                }else throw new RuntimeException("illegal argument");
            }
            default -> throw new RuntimeException("illegal argument"); //TODO
        }

        return value;
    }

    private void nextLine(){
        if(line >= code.length)
            throw new RuntimeException("internal compiler error");

        th = Lexer.lex(code[line]);
        line++;
    }
}
