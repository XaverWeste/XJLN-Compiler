package com.github.xjln.utility;

import java.util.*;

public class MatchedList<Key, Value> {

    private final ArrayList<Key> keyList;
    private final ArrayList<Value> valueList;

    public MatchedList(){
        keyList = new ArrayList<>();
        valueList = new ArrayList<>();
    }

    public void add(Key key, Value value){
        keyList.add(key);
        valueList.add(value);
    }

    public Key getKey(Value value){
        if(!valueList.contains(value))
            return null;
        return keyList.get(valueList.indexOf(value));
    }

    public Key getKey(int n){
        if(n < 0 || n >= size())
            return null;
        return keyList.get(n);
    }

    public boolean hasKey(Key key){
        return keyList.contains(key);
    }

    public Value getValue(Key key){
        if(!keyList.contains(key))
            return null;
        return valueList.get(keyList.indexOf(key));
    }

    public Value getValue(int n){
        if(n < 0 || n >= size())
            return null;
        return valueList.get(n);
    }

    public boolean hasValue(Value value){
        return valueList.contains(value);
    }

    public ArrayList<Key> getKeyList(){
        return keyList;
    }

    public ArrayList<Value> getValueList(){
        return valueList;
    }

    public void remove(int n){
        if(keyList.size() <= n){
            keyList.remove(n);
            valueList.remove(n);
        }
    }

    public int size(){
        return keyList.size();
    }

    public static <First, Second> MatchedList<First, Second> of(First[] firsts, Second[] seconds) throws RuntimeException{
        assert firsts != null;
        assert seconds != null;
        assert firsts.length == seconds.length;

        MatchedList<First, Second> matchedList = new MatchedList<>();

        for(int i = 0;i < firsts.length;i++)
            matchedList.add(firsts[i], seconds[i]);

        return matchedList;
    }
}
