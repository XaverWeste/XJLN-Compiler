package com.github.xjln.compiler;

import java.util.Set;

public record Token(String s, Type t){

    enum Type{
        SIMPLE, IDENTIFIER, STRING, OPERATOR, CHAR, INTEGER, LONG, DOUBLE, FLOAT, SHORT;

        @Override
        public String toString() {
            return switch (this){
                case FLOAT -> "float";
                case DOUBLE -> "double";
                case SHORT -> "short";
                case INTEGER -> "int";
                case LONG -> "long";
                case CHAR -> "char";
                case STRING -> "java.lang.String";
                default -> super.toString();
            };
        }
    }

    public Token getWithoutExtension(){
        if(!hasExtension())
            return this;

        return new Token(s.substring(0, s.length() - 2), t);
    }

    public boolean hasExtension(){
        return Set.of(Type.IDENTIFIER, Type.DOUBLE, Type.FLOAT, Type.LONG, Type.SHORT).contains(t) && Character.isLetter(s.toCharArray()[s.length() - 2]);
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Token && ((Token) obj).t == t && ((Token) obj).equals(s);
    }

    public boolean equals(Token token){
        return token.equals(s);
    }

    public boolean equals(String s){
        return this.s.equals(s);
    }

    public boolean equals(Token.Type t){
        return this.t == t;
    }

    @Override
    public String toString() {
        return s;
    }
}
