package com.github.xjln.compiler;

record Token(String s, Type t){

    enum Type{SIMPLE, IDENTIFIER, NUMBER, STRING, OPERATOR}

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

    @Override
    public String toString() {
        return s;
    }
}
