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

    public Value get(Key key){
        return values.get(keys.indexOf(key));
    }
}
