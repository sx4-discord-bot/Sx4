package com.sx4.pair;

public class CustomPair<Key, Value> {
		
	private Key key;
	private Value value;
	
	public CustomPair(Key key, Value value) {
		this.key = key;
		this.value = value;
	}
	
	public Key getKey() {
		return this.key;
	}
	
	public Value getValue() {
		return this.value;
	}
	
	public CustomPair<Key, Value> setPair(Key key, Value value) {
		this.key = key;
		this.value = value;
		
		return this;
	}
	
	public CustomPair<Key, Value> setKey(Key key) {
		this.key = key;
		
		return this;
	}
	
	public CustomPair<Key, Value> setValue(Value value) {
		this.value = value;
		
		return this;
	}
	
}
