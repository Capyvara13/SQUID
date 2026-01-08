package com.squid.core.model;

/**
 * Simple in-memory leaf history record used for audit and dashboard display
 */
public class LeafHistory {
    private int index;
    private String previousValue;
    private String newValue;
    private String action;
    private String timestamp;

    public LeafHistory() {}

    public int getIndex() { return index; }
    public void setIndex(int index) { this.index = index; }

    public String getPreviousValue() { return previousValue; }
    public void setPreviousValue(String previousValue) { this.previousValue = previousValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
}
