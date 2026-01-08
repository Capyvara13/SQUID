import { useState, useEffect } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";

interface NodeState {
  nodeId: string;
  state: "VALID" | "DECOY" | "TRANSITIONING" | "COMPROMISED";
  color: string;
  lastTransition: number;
  transitionCount: number;
}

interface Transition {
  id: string;
  type: string;
  nodeId: string;
  from: string;
  to: string;
  reason: string;
  timestamp: number;
  affectedNodes: string[];
}

interface TreeStatus {
  rootHash: string;
  totalNodes: number;
  validNodes: number;
  decoyNodes: number;
  compromisedNodes: number;
  transitioningNodes: number;
  autonomousTransitions: number;
  engineRunning: boolean;
  lastUpdate: string;
}

export function DynamicMerkleTreeViewer({ apiBaseUrl = "http://localhost:8080/api/v1" }) {
  const [treeStatus, setTreeStatus] = useState<TreeStatus | null>(null);
  const [allTransitions, setAllTransitions] = useState<Transition[]>([]);
  const [nodeStates, setNodeStates] = useState<Map<string, NodeState>>(new Map());
  const [auditTrail, setAuditTrail] = useState<any>(null);
  
  const [newLeaves, setNewLeaves] = useState<string>("");
  const [updateReason, setUpdateReason] = useState("");
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [refreshInterval, setRefreshInterval] = useState(2000); // 2 seconds

  // State color mapping
  const stateColors: Record<string, string> = {
    VALID: "#10b981",          // Green
    DECOY: "#f59e0b",           // Orange
    TRANSITIONING: "#8b5cf6",   // Purple
    COMPROMISED: "#ef4444"      // Red
  };

  const stateBgColors: Record<string, string> = {
    VALID: "bg-green-100 border-green-300",
    DECOY: "bg-yellow-100 border-yellow-300",
    TRANSITIONING: "bg-purple-100 border-purple-300",
    COMPROMISED: "bg-red-100 border-red-300"
  };

  // Fetch tree status
  const fetchTreeStatus = async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/merkle/status`);
      const data = await response.json();
      setTreeStatus(data);
    } catch (err) {
      console.error("Error fetching tree status:", err);
    }
  };

  // Fetch all transitions
  const fetchTransitions = async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/merkle/history`);
      const data = await response.json();
      
      if (Array.isArray(data)) {
        setAllTransitions(data);
        
        // Update node states from transitions
        const newStates = new Map<string, NodeState>();
        
        data.forEach((t: any) => {
          if (t.nodeId && t.type?.startsWith("AUTONOMOUS_")) {
            const state = t.type.replace("AUTONOMOUS_", "");
            newStates.set(t.nodeId, {
              nodeId: t.nodeId,
              state: state as any,
              color: stateColors[state] || "#808080",
              lastTransition: t.timestamp,
              transitionCount: (newStates.get(t.nodeId)?.transitionCount || 0) + 1
            });
          }
        });
        
        setNodeStates(newStates);
      }
    } catch (err) {
      console.error("Error fetching transitions:", err);
    }
  };

  // Fetch audit trail
  const fetchAuditTrail = async () => {
    try {
      const response = await fetch(`${apiBaseUrl}/merkle/audit`);
      const data = await response.json();
      setAuditTrail(data);
    } catch (err) {
      console.error("Error fetching audit trail:", err);
    }
  };

  // Auto-refresh data
  useEffect(() => {
    if (!autoRefresh) return;

    fetchTreeStatus();
    fetchTransitions();
    fetchAuditTrail();

    const interval = setInterval(() => {
      fetchTreeStatus();
      fetchTransitions();
    }, refreshInterval);

    return () => clearInterval(interval);
  }, [autoRefresh, refreshInterval, apiBaseUrl]);

  // Add leaves to tree
  const handleAddLeaves = async () => {
    if (!newLeaves.trim()) {
      setError("Please enter at least one leaf data");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const leaves = newLeaves
        .split("\n")
        .map((l) => l.trim())
        .filter((l) => l);

      const response = await fetch(`${apiBaseUrl}/merkle/add-leaves`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          leaves,
          reason: updateReason || "User added leaves"
        })
      });

      if (response.ok) {
        setNewLeaves("");
        setUpdateReason("");
        await fetchTreeStatus();
        await fetchTransitions();
      } else {
        setError("Failed to add leaves");
      }
    } catch (err) {
      setError("Error adding leaves: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  // Verify integrity
  const handleVerifyIntegrity = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${apiBaseUrl}/merkle/verify`, {
        method: "POST"
      });

      const data = await response.json();
      
      if (data.isValid) {
        alert("✓ Tree integrity is valid!");
      } else {
        alert(`✗ Integrity check failed! Found ${data.compromisedNodes} compromised nodes`);
      }
      
      await fetchTreeStatus();
    } catch (err) {
      setError("Error verifying integrity: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  // Rotate keys
  const handleRotateKeys = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch(`${apiBaseUrl}/merkle/rotate-keys`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reason: "Periodic key rotation" })
      });

      if (response.ok) {
        alert("Keys rotated successfully!");
        await fetchTreeStatus();
        await fetchTransitions();
      } else {
        setError("Failed to rotate keys");
      }
    } catch (err) {
      setError("Error rotating keys: " + (err as Error).message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Tree Status Cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <Card className="border-green-200 bg-green-50">
          <CardContent className="pt-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-green-600">{treeStatus?.validNodes || 0}</div>
              <div className="text-sm text-green-600">Valid Nodes</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-yellow-200 bg-yellow-50">
          <CardContent className="pt-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-yellow-600">{treeStatus?.decoyNodes || 0}</div>
              <div className="text-sm text-yellow-600">Decoy Nodes</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-red-200 bg-red-50">
          <CardContent className="pt-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-red-600">{treeStatus?.compromisedNodes || 0}</div>
              <div className="text-sm text-red-600">Compromised</div>
            </div>
          </CardContent>
        </Card>

        <Card className="border-purple-200 bg-purple-50">
          <CardContent className="pt-6">
            <div className="text-center">
              <div className="text-3xl font-bold text-purple-600">{treeStatus?.transitioningNodes || 0}</div>
              <div className="text-sm text-purple-600">Transitioning</div>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Root Hash Display */}
      {treeStatus && (
        <Card className="border-blue-200">
          <CardHeader>
            <CardTitle className="text-blue-600">Merkle Tree Root Hash</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="font-mono text-sm bg-gray-100 p-3 rounded break-all">
              {treeStatus.rootHash || "No root hash"}
            </div>
            <div className="mt-4 grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <div>
                <span className="font-semibold">Total Nodes:</span> {treeStatus.totalNodes}
              </div>
              <div>
                <span className="font-semibold">Autonomous Transitions:</span> {treeStatus.autonomousTransitions}
              </div>
              <div>
                <span className="font-semibold">Engine Running:</span>
                <Badge className="ml-2" variant={treeStatus.engineRunning ? "default" : "destructive"}>
                  {treeStatus.engineRunning ? "Active" : "Inactive"}
                </Badge>
              </div>
              <div>
                <span className="font-semibold">Last Update:</span>
                {new Date(treeStatus.lastUpdate).toLocaleTimeString()}
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Error Alert */}
      {error && (
        <Alert variant="destructive">
          <AlertDescription>{error}</AlertDescription>
        </Alert>
      )}

      {/* Controls */}
      <Card>
        <CardHeader>
          <CardTitle>Tree Controls</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Add Leaves Section */}
          <div className="space-y-2">
            <label className="block text-sm font-semibold">Add New Leaves (one per line)</label>
            <Textarea
              placeholder="leaf_data_1&#10;leaf_data_2&#10;leaf_data_3"
              value={newLeaves}
              onChange={(e) => setNewLeaves(e.target.value)}
              className="font-mono text-sm"
              rows={4}
            />
            <Input
              placeholder="Reason for adding leaves"
              value={updateReason}
              onChange={(e) => setUpdateReason(e.target.value)}
            />
            <Button
              onClick={handleAddLeaves}
              disabled={loading}
              className="bg-blue-600 hover:bg-blue-700"
            >
              {loading ? "Adding..." : "Add Leaves"}
            </Button>
          </div>

          {/* Action Buttons */}
          <div className="flex gap-2 flex-wrap">
            <Button
              onClick={handleVerifyIntegrity}
              disabled={loading}
              variant="outline"
              className="border-green-600 text-green-600 hover:bg-green-50"
            >
              Verify Integrity
            </Button>
            <Button
              onClick={handleRotateKeys}
              disabled={loading}
              variant="outline"
              className="border-orange-600 text-orange-600 hover:bg-orange-50"
            >
              Rotate Keys
            </Button>
          </div>

          {/* Auto-Refresh Control */}
          <div className="flex items-center gap-4 pt-4 border-t">
            <label className="flex items-center gap-2">
              <input
                type="checkbox"
                checked={autoRefresh}
                onChange={(e) => setAutoRefresh(e.target.checked)}
                className="rounded"
              />
              <span className="text-sm">Auto-refresh</span>
            </label>
            {autoRefresh && (
              <select
                value={refreshInterval}
                onChange={(e) => setRefreshInterval(Number(e.target.value))}
                className="text-sm border rounded px-2 py-1"
              >
                <option value={1000}>1s</option>
                <option value={2000}>2s</option>
                <option value={5000}>5s</option>
                <option value={10000}>10s</option>
              </select>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Recent Transitions */}
      <Card>
        <CardHeader>
          <CardTitle>Recent Node Transitions (Last 20)</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-2 max-h-96 overflow-y-auto">
            {allTransitions.slice(0, 20).map((t) => (
              <div
                key={t.id}
                className={`p-3 rounded border-l-4 ${
                  stateBgColors[t.to as keyof typeof stateBgColors] || "bg-gray-100"
                }`}
              >
                <div className="flex items-center justify-between">
                  <div>
                    <Badge className="mr-2">{t.type}</Badge>
                    <span className="font-mono text-xs text-gray-600">{t.nodeId}</span>
                  </div>
                  <span className="text-xs text-gray-500">
                    {new Date(t.timestamp).toLocaleTimeString()}
                  </span>
                </div>
                <div className="mt-1 text-sm">
                  <span className="font-semibold text-gray-700">{t.from}</span>
                  <span className="mx-2">→</span>
                  <span className="font-semibold text-gray-700">{t.to}</span>
                </div>
                <div className="mt-1 text-xs text-gray-600">{t.reason}</div>
              </div>
            ))}
          </div>
        </CardContent>
      </Card>

      {/* Audit Trail Tab */}
      <Card>
        <CardHeader>
          <CardTitle>Audit Trail</CardTitle>
        </CardHeader>
        <CardContent>
          {auditTrail && (
            <Tabs defaultValue="summary" className="w-full">
              <TabsList className="grid w-full grid-cols-3">
                <TabsTrigger value="summary">Summary</TabsTrigger>
                <TabsTrigger value="counts">Event Counts</TabsTrigger>
                <TabsTrigger value="recent">Recent Events</TabsTrigger>
              </TabsList>

              <TabsContent value="summary">
                <div className="space-y-2 pt-4 text-sm">
                  <div>
                    <span className="font-semibold">Total Transitions:</span> {auditTrail.totalTransitions}
                  </div>
                  <div>
                    <span className="font-semibold">Current Status:</span>
                  </div>
                  <div className="ml-4 p-2 bg-gray-50 rounded text-xs">
                    <pre>{JSON.stringify(auditTrail.currentStatus, null, 2)}</pre>
                  </div>
                </div>
              </TabsContent>

              <TabsContent value="counts">
                <div className="space-y-2 pt-4 text-sm">
                  {auditTrail.transitionCounts &&
                    Object.entries(auditTrail.transitionCounts).map(([key, value]) => (
                      <div key={key} className="flex justify-between p-2 bg-gray-50 rounded">
                        <span>{key}</span>
                        <span className="font-bold">{value as number}</span>
                      </div>
                    ))}
                </div>
              </TabsContent>

              <TabsContent value="recent">
                <div className="space-y-2 pt-4 max-h-72 overflow-y-auto">
                  {auditTrail.recentTransitions &&
                    auditTrail.recentTransitions.map((t: any, idx: number) => (
                      <div key={idx} className="p-2 bg-gray-50 rounded text-xs">
                        <div className="font-semibold">{t.type || t.reason}</div>
                        <div className="text-gray-600">
                          {new Date(t.timestamp * 1000 || t.timestamp).toLocaleTimeString()}
                        </div>
                        {t.details && <div className="text-gray-500 mt-1">{t.details}</div>}
                      </div>
                    ))}
                </div>
              </TabsContent>
            </Tabs>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
