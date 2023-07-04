package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class Lexer {

    public static TokenHandler toToken(String line){
        List<Token> tokens = new ArrayList<>();
        char[] chars = line.toCharArray();
        StringBuilder value;

        for(int i = 0; i < chars.length; i++){

            if(Character.isDigit(chars[i])){

                value = new StringBuilder();
                value.append(chars[i]);

                while(i + 1 < chars.length && Character.isDigit(chars[i+1])){
                    i++;
                    value.append(chars[i]);
                }

                if(i + 1 < chars.length && chars[i+1] == '.'){
                    i++;
                    value.append(chars[i]);
                    while(i + 1 < chars.length && Character.isDigit(chars[i+1])){
                        i++;
                        value.append(chars[i]);
                    }
                }

                tokens.add(new Token(value.toString(), Token.Type.NUMBER));

            }else if(Character.isLetter(chars[i])){

                value = new StringBuilder();
                value.append(chars[i]);

                while(i + 1 < chars.length && (Character.isDigit(chars[i+1]) || Character.isLetter(chars[i+1]))){
                    i++;
                    value.append(chars[i]);
                }

                tokens.add(new Token(value.toString(), Token.Type.IDENTIFIER));

            }else if(isOperator(chars[i])){

                value = new StringBuilder();
                value.append(chars[i]);

                while(i + 1 < chars.length && isOperator(chars[i+1])){
                    i++;
                    value.append(chars[i]);
                }

                tokens.add(new Token(value.toString(), Token.Type.OPERATOR));

            }else if(chars[i] == '"'){

                value = new StringBuilder();
                value.append(chars[i]);

                while(chars[i+1] != '"'){
                    i++;
                    value.append(chars[i]);
                }

                i++;

                tokens.add(new Token(value.append("\"").toString(), Token.Type.STRING));

            }else if(!Set.of('\n', '\r', '\t', ' ').contains(chars[i])){

                tokens.add(new Token(String.valueOf(chars[i]), Set.of('$', '§').contains(chars[i]) ? Token.Type.IDENTIFIER : Token.Type.SIMPLE));

            }

        }

        return new TokenHandler(tokens);
    }

    private static boolean isOperator(char c){
        return String.valueOf(c).matches("[-+*/!=<>%&|]");
    }
}
