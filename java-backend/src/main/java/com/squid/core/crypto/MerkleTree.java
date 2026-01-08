package com.squid.core.crypto;

import org.bouncycastle.crypto.digests.Blake2bDigest;

import java.util.ArrayList;
import java.util.List;

/**
 * Merkle Tree implementation using BLAKE2b for hashing
 * Provides efficient cryptographic proofs for leaf membership
 */
public class MerkleTree {
    
    private final List<byte[]> leaves;
    private final List<List<byte[]>> levels;
    private byte[] root;

    // Optional hardware-aware mixer; when present we blend its output into
    // the BLAKE2b input to approximate the research plan's assembly hash mix.
    private final AssemblyHashMix assemblyHashMix;
    
    public MerkleTree(List<byte[]> leaves) {
        this(leaves, null);
    }

    public MerkleTree(List<byte[]> leaves, AssemblyHashMix assemblyHashMix) {
        if (leaves == null || leaves.isEmpty()) {
            throw new IllegalArgumentException("Leaves cannot be null or empty");
        }
        
        this.leaves = new ArrayList<>(leaves);
        this.levels = new ArrayList<>();
        this.assemblyHashMix = assemblyHashMix;
        buildTree();
    }
    
    private void buildTree() {
        // Start with leaf level
        List<byte[]> currentLevel = new ArrayList<>();
        for (byte[] leaf : leaves) {
            currentLevel.add(hash(leaf));
        }
        levels.add(new ArrayList<>(currentLevel));
        
        // Build tree bottom-up
        while (currentLevel.size() > 1) {
            List<byte[]> nextLevel = new ArrayList<>();
            
            for (int i = 0; i < currentLevel.size(); i += 2) {
                byte[] left = currentLevel.get(i);
                byte[] right = (i + 1 < currentLevel.size()) ? 
                    currentLevel.get(i + 1) : left; // Duplicate if odd number
                
                nextLevel.add(hashPair(left, right));
            }
            
            levels.add(nextLevel);
            currentLevel = nextLevel;
        }
        
        root = currentLevel.get(0);
    }
    
    /**
     * Hash a single value using BLAKE2b-256. If an AssemblyHashMix instance
     * is provided, its output is blended into the input before hashing to
     * introduce a hardware-dependent component while preserving determinism
     * for a given host.
     */
    private byte[] hash(byte[] data) {
        Blake2bDigest digest = new Blake2bDigest(256);
        byte[] toHash = data;
        if (assemblyHashMix != null) {
            long seed = assemblyHashMix.getHardwareSeed();
            byte[] mixed = assemblyHashMix.customHashMix(data, seed);
            // Concatenate original data with mixed bytes as input to BLAKE2b
            byte[] combined = new byte[data.length + mixed.length];
            System.arraycopy(data, 0, combined, 0, data.length);
            System.arraycopy(mixed, 0, combined, data.length, mixed.length);
            toHash = combined;
        }
        digest.update(toHash, 0, toHash.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }
    
    /**
     * Hash a pair of values (left || right)
     */
    private byte[] hashPair(byte[] left, byte[] right) {
        Blake2bDigest digest = new Blake2bDigest(256);
        byte[] leftIn = left;
        byte[] rightIn = right;
        if (assemblyHashMix != null) {
            long seed = assemblyHashMix.getHardwareSeed();
            leftIn = assemblyHashMix.customHashMix(left, seed);
            rightIn = assemblyHashMix.customHashMix(right, seed);
        }
        digest.update(leftIn, 0, leftIn.length);
        digest.update(rightIn, 0, rightIn.length);
        byte[] result = new byte[digest.getDigestSize()];
        digest.doFinal(result, 0);
        return result;
    }
    
    /**
     * Get the Merkle root
     */
    public byte[] getRoot() {
        return root.clone();
    }
    
    /**
     * Generate Merkle proof for a leaf at given index
     */
    public MerkleProof getProof(int leafIndex) {
        if (leafIndex < 0 || leafIndex >= leaves.size()) {
            throw new IllegalArgumentException("Invalid leaf index");
        }
        
        List<byte[]> proof = new ArrayList<>();
        List<Boolean> directions = new ArrayList<>(); // true = right, false = left
        
        int currentIndex = leafIndex;
        
        // Traverse from leaf to root
        for (int level = 0; level < levels.size() - 1; level++) {
            List<byte[]> currentLevel = levels.get(level);
            
            // Find sibling
            int siblingIndex;
            boolean isRight;
            
            if (currentIndex % 2 == 0) {
                // Current is left child, sibling is right
                siblingIndex = currentIndex + 1;
                isRight = true;
            } else {
                // Current is right child, sibling is left
                siblingIndex = currentIndex - 1;
                isRight = false;
            }
            
            if (siblingIndex < currentLevel.size()) {
                proof.add(currentLevel.get(siblingIndex));
                directions.add(isRight);
            }
            
            currentIndex = currentIndex / 2;
        }
        
        return new MerkleProof(leaves.get(leafIndex), proof, directions, root);
    }
    
    /**
     * Verify a Merkle proof
     */
    public static boolean verifyProof(MerkleProof proof) {
        byte[] currentHash = proof.getLeafHash();
        
        for (int i = 0; i < proof.getProof().size(); i++) {
            byte[] siblingHash = proof.getProof().get(i);
            boolean isRight = proof.getDirections().get(i);
            
            Blake2bDigest digest = new Blake2bDigest(256);
            
            if (isRight) {
                // Sibling is right, current is left
                digest.update(currentHash, 0, currentHash.length);
                digest.update(siblingHash, 0, siblingHash.length);
            } else {
                // Sibling is left, current is right
                digest.update(siblingHash, 0, siblingHash.length);
                digest.update(currentHash, 0, currentHash.length);
            }
            
            byte[] result = new byte[digest.getDigestSize()];
            digest.doFinal(result, 0);
            currentHash = result;
        }
        
        return java.util.Arrays.equals(currentHash, proof.getExpectedRoot());
    }
    
    /**
     * Merkle proof structure
     */
    public static class MerkleProof {
        private final byte[] leafHash;
        private final List<byte[]> proof;
        private final List<Boolean> directions;
        private final byte[] expectedRoot;
        
        public MerkleProof(byte[] leaf, List<byte[]> proof, List<Boolean> directions, byte[] expectedRoot) {
            Blake2bDigest digest = new Blake2bDigest(256);
            digest.update(leaf, 0, leaf.length);
            byte[] hash = new byte[digest.getDigestSize()];
            digest.doFinal(hash, 0);
            
            this.leafHash = hash;
            this.proof = new ArrayList<>(proof);
            this.directions = new ArrayList<>(directions);
            this.expectedRoot = expectedRoot.clone();
        }
        
        public byte[] getLeafHash() { return leafHash.clone(); }
        public List<byte[]> getProof() { return new ArrayList<>(proof); }
        public List<Boolean> getDirections() { return new ArrayList<>(directions); }
        public byte[] getExpectedRoot() { return expectedRoot.clone(); }
    }
}
