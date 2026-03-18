package com.squid.core.service;

import org.springframework.stereotype.Service;

@Service
public class AIDecisionStateService {
    private volatile double entropyBudget = 100.0;
    private volatile String lastDecisionHashHex = null;
    private volatile String lastFinalSeedHex = null;

    public double getEntropyBudget() {
        return entropyBudget;
    }

    public void consumeEntropy(double amount) {
        entropyBudget = Math.max(0.0, entropyBudget - Math.max(0.0, amount));
    }

    public void setLastDecisionHashHex(String hex) {
        this.lastDecisionHashHex = hex;
    }

    public String getLastDecisionHashHex() {
        return lastDecisionHashHex;
    }

    public void setLastFinalSeedHex(String hex) {
        this.lastFinalSeedHex = hex;
    }

    public String getLastFinalSeedHex() {
        return lastFinalSeedHex;
    }
}
