package com.github.xjln.compiler;

public record Token(String s, Type t){

    enum Type{SIMPLE, IDENTIFIER, STRING, OPERATOR, CHAR, INTEGER, LONG, DOUBLE, FLOAT}

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
