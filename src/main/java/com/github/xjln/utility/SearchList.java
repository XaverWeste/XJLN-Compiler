package com.github.xjln.utility;

import java.util.ArrayList;

public class SearchList <Key, Value>{

    private final ArrayList<Key> keys;
    private final ArrayList<Value> values;

    public SearchList(){
        keys = new ArrayList<>();
        values = new ArrayList<>();
    }

    public void add(Key key, Value value){
        keys.add(key);
        values.add(value);
    }

    public Value get(int index){
        return values.get(index);
    }

    public Value get(Key key){
        if(!keys.contains(key))
            return null;
        return values.get(keys.indexOf(key));
    }

    public ArrayList<Key> getKeys(){
        return keys;
    }

    public ArrayList<Value> getValues(){
        return values;
    }

    public int size(){
        return keys.size();
    }
}
