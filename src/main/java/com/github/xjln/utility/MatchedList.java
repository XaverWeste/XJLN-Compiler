package com.github.xjln.utility;

import java.util.*;

public class MatchedList<First, Second> {

    private final ArrayList<First> firstList;
    private final ArrayList<Second> secondList;

    public MatchedList(){
        firstList = new ArrayList<>();
        secondList = new ArrayList<>();
    }

    public void add(First first, Second second){
        firstList.add(first);
        secondList.add(second);
    }

    public First getFirst(Second second){
        if(!secondList.contains(second))
            return null;
        return firstList.get(secondList.indexOf(second));
    }

    public First getFirst(int n){
        if(n < 0 || n >= size())
            return null;
        return firstList.get(n);
    }

    public Second getSecond(First first){
        if(!firstList.contains(first))
            return null;
        return secondList.get(firstList.indexOf(first));
    }

    public Second getSecond(int n){
        if(n < 0 || n >= size())
            return null;
        return secondList.get(n);
    }

    public ArrayList<First> getFirstList(){
        return firstList;
    }

    public ArrayList<Second> getSecondList(){
        return secondList;
    }

    public int size(){
        return firstList.size();
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
