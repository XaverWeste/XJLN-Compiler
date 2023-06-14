package com.github.xjln.compiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

class TokenHandler {

    private final Token[] tokens;
    private int index;

    public TokenHandler(List<Token> tokens){
        this.tokens = tokens.toArray(new Token[0]);
        index = -1;
    }

    public Token next() throws RuntimeException {
        if(!hasNext()) throw new RuntimeException("expected Token got nothing ");
        index++;
        return tokens[index];
    }

    public Token current() throws RuntimeException {
        if(!isValid()) throw new RuntimeException("expected Token got nothing");
        return tokens[index];
    }

    public Token last() throws RuntimeException {
        index--;
        if(!isValid()) throw new RuntimeException("expected Token got nothing");
        return tokens[index];
    }

    public boolean hasNext(){
        return index + 1 < tokens.length;
    }

    public boolean isValid(){
        return index < tokens.length && index > -1;
    }

    public boolean isEmpty(){
        return tokens.length == 0;
    }

    public int length(){
        return tokens.length;
    }

    public void toFirst(){
        index = -1;
    }

    public TokenHandler getInBracket() throws RuntimeException {
        if(!isValid()) throw new RuntimeException("expected left bracket got nothing");
        Token current = tokens[index];
        if(!Set.of("(", "[", "{").contains(current.s())) throw new RuntimeException("expected left bracket got " + tokens[index].s());

        String openingBracket = current.s();
        String closingBracket = openingBracket.equals("(") ? ")" : openingBracket.equals("[") ? "]" : "}";
        List<Token> tokenList = new ArrayList<>();
        int i = 1;

        while (i > 0 && hasNext()){
            current = next();
            if(current.s().equals(closingBracket)) i--;
            else if(current.s().equals(openingBracket)) i++;
            if(i > 0) tokenList.add(current);
        }

        if(i > 0) throw new RuntimeException("expected right bracket got nothing");
        return new TokenHandler(tokenList);
    }

    public Token assertToken(String string) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException("expected " + string + " got nothing");
        Token token = next();
        if(!token.s().equals(string)) throw new RuntimeException("expected " + string + " got " + token.s());
        return token;
    }

    public Token assertToken(String...strings) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException("expected one of " + arrayToString(strings) + " got nothing");
        Token token = next();

        for(String str:strings) if(token.s().equals(str)) return token;

        throw new RuntimeException("expected one of " + arrayToString(strings) + " got " + token.s());
    }

    public Token assertToken(Token.Type type) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException("expected " + type.toString() + " got nothing");
        Token token = next();
        if(token.t() != type) throw new RuntimeException("expected " + type.toString() + " got " + token.t().toString());
        return token;
    }

    public Token assertToken(Token.Type...types) throws RuntimeException {
        if(!hasNext()) throw new RuntimeException("expected one of " + arrayToString(types) + " got nothing");
        Token token = next();

        for(Token.Type t:types) if(token.t() == t) return token;

        throw new RuntimeException("expected one of " + arrayToString(types) + " got " + token.t().toString());
    }

    public static void assertToken(Token token, String string) throws RuntimeException {
        if(!token.s().equals(string)) throw new RuntimeException("expected " + string + " got " + token.s());
    }

    public static void assertToken(Token token, Token.Type type) throws RuntimeException {
        if(token.t() != type) throw new RuntimeException("expected " + type.toString() + " got " + token.t().toString());
    }

    public void assertHasNext() throws RuntimeException{
        if(index >= tokens.length) throw new RuntimeException("expected Token, got Nothing");
    }

    public void assertNull() throws RuntimeException{
        if(hasNext()) throw new RuntimeException("expected nothing, got " + tokens[index].t().toString());
    }

    private String arrayToString(String[] sa){
        StringBuilder sb = new StringBuilder();

        for(String s:sa) sb.append(s).append(", ");

        sb.deleteCharAt(sb.length() - 2);
        return sb.toString();
    }

    private String arrayToString(Token.Type[] ta){
        StringBuilder sb = new StringBuilder();

        for(Token.Type t:ta) sb.append(t.toString()).append(", ");

        sb.deleteCharAt(sb.length() - 2);
        return sb.toString();
    }

    public String toString(){
        StringBuilder sb = new StringBuilder();
        for(Token t:tokens) sb.append(t.s()).append(" ");
        return sb.toString();
    }
}
