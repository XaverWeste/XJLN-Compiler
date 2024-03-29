package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class Lexer {

    public static TokenHandler lex(String line){
        List<Token> tokens = new ArrayList<>();
        char[] chars = line.toCharArray();
        StringBuilder value;

        for(int i = 0; i < chars.length; i++){

            if(chars[i] == '#'){
                i++;

                while (i < chars.length && chars[i] != '#')
                    i++;
            }else if(Character.isDigit(chars[i]) || (chars[i] == '-' && chars.length >= i + 1 && Character.isDigit(chars[i+1]))){

                value = new StringBuilder();
                value.append(chars[i]);

                Token.Type type;

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

                    if(i + 1 < chars.length && chars[i+1] == 'f') {
                        type = Token.Type.FLOAT;
                        value.append('f');
                        i++;
                    }else{
                        type = Token.Type.DOUBLE;

                        if(i + 1 < chars.length && chars[i+1] == 'd')
                            i++;
                    }
                }else{
                    if(i + 1 < chars.length && chars[i+1] == 'l') {
                        type = Token.Type.LONG;
                        value.append('l');
                        i++;
                    }else if(i + 1 < chars.length && chars[i+1] == 's') {
                        type = Token.Type.SHORT;
                        value.append('s');
                        i++;
                    }else{
                        type = Token.Type.INTEGER;

                        if(i + 1 < chars.length && chars[i+1] == 'i')
                            i++;
                    }
                }

                tokens.add(new Token(value.toString(), type));

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

            }else if(chars[i] == '\''){

                if (chars.length >= i + 2 && chars[i + 2] == '\''){
                    tokens.add(new Token("'" + chars[i + 1] + "'", Token.Type.CHAR));

                    i += 3;
                }else{
                    tokens.add(new Token("'", Token.Type.SIMPLE));

                    i++;
                }

            }else if(!Set.of('\n', '\r', '\t', ' ').contains(chars[i])){

                tokens.add(new Token(String.valueOf(chars[i]), Token.Type.SIMPLE));

            }

        }

        return new TokenHandler(tokens);
    }

    private static boolean isOperator(char c){
        return String.valueOf(c).matches("[-+*/!=<>%&|^]");
    }
}
